
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

  private data class LoadInfo(
        val dependencies: List<String>,
        val minimumOS: String,
        val platform: String
    )

    private fun analyzeLoadCommands(
        raf: RandomAccessFile,
        order: ByteOrder,
        is64Bit: Boolean
    ): LoadInfo {

        val headerSize = if (is64Bit) 32L else 28L

        raf.seek(16)
        val headerData = ByteArray(8)
        raf.readFully(headerData)

        val headerBuffer = ByteBuffer.wrap(headerData).order(order)
        val commandCount = headerBuffer.getInt(0)
        val commandBytes = headerBuffer.getInt(4)

        if (
            commandCount < 0 ||
            commandCount > 10000 ||
            commandBytes < 0 ||
            commandBytes > 16 * 1024 * 1024 ||
            headerSize + commandBytes > raf.length()
        ) {
            return LoadInfo(
                emptyList(),
                "加载命令无效",
                "未检测到"
            )
        }

        val dependencies = mutableListOf<String>()
        var minimumOS = "未检测到"
        var platform = "未检测到"

        var position = headerSize
        val end = headerSize + commandBytes

        repeat(commandCount) {

            if (position + 8 > end) {
                return@repeat
            }

            raf.seek(position)

            val commandHeader = ByteArray(8)
            raf.readFully(commandHeader)

            val commandBuffer =
                ByteBuffer.wrap(commandHeader).order(order)

            val command = commandBuffer.getInt(0)
            val commandSize = commandBuffer.getInt(4)

            if (
                commandSize < 8 ||
                position + commandSize > end
            ) {
                return LoadInfo(
                    dependencies,
                    "加载命令结构异常",
                    platform
                )
            }

            when (command) {

                0x0C,
                0x18,
                0x1F,
                0x20,
                0x80000018.toInt(),
                0x8000001F.toInt(),
                0x80000023.toInt() -> {

                    if (commandSize >= 24) {
                        val data = ByteArray(commandSize)
                        raf.seek(position)
                        raf.readFully(data)

                        val buffer =
                            ByteBuffer.wrap(data).order(order)

                        val nameOffset = buffer.getInt(8)

                        if (
                            nameOffset >= 8 &&
                            nameOffset < commandSize
                        ) {
                            val name = String(
                                data,
                                nameOffset,
                                commandSize - nameOffset,
                                Charsets.UTF_8
                            ).substringBefore('\u0000')

                            if (
                                name.isNotBlank() &&
                                !dependencies.contains(name)
                            ) {
                                dependencies.add(name)
                            }
                        }
                    }
                }

                0x24 -> {
                    if (commandSize >= 16) {
                        val data = ByteArray(16)
                        raf.seek(position)
                        raf.readFully(data)

                        val buffer =
                            ByteBuffer.wrap(data).order(order)

                        minimumOS = formatVersion(buffer.getInt(8))
                        platform = "macOS"
                    }
                }

                0x32 -> {
                    if (commandSize >= 24) {
                        val data = ByteArray(24)
                        raf.seek(position)
                        raf.readFully(data)

                        val buffer =
                            ByteBuffer.wrap(data).order(order)

                        val platformId = buffer.getInt(8)
                        val version = buffer.getInt(12)

                        platform = when (platformId) {
                            1 -> "macOS"
                            2 -> "iOS"
                            3 -> "tvOS"
                            4 -> "watchOS"
                            5 -> "桥接系统"
                            6 -> "Mac Catalyst"
                            7 -> "iOS Simulator"
                            8 -> "tvOS Simulator"
                            9 -> "watchOS Simulator"
                            10 -> "DriverKit"
                            11 -> "visionOS"
                            12 -> "visionOS Simulator"
                            else -> "未知平台 ($platformId)"
                        }

                        minimumOS = formatVersion(version)
                    }
                }
            }

            position += commandSize
        }

        return LoadInfo(
            dependencies,
            minimumOS,
            platform
        )
    }

    private fun formatVersion(version: Int): String {
        val major = (version ushr 16) and 0xFFFF
        val minor = (version ushr 8) and 0xFF
        val patch = version and 0xFF

        return "$major.$minor.$patch"
    }
    
}
