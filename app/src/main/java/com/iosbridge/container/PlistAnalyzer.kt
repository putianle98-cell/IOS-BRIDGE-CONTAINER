
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

    fun analyze(file: File): PlistResult {

        if (!file.exists() || !file.isFile) {
            return failure("File does not exist.")
        }

        if (file.length() > 20L * 1024 * 1024) {
            return failure("PLIST file exceeds 20 MB limit.")
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
                        val keys = value.allKeys

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
                message = "PLIST parsed successfully."
            )

        } catch (e: Exception) {
            failure("PLIST parsing failed: ${e.message}")
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
