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
            text = "文件详情"
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
            addText("错误：文件路径不存在。")
            return
        }

        val file = File(path)

        if (!file.exists() || !file.isFile) {
            addText("错误：找不到文件。")
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

        addText("文件格式：$format", 14f)
        addText("文件大小：${analysis?.size ?: "未知"}", 14f)
        addText("--------------------------------")

        when (format) {

         "Mach-O" -> {
                val result = MachOAnalyzer.analyze(file)

                addText("Mach-O 可执行文件分析", 18f, true)
                addText("分析状态：${if (result.success) "成功" else "失败"}")
                addText("文件格式：${result.format}")
                addText("CPU 架构：${result.architecture}")
                addText("文件类型：${result.fileType}")
                addText(
                    "通用二进制：${if (result.isUniversal) "是" else "否"}"
                )
                addText("分析说明：${result.message}")
                addText("--------------------------------")
                addText("系统依赖分析", 18f, true)

                addText("目标平台：${result.platform}")
                addText("最低系统版本：${result.minimumOS}")

                addText("--------------------------------")
                addText(
                    "动态库依赖（${result.dependencies.size} 项）",
                    16f,
                    true
                )

                if (result.dependencies.isEmpty()) {
                    addText("没有检测到动态库依赖，或文件未提供相关加载命令。")
                } else {
                    result.dependencies.take(200).forEach {
                        addText(it, 12f)
                    }
                }

                if (result.success) {
                    addText("--------------------------------")
                    addText("兼容性提示", 16f, true)
                    addText(
                        "识别到的 CPU 架构仅代表文件包含相应机器代码，" +
                        "不代表当前 Android 系统能够直接运行该程序。"
                    )
                }
         }   
            
            "IPA" -> {
                val result = IPAExtractor.inspect(file)

                addText("IPA 应用信息", 18f, true)
                addText("分析成功：${if (result.success) "是" else "否"}")
                addText("应用名称：${result.appName}")
                addText("应用包路径：${result.bundlePath}")
                addText("文件数量：${result.fileCount}")
                addText(result.message)
            }

            "PLIST", "Binary PLIST" -> {
                val result = PlistAnalyzer.analyze(file)

                addText("属性列表数据", 18f, true)
                addText(result.message)

                result.entries.take(500).forEach {
                    addText(it, 13f)
                }
            }

            "ZIP ARCHIVE" -> {
                val result = ArchiveAnalyzer.inspect(file)

                addText("压缩包内容", 18f, true)
                addText("文件数量：${result.totalFiles}")
                addText("文件夹数量：${result.totalDirectories}")
                addText("解压后大小：${result.totalSize} 字节")
                addText(result.message)

                result.entries.take(300).forEach {
                    val type = if (it.isDirectory) "[文件夹]" else "[文件]"
                    addText("$type ${it.name}（${it.size} 字节）", 12f)
                }
            }

            "SQLite", "DATABASE" -> {
                val result = SQLiteAnalyzer.inspect(file)

                addText("数据库结构", 18f, true)
                addText(result.message)

                result.tables.forEach { table ->
                    addText("数据表：${table.name}", 16f, true)
                    addText("记录数量：${table.rowCount}")

                    table.columns.forEach { column ->
                        addText(
                            "${column.name}：${column.type} " +
                            if (column.primaryKey) "[主键]" else ""
                        )
                    }
                }
            }

            "PNG IMAGE", "JPEG IMAGE" -> {
                showImage(file)
            }

            "JSON", "XML" -> {
                addText("文档内容", 18f, true)

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
                    addText("预览内容最多显示 32 KB。")
                } catch (e: Exception) {
                    addText("无法读取文档：${e.message}")
                }
            }

            else -> {
                addText("暂不支持预览此格式。")
                addText(analysis?.description ?: "未知文件。")
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
                    "图片尺寸：${bounds.outWidth} × ${bounds.outHeight}"
                )
            } else {
                addText("无法解码图片。")
            }

        } catch (e: Exception) {
            addText("图片预览失败：${e.message}")
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
