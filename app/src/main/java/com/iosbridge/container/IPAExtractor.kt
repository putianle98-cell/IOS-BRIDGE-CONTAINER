
package com.iosbridge.container

import java.io.File
import java.util.zip.ZipFile

object IPAExtractor {

    data class IPAInfo(
        val success: Boolean,
        val appName: String,
        val bundlePath: String,
        val fileCount: Int,
        val extractedPath: String,
        val message: String
    )

    fun inspect(ipaFile: File): IPAInfo {

        if (!ipaFile.exists() ||
            !ipaFile.isFile ||
            ipaFile.extension.lowercase() != "ipa"
        ) {
            return failure("Invalid IPA file.")
        }

        return try {
            ZipFile(ipaFile).use { zip ->

                val entries = zip.entries().asSequence()
                    .toList()

                val appEntry = entries.firstOrNull {
                    it.name.startsWith("Payload/") &&
                    it.name.endsWith(".app/")
                }

                val bundlePath = appEntry?.name
                    ?: return failure(
                        "No Payload application directory found."
                    )

                val appName = bundlePath
                    .removePrefix("Payload/")
                    .removeSuffix(".app/")

                val fileCount = entries.count {
                    it.name.startsWith(bundlePath) &&
                    !it.isDirectory
                }

                IPAInfo(
                    success = true,
                    appName = appName,
                    bundlePath = bundlePath,
                    fileCount = fileCount,
                    extractedPath = "",
                    message = "IPA structure recognized successfully."
                )
            }
        } catch (e: Exception) {
            failure("Unable to inspect IPA: ${e.message}")
        }
    }

    fun extract(
        ipaFile: File,
        outputDirectory: File
    ): IPAInfo {

        if (!ipaFile.exists() || !ipaFile.isFile) {
            return failure("IPA file does not exist.")
        }

        val output = File(
            outputDirectory,
            ipaFile.nameWithoutExtension + "_Extracted"
        )

        return try {
            output.mkdirs()

            var fileCount = 0
            var appName = "Unknown"
            var bundlePath = ""

            ZipFile(ipaFile).use { zip ->

                val entries = zip.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    val relativePath = entry.name

                    if (relativePath.startsWith("/") ||
                        relativePath.contains("\\") ||
                        relativePath.split("/").any {
                            it == ".."
                        }
                    ) {
                        continue
                    }

                    val destination = File(
                        output,
                        relativePath
                    ).canonicalFile

                    if (!destination.toPath().startsWith(
                            output.canonicalFile.toPath()
                        )) {
                        continue
                    }

                    if (entry.isDirectory) {
                        destination.mkdirs()
                        continue
                    }

                    destination.parentFile?.mkdirs()

                    zip.getInputStream(entry).use { input ->
                        destination.outputStream().use { stream ->
                            input.copyTo(stream)
                        }
                    }

                    fileCount++

                    if (relativePath.startsWith("Payload/") &&
                        relativePath.endsWith(".app/")
                    ) {
                        bundlePath = relativePath
                        appName = relativePath
                            .removePrefix("Payload/")
                            .removeSuffix(".app/")
                    }
                }
            }

            IPAInfo(
                success = true,
                appName = appName,
                bundlePath = bundlePath,
                fileCount = fileCount,
                extractedPath = output.absolutePath,
                message = "Extraction completed."
            )

        } catch (e: Exception) {
            failure("Extraction failed: ${e.message}")
        }
    }

    private fun failure(message: String): IPAInfo {
        return IPAInfo(
            success = false,
            appName = "",
            bundlePath = "",
            fileCount = 0,
            extractedPath = "",
            message = message
        )
    }
}
