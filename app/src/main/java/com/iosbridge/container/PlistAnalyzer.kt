
package com.iosbridge.container

import com.dd.plist.NSDictionary
import com.dd.plist.NSArray
import com.dd.plist.PropertyListParser
import java.io.File

object PlistAnalyzer {

    data class PlistResult(
        val success: Boolean,
        val format: String,
        val entries: List<String>,
        val message: String
    )

    data class IOSAppInfo(
    val bundleIdentifier: String,
    val appName: String,
    val version: String,
    val minimumOSVersion: String,
    val deviceFamily: String,
    val executable: String
)

    fun analyze(file: File): PlistResult {

        if (!file.exists() || !file.isFile) {
            return failure("文件不存在。")
        }

        if (file.length() > 20L * 1024 * 1024) {
            return failure("PLIST 文件超过 20 MB 大小限制。")
        }

        return try {
            val root = PropertyListParser.parse(file)

            val format = when {
                file.readBytes().take(8)
                    .toByteArray()
                    .contentEquals("bplist00".toByteArray()) ->
                    "BINARY PLIST"

                else -> "XML PLIST"
            }

            val entries = mutableListOf<String>()

            fun readValue(
                key: String,
                value: Any,
                depth: Int
            ) {
                if (depth > 8 || entries.size >= 500) return

                when (value) {
                    is NSDictionary -> {
                        val keys = value.allKeys()

                        for (childKey in keys) {
                            val childValue = value.objectForKey(childKey)
                                ?: continue

                            readValue(
                                "$key.$childKey",
                                childValue,
                                depth + 1
                            )
                        }
                    }

                    is NSArray -> {
                        for (i in 0 until value.count()) {
                            val child = value.objectAtIndex(i)
                            readValue("$key[$i]", child, depth + 1)
                        }
                    }

                    else -> {
                        entries.add(
                            "$key = ${value.toString().take(500)}"
                        )
                    }
                }
            }

            when (root) {
                is NSDictionary ->
                    readValue("ROOT", root, 0)

                is NSArray ->
                    readValue("ROOT", root, 0)

                else ->
                    entries.add(root.toString())
            }

            PlistResult(
                success = true,
                format = format,
                entries = entries,
                message = "PLIST 文件解析成功。"
            )

        } catch (e: Exception) {
            failure("PLIST 文件解析失败：${e.message}")
        }
    }

    fun analyzeIOSApp(file: File): IOSAppInfo? {

    if (!file.exists() || !file.isFile) {
        return null
    }

    if (file.length() > 20L * 1024 * 1024) {
        return null
    }

    return try {
        val root = PropertyListParser.parse(file)

        if (root !is NSDictionary) {
            return null
        }

        fun getString(key: String): String {
            return root.objectForKey(key)?.toString() ?: "未知"
        }

        val familyArray =
    root.objectForKey("UIDeviceFamily") as? NSArray

val families = mutableListOf<Int>()

if (familyArray != null) {
    for (i in 0 until familyArray.count()) {
        val value = familyArray.objectAtIndex(i)
    .toString()
    .toDoubleOrNull()
    ?.toInt()

        if (value != null) {
            families.add(value)
        }
    }
}

val deviceFamily = when {
    families.contains(1) && families.contains(2) ->
        "iPhone / iPad"

    families.contains(1) ->
        "iPhone"

    families.contains(2) ->
        "iPad"

    else -> "未知"
}
        IOSAppInfo(
            bundleIdentifier = getString("CFBundleIdentifier"),
            appName = getString("CFBundleDisplayName")
                .let {
                    if (it == "未知") getString("CFBundleName")
                    else it
                },
            version = getString("CFBundleShortVersionString"),
            minimumOSVersion = getString("MinimumOSVersion"),
            deviceFamily = deviceFamily,
            executable = getString("CFBundleExecutable")
        )

    } catch (_: Exception) {
        null
    }
}
    
    private fun failure(message: String): PlistResult {
        return PlistResult(
            success = false,
            format = "UNKNOWN",
            entries = emptyList(),
            message = message
        )
    }
}
