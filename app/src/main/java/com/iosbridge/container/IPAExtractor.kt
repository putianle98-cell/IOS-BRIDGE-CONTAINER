
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
            return failure("无效的 IPA 文件。")
        }

        return try {
            ZipFile(ipaFile).use { zip ->

                val entries = zip.entries().asSequence()
                    .toList()

                val appEntry = entries.firstOrNull {
    it.name.startsWith("Payload/") &&
    it.name.contains(".app/")
}

val bundlePath = appEntry?.name
    ?.substringBefore(".app/")
    ?.plus(".app/")
    ?: return failure("未找到 Payload 应用目录。")

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
                    message = "IPA 文件结构识别成功。"
                )
            }
        } catch (e: Exception) {
            failure("无法分析 IPA 文件：${e.message}")
        }
    }

    fun analyzeAppInfo(ipaFile: File): PlistAnalyzer.IOSAppInfo? {

    if (!ipaFile.exists() ||
        !ipaFile.isFile ||
        ipaFile.extension.lowercase() != "ipa"
    ) {
        return null
    }

    return try {
        ZipFile(ipaFile).use { zip ->

            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()

                if (entry.isDirectory ||
                    !entry.name.startsWith("Payload/") ||
                    !entry.name.endsWith(".app/Info.plist")
                ) {
                    continue
                }

                val tempFile = File.createTempFile(
                    "iosbridge_",
                    ".plist"
                )

                try {
                    zip.getInputStream(entry).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    return PlistAnalyzer.analyzeIOSApp(tempFile)
                } finally {
                    tempFile.delete()
                }
            }

            null
        }
    } catch (_: Exception) {
        null
    }
}
    
    fun extract(
        ipaFile: File,
        outputDirectory: File
    ): IPAInfo {

        if (!ipaFile.exists() || !ipaFile.isFile) {
            return failure("IPA 文件不存在。")
        }

        val output = File(
            outputDirectory,
            ipaFile.nameWithoutExtension + "_Extracted"
        )

        return try {
            output.mkdirs()

            var fileCount = 0
            var appName = "未知"
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
                message = "文件提取完成。"
            )

        } catch (e: Exception) {
            failure("文件提取失败：${e.message}")
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
