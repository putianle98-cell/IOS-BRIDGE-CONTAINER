
package com.iosbridge.container

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MachOAnalyzer {

        data class MachOInfo(
        val success: Boolean,
        val format: String,
        val architecture: String,
        val fileType: String,
        val isUniversal: Boolean,
        val message: String,
        val dependencies: List<String> = emptyList(),
        val minimumOS: String = "未检测到",
        val platform: String = "未检测到"
    )

    fun analyze(file: File): MachOInfo {

        if (!file.exists() || !file.isFile) {
            return failure("文件不存在。")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->

                if (raf.length() < 4) {
                    return failure("文件长度不足。")
                }

                val magicBytes = ByteArray(4)
                raf.readFully(magicBytes)

                val magic = magicBytes.joinToString("") {
                    "%02X".format(it.toInt() and 0xFF)
                }

                when (magic) {

                    "FEEDFACE" ->
                        analyzeThin(raf, ByteOrder.BIG_ENDIAN, false)

                    "CEFAEDFE" ->
                        analyzeThin(raf, ByteOrder.LITTLE_ENDIAN, false)

                    "FEEDFACF" ->
                        analyzeThin(raf, ByteOrder.BIG_ENDIAN, true)

                    "CFFAEDFE" ->
                        analyzeThin(raf, ByteOrder.LITTLE_ENDIAN, true)

                    "CAFEBABE" ->
                        analyzeUniversal(raf, ByteOrder.BIG_ENDIAN, false)

                    "BEBAFECA" ->
                        analyzeUniversal(raf, ByteOrder.LITTLE_ENDIAN, false)

                    "CAFEBABF" ->
                        analyzeUniversal(raf, ByteOrder.BIG_ENDIAN, true)

                    "BFBAFECA" ->
                        analyzeUniversal(raf, ByteOrder.LITTLE_ENDIAN, true)

                    else ->
                        failure("未识别到 Mach-O 文件头。")
                }
            }

        } catch (e: Exception) {
            failure("Mach-O 分析失败：${e.message}")
        }
    }

    private fun analyzeThin(
        raf: RandomAccessFile,
        order: ByteOrder,
        is64Bit: Boolean
    ): MachOInfo {

        if (raf.length() < 28) {
            return failure("Mach-O 文件头长度不足。")
        }

        val header = ByteArray(32)
        raf.seek(0)
        raf.readFully(header, 0, 28)

        if (is64Bit && raf.length() < 32) {
            return failure("64 位 Mach-O 文件头不完整。")
        }

        if (is64Bit) {
            raf.seek(0)
            raf.readFully(header)
        }

        val buffer = ByteBuffer.wrap(header).order(order)

        val cpuType = buffer.getInt(4)
        val cpuSubtype = buffer.getInt(8)
        val fileType = buffer.getInt(12)

        return MachOInfo(
            success = true,
            format = if (is64Bit) "Mach-O 64-bit" else "Mach-O 32-bit",
            architecture = architectureName(cpuType, cpuSubtype),
            fileType = fileTypeName(fileType),
            isUniversal = false,
                message = "Mach-O 文件头识别成功。",
            dependencies = loadInfo.dependencies,
            minimumOS = loadInfo.minimumOS,
            platform = loadInfo.platform
        )
    }

    private fun analyzeUniversal(
        raf: RandomAccessFile,
        order: ByteOrder,
        is64Bit: Boolean
    ): MachOInfo {

        val headerSize = if (is64Bit) 8L else 8L
        val entrySize = if (is64Bit) 32L else 20L

        if (raf.length() < headerSize) {
            return failure("Universal Binary 文件头不完整。")
        }

        val header = ByteArray(8)
        raf.seek(0)
        raf.readFully(header)

        val buffer = ByteBuffer.wrap(header).order(order)
        val count = buffer.getInt(4)

        if (count <= 0 || count > 64) {
            return failure("Universal Binary 架构数量无效。")
        }

        val tableEnd = headerSize + count.toLong() * entrySize

        if (tableEnd > raf.length()) {
            return failure("Universal Binary 架构表超出文件范围。")
        }

        val architectures = mutableListOf<String>()

        repeat(count) {

            val entry = ByteArray(entrySize.toInt())

            raf.seek(headerSize + it.toLong() * entrySize)
            raf.readFully(entry)

            val entryBuffer = ByteBuffer.wrap(entry).order(order)

            val cpuType = entryBuffer.getInt(0)
            val cpuSubtype = entryBuffer.getInt(4)

            val sliceOffset: Long
            val sliceSize: Long

            if (is64Bit) {
                sliceOffset = entryBuffer.getLong(8)
                sliceSize = entryBuffer.getLong(16)
            } else {
                sliceOffset = entryBuffer.getInt(8).toLong() and 0xFFFFFFFFL
                sliceSize = entryBuffer.getInt(12).toLong() and 0xFFFFFFFFL
            }

            if (
                sliceOffset < tableEnd ||
                sliceSize <= 0 ||
                sliceOffset > raf.length() ||
                sliceSize > raf.length() - sliceOffset
            ) {
                return failure("发现无效的架构数据区域。")
            }

            val name = architectureName(cpuType, cpuSubtype)

            if (!architectures.contains(name)) {
                architectures.add(name)
            }
        }

        if (architectures.isEmpty()) {
            return failure("没有找到有效的 CPU 架构。")
        }

        return MachOInfo(
            success = true,
            format = if (is64Bit) {
                "Universal Binary 64-bit"
            } else {
                "Universal Binary 32-bit"
            },
            architecture = architectures.joinToString(", "),
            fileType = "包含多架构切片",
            isUniversal = true,
            message = "成功识别 ${architectures.size} 种架构。"
        )
    }

    private fun architectureName(
        cpuType: Int,
        cpuSubtype: Int
    ): String {

        val subtype = cpuSubtype and 0x00FFFFFF

        return when (cpuType) {
            7 -> "x86"
            0x01000007 -> "x86_64"
            12 -> "ARM"
            0x0100000C -> {
                if (subtype == 2) "ARM64E" else "ARM64"
            }
            else -> "未知架构 ($cpuType)"
        }
    }

    private fun fileTypeName(fileType: Int): String {
        return when (fileType) {
            1 -> "OBJECT"
            2 -> "EXECUTE"
            6 -> "DYLIB"
            8 -> "BUNDLE"
            else -> "其他类型 ($fileType)"
        }
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
