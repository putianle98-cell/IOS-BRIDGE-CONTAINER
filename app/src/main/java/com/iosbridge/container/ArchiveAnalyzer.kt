
package com.iosbridge.container

import java.io.File
import java.util.zip.ZipFile

object ArchiveAnalyzer {

    data class ArchiveEntry(
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val isDirectory: Boolean
    )

    data class ArchiveResult(
        val success: Boolean,
        val entries: List<ArchiveEntry>,
        val totalFiles: Int,
        val totalDirectories: Int,
        val totalSize: Long,
        val message: String
    )

    fun inspect(file: File): ArchiveResult {

        if (!file.exists() || !file.isFile) {
            return failure("Archive file does not exist.")
        }

        return try {
            ZipFile(file).use { zip ->

                val result = mutableListOf<ArchiveEntry>()

                val iterator = zip.entries()

                var totalSize = 0L
                var totalFiles = 0
                var totalDirectories = 0

                while (iterator.hasMoreElements() &&
                    result.size < 5000
                ) {
                    val entry = iterator.nextElement()

                    if (entry.name.startsWith("/") ||
                        entry.name.contains("\\") ||
                        entry.name.split("/").any { it == ".." }
                    ) {
                        continue
                    }

                    if (entry.isDirectory) {
                        totalDirectories++
                    } else {
                        totalFiles++
                        if (entry.size > 0) {
                            totalSize += entry.size
                        }
                    }

                    result.add(
                        ArchiveEntry(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            isDirectory = entry.isDirectory
                        )
                    )
                }

                ArchiveResult(
                    success = true,
                    entries = result,
                    totalFiles = totalFiles,
                    totalDirectories = totalDirectories,
                    totalSize = totalSize,
                    message = "Archive inspected successfully."
                )
            }

        } catch (e: Exception) {
            failure("Archive inspection failed: ${e.message}")
        }
    }

    private fun failure(message: String): ArchiveResult {
        return ArchiveResult(
            success = false,
            entries = emptyList(),
            totalFiles = 0,
            totalDirectories = 0,
            totalSize = 0,
            message = message
        )
    }
}
