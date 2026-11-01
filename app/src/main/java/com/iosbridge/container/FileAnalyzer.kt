
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

        
        val isMachO = signature.size >= 4 && (
            signature.startsWithBytes(
                byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCE.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xCE.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCF.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xBE.toByte(), 0xBA.toByte(), 0xFE.toByte(), 0xCA.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBF.toByte())
            ) ||
            signature.startsWithBytes(
                byteArrayOf(0xBF.toByte(), 0xBA.toByte(), 0xFE.toByte(), 0xCA.toByte())
            )
        )

        val format = when {
            extension == "ipa" -> "IPA"
            isMachO -> "Mach-O"
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
            bytes < 1024 -> "$bytes 字节"
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
