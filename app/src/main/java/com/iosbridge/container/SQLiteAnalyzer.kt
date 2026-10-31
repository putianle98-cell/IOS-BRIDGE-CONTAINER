
package com.iosbridge.container

import android.database.sqlite.SQLiteDatabase
import java.io.File

object SQLiteAnalyzer {

    data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val primaryKey: Boolean
    )

    data class TableInfo(
        val name: String,
        val columns: List<ColumnInfo>,
        val rowCount: Long
    )

    data class DatabaseResult(
        val success: Boolean,
        val tables: List<TableInfo>,
        val message: String
    )

    fun inspect(file: File): DatabaseResult {

        if (!file.exists() || !file.isFile) {
            return failure("Database file does not exist.")
        }

        if (file.length() > 512L * 1024 * 1024) {
            return failure("Database exceeds 512 MB limit.")
        }

        var database: SQLiteDatabase? = null

        return try {
            database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val tables = mutableListOf<TableInfo>()

            database.rawQuery(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                AND name NOT LIKE 'sqlite_%'
                ORDER BY name
                LIMIT 200
                """.trimIndent(),
                null
            ).use { cursor ->

                while (cursor.moveToNext()) {

                    val tableName = cursor.getString(0)
                    val safeName = tableName.replace("\"", "\"\"")
                    val columns = mutableListOf<ColumnInfo>()

                    database.rawQuery(
                        "PRAGMA table_info(\"$safeName\")",
                        null
                    ).use { columnCursor ->

                        while (columnCursor.moveToNext()) {
                            columns.add(
                                ColumnInfo(
                                    name = columnCursor.getString(1),
                                    type = columnCursor.getString(2) ?: "",
                                    notNull = columnCursor.getInt(3) == 1,
                                    primaryKey = columnCursor.getInt(5) > 0
                                )
                            )
                        }
                    }

                    val count = try {
                        database.rawQuery(
                            "SELECT COUNT(*) FROM \"$safeName\"",
                            null
                        ).use { countCursor ->
                            if (countCursor.moveToFirst()) {
                                countCursor.getLong(0)
                            } else {
                                0L
                            }
                        }
                    } catch (_: Exception) {
                        -1L
                    }

                    tables.add(
                        TableInfo(
                            name = tableName,
                            columns = columns,
                            rowCount = count
                        )
                    )
                }
            }

            DatabaseResult(
                success = true,
                tables = tables,
                message = "Database inspected successfully."
            )

        } catch (e: Exception) {
            failure("Database inspection failed: ${e.message}")
        } finally {
            database?.close()
        }
    }

    private fun failure(message: String): DatabaseResult {
        return DatabaseResult(
            success = false,
            tables = emptyList(),
            message = message
        )
    }
}
