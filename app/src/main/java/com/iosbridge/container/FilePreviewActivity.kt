
package com.iosbridge.container

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.view.Gravity
import android.widget.*
import java.io.File

class FilePreviewActivity : Activity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 19, 29))
            setPadding(20, 24, 20, 20)
        }

        val title = TextView(this).apply {
            text = "FILE PREVIEW"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(content)

        root.addView(title)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1, 0, 1f
            )
        )

        setContentView(root)

        val path = intent.getStringExtra("file_path")

        if (path.isNullOrBlank()) {
            addText("ERROR: File path missing.")
            return
        }

        val file = File(path)

        if (!file.exists() || !file.isFile) {
            addText("ERROR: File not found.")
            return
        }

        showFile(file)
    }

    private fun showFile(file: File) {

        addText(file.name, 25f, true)

        val analysis = try {
            FileAnalyzer.analyze(file)
        } catch (_: Exception) {
            null
        }

        val format = analysis?.format ?: "UNKNOWN"

        addText("FORMAT: $format", 14f)
        addText("SIZE: ${analysis?.size ?: "UNKNOWN"}", 14f)
        addText("--------------------------------")

        when (format) {

            "IPA" -> {
                val result = IPAExtractor.inspect(file)

                addText("IPA APPLICATION INFORMATION", 18f, true)
                addText("Success: ${result.success}")
                addText("Application: ${result.appName}")
                addText("Bundle path: ${result.bundlePath}")
                addText("Files: ${result.fileCount}")
                addText(result.message)
            }

            "PLIST", "Binary PLIST" -> {
                val result = PlistAnalyzer.analyze(file)

                addText("PROPERTY LIST DATA", 18f, true)
                addText(result.message)

                result.entries.take(500).forEach {
                    addText(it, 13f)
                }
            }

            "ZIP ARCHIVE" -> {
                val result = ArchiveAnalyzer.inspect(file)

                addText("ARCHIVE CONTENTS", 18f, true)
                addText("Files: ${result.totalFiles}")
                addText("Folders: ${result.totalDirectories}")
                addText("Uncompressed size: ${result.totalSize} bytes")
                addText(result.message)

                result.entries.take(300).forEach {
                    val type = if (it.isDirectory) "[DIR]" else "[FILE]"
                    addText("$type ${it.name} (${it.size} bytes)", 12f)
                }
            }

            "SQLite", "DATABASE" -> {
                val result = SQLiteAnalyzer.inspect(file)

                addText("DATABASE STRUCTURE", 18f, true)
                addText(result.message)

                result.tables.forEach { table ->
                    addText("TABLE: ${table.name}", 16f, true)
                    addText("Records: ${table.rowCount}")

                    table.columns.forEach { column ->
                        addText(
                            "${column.name} : ${column.type} " +
                            if (column.primaryKey) "[PRIMARY KEY]" else ""
                        )
                    }
                }
            }

            "PNG IMAGE", "JPEG IMAGE" -> {
                showImage(file)
            }

            "JSON", "XML" -> {
                addText("DOCUMENT CONTENT", 18f, true)

                try {
                    val preview = file.inputStream().bufferedReader()
                        .use { reader ->
                            val buffer = CharArray(32768)
                            val count = reader.read(buffer)
                            if (count > 0) {
                                String(buffer, 0, count)
                            } else {
                                ""
                            }
                        }

                    addText(preview, 12f)
                    addText("Preview limited to 32 KB.")
                } catch (e: Exception) {
                    addText("Unable to read document: ${e.message}")
                }
            }

            else -> {
                addText("No preview available for this format.")
                addText(analysis?.description ?: "Unknown file.")
            }
        }
    }

    private fun showImage(file: File) {
        try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val options = BitmapFactory.Options().apply {
                inSampleSize = 4
            }

            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                options
            )

            if (bitmap != null) {
                val image = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = file.name
                }

                content.addView(
                    image,
                    LinearLayout.LayoutParams(-1, -2)
                )

                addText(
                    "Image dimensions: ${bounds.outWidth} × ${bounds.outHeight}"
                )
            } else {
                addText("Unable to decode image.")
            }

        } catch (e: Exception) {
            addText("Image preview failed: ${e.message}")
        }
    }

    private fun addText(
        value: String,
        size: Float = 14f,
        bold: Boolean = false
    ) {
        val text = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(12, 9, 12, 9)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        content.addView(text)
    }
}
