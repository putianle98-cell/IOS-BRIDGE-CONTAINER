package com.iosbridge.container

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MachOAnalyzer {

    data class MachOInfo(
        val success: Boolean,
        val format: String,
        val architecture: String,
        val fileType: String,
        val isUniversal: Boolean,
        val message: String
    )

    fun analyze(file: File): MachOInfo {

        if (!file.exists() || !file.isFile) {
            return failure("文件不存在。")
        }

        return try {
            file.inputStream().use { input ->

                val header = ByteArray(32)
                val count = input.read(header)

                if (count < 8) {
                    return failure("文件头长度不足。")
                }

                val magic = header.take(4)
                    .joinToString("") {
                        "%02X".format(it.toInt() and 0xFF)
                    }

                when (magic) {

                    "FEEDFACE" ->
                        analyzeHeader(header, ByteOrder.BIG_ENDIAN, false)

                    "CEFAEDFE" ->
                        analyzeHeader(header, ByteOrder.LITTLE_ENDIAN, false)

                    "FEEDFACF" ->
                        analyzeHeader(header, ByteOrder.BIG_ENDIAN, true)

                    "CFFAEDFE" ->
                        analyzeHeader(header, ByteOrder.LITTLE_ENDIAN, true)

                    "CAFEBABE", "BEBAFECA",
                    "CAFEBABF", "BFBAFECA" ->
                        failure("检测到 Universal Binary，需要进一步分析架构表。")

                    else ->
                        failure("未识别到 Mach-O 文件头。")
                }
            }

        } catch (e: Exception) {
            failure("Mach-O 分析失败：${e.message}")
        }
    }

    private fun analyzeHeader(
        header: ByteArray,
        order: ByteOrder,
        is64Bit: Boolean
    ): MachOInfo {

        val buffer = ByteBuffer.wrap(header)
            .order(order)

        val cpuType = buffer.getInt(4)
        val fileType = buffer.getInt(12)

        val architecture = when (cpuType) {
            7 -> "x86"
            0x01000007 -> "x86_64"
            12 -> "ARM"
            0x0100000C -> "ARM64"
            else -> "未知架构"
        }

        val typeName = when (fileType) {
            1 -> "OBJECT"
            2 -> "EXECUTE"
            6 -> "DYLIB"
            8 -> "BUNDLE"
            else -> "其他类型 ($fileType)"
        }

        return MachOInfo(
            success = true,
            format = if (is64Bit) "Mach-O 64-bit" else "Mach-O 32-bit",
            architecture = architecture,
            fileType = typeName,
            isUniversal = false,
            message = "Mach-O 文件头识别成功。"
        )
    }

    private fun failure(message: String): MachOInfo {
        return MachOInfo(
            success = false,
            format = "UNKNOWN",
            architecture = "未知",
            fileType = "未知",
            isUniversal = false,
            message = message
        )
    }
}
