
package com.iosbridge.container

import java.io.File

object FileAnalyzer {

    data class AnalysisResult(
        val name: String,
        val format: String,
        val size: String,
        val description: String,
        val supported: Boolean
    )

    fun analyze(file: File): AnalysisResult {

        val header = ByteArray(16)

        val count = file.inputStream().use {
            it.read(header)
        }

        val signature = if (count > 0) {
            header.copyOf(count)
        } else {
            byteArrayOf()
        }

        val extension = file.extension.lowercase()

        val format = when {
            extension == "ipa" -> "IPA"
            signature.startsWithBytes(
                "SQLite format 3\u0000".toByteArray()
            ) -> "SQLite"
            signature.startsWithBytes(
                "bplist00".toByteArray()
            ) -> "Binary PLIST"
            signature.startsWithBytes(
                byteArrayOf(0x50, 0x4B, 0x03, 0x04)
            ) -> "ZIP ARCHIVE"
            extension == "plist" -> "PLIST"
            extension == "json" -> "JSON"
            extension == "xml" -> "XML"
            extension in listOf("jpg", "jpeg") -> "JPEG IMAGE"
            extension == "png" -> "PNG IMAGE"
            extension == "db" || extension == "sqlite" ->
                "DATABASE"
            extension == "zip" -> "ZIP ARCHIVE"
            else -> detectTextFormat(file)
        }

        val description = when (format) {
            "IPA" ->
                "iOS application package. ZIP-based archive."
            "SQLite", "DATABASE" ->
                "Database file. Structure inspection available."
            "Binary PLIST" ->
                "Apple binary property list."
            "PLIST" ->
                "Apple property list document."
            "ZIP ARCHIVE" ->
                "Compressed archive. Contents can be inspected."
            "JSON" ->
                "JavaScript Object Notation document."
            "XML" ->
                "Extensible Markup Language document."
            "JPEG IMAGE", "PNG IMAGE" ->
                "Image file."
            else ->
                "Unknown or unsupported file format."
        }

        val supported = format != "UNKNOWN"

        return AnalysisResult(
            name = file.name,
            format = format,
            size = formatSize(file.length()),
            description = description,
            supported = supported
        )
    }

    private fun detectTextFormat(file: File): String {
        return try {
            val text = file.inputStream().bufferedReader()
                .use { it.readText().take(4096) }
                .trimStart('\uFEFF', ' ', '\n', '\r', '\t')

            when {
                text.startsWith("{") ||
                text.startsWith("[") -> "JSON"

                text.startsWith("<?xml") ||
                text.startsWith("<plist") ||
                text.startsWith("<") -> "XML"

                else -> "UNKNOWN"
            }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 ->
                "%.2f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L ->
                "%.2f MB".format(
                    bytes / (1024.0 * 1024.0)
                )
            else ->
                "%.2f GB".format(
                    bytes / (1024.0 * 1024.0 * 1024.0)
                )
        }
    }

    private fun ByteArray.startsWithBytes(
        prefix: ByteArray
    ): Boolean {
        if (size < prefix.size) return false

        return prefix.indices.all {
            this[it] == prefix[it]
        }
    }
}
