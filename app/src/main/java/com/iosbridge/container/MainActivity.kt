
package com.iosbridge.container

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File
import android.widget.EditText

class MainActivity : Activity() {

    private lateinit var fileList: LinearLayout
    private lateinit var statusText: TextView

    private lateinit var searchInput: EditText
    private var sortNewestFirst = true

    private val containerDir: File by lazy {
        File(filesDir, "IOSBridge/Imported").apply {
            mkdirs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(12, 19, 29)
        window.navigationBarColor = Color.rgb(12, 19, 29)

        createInterface()
        refreshFiles()
    }
    
    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(12, 19, 29))
        }

        val title = TextView(this).apply {
            text = "iOS Bridge"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val subtitle = TextView(this).apply {
            text = "iOS 文件管理环境"
            textSize = 11f
            setTextColor(Color.rgb(95, 177, 220))
            setPadding(0, 6, 0, 25)
        }

        val importButton = Button(this).apply {
            text = "+  导入 iOS 文件"
            setOnClickListener {
                openFilePicker()
            }
        }
        val deviceButton = Button(this).apply {
    text = "检测设备运行环境"
    setOnClickListener {
        showDeviceReport()
    }
         }

        statusText = TextView(this).apply {
            text = "容器已就绪"
            textSize = 13f
            setTextColor(Color.rgb(90, 205, 160))
            setPadding(0, 22, 0, 18)
        }

        val section = TextView(this).apply {
            text = "已导入的文件"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 14)
        }

        val scrollView = ScrollView(this)

        fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(fileList)

        root.addView(title)
        root.addView(subtitle)
        root.addView(importButton)
        root.addView(deviceButton)
        root.addView(statusText)
        root.addView(section)
        
        val searchRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

searchInput = EditText(this).apply {
    hint = "搜索文件名称..."
    textSize = 14f
    setSingleLine(true)
    setTextColor(Color.WHITE)
    setHintTextColor(Color.GRAY)
    setPadding(14, 8, 14, 8)
    setBackgroundColor(Color.rgb(27, 39, 53))

    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?, start: Int, count: Int, after: Int
        ) {}

        override fun onTextChanged(
            s: CharSequence?, start: Int, before: Int, count: Int
        ) {
            refreshFiles()
        }

        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}

val sortButton = Button(this).apply {
    text = "最新优先"
    textSize = 11f

    setOnClickListener {
        sortNewestFirst = !sortNewestFirst
        text = if (sortNewestFirst) "最新优先" else "最旧优先"
        refreshFiles()
    }
}

searchRow.addView(
    searchInput,
    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
)

searchRow.addView(
    sortButton,
    LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
)

root.addView(
    searchRow,
    LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = 14
    }
)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        startActivityForResult(intent, 1001)
    }

    @Deprecated("Using the activity result callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 1001 || resultCode != Activity.RESULT_OK) {
            return
        }

        val uris = mutableListOf<Uri>()

        data?.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                uris.add(clip.getItemAt(i).uri)
            }
        }

        if (uris.isEmpty()) {
            data?.data?.let { uris.add(it) }
        }

        var imported = 0

        for (uri in uris) {
            try {
                val permissionFlags = data?.flags?.and(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                ) ?: 0

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        permissionFlags
                    )
                } catch (_: Exception) {
                    // 某些文件提供程序不支持持久化权限。
                }

                val originalName = getFileName(uri)
                val destination = uniqueFile(originalName)

                contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: continue

                imported++
            } catch (e: Exception) {
                statusText.text = "导入错误：${e.message}"
            }
        }

        if (imported > 0) {
            statusText.text = "成功导入 $imported 个文件"
        } else if (uris.isNotEmpty()) {
            statusText.text = "文件导入失败"
        }

        refreshFiles()
    }

    private fun getFileName(uri: Uri): String {
        var name = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?: "未知文件"

        try {
            val cursor = contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )

                    if (index >= 0) {
                        name = it.getString(index) ?: name
                    }
                }
            }
        } catch (_: Exception) {
        }

        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(180)
            .ifBlank { "未知文件" }
    }

    private fun uniqueFile(name: String): File {
        var file = File(containerDir, name)
        var counter = 1

        while (file.exists()) {
            val base = name.substringBeforeLast(".", name)
            val extension = name.substringAfterLast(
                ".",
                ""
            ).let { if (name.contains(".")) ".$it" else "" }

            file = File(
                containerDir,
                "${base}_$counter$extension"
            )

            counter++
        }

        return file
    }

    

    private fun showDeviceReport() {
    val activityManager =
        getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager

    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val totalMemory =
        memoryInfo.totalMem / (1024L * 1024L)

    val availableMemory =
        memoryInfo.availMem / (1024L * 1024L)

    val cpuArchitecture =
        android.os.Build.SUPPORTED_ABIS.joinToString("\n")

    val cpuCores =
        Runtime.getRuntime().availableProcessors()

    val kernelVersion =
        System.getProperty("os.version") ?: "未知"

    val kvmFile = File("/dev/kvm")

val kvmModule = File("/sys/module/kvm")
val kvmArmModule = File("/sys/module/kvm_arm")
val virtualizationPaths = listOf(
    "/dev/kvm",
    "/dev/vhost-vsock",
    "/sys/module/kvm",
    "/sys/module/kvm_arm",
    "/sys/hypervisor"
)


val virtualizationReport = virtualizationPaths.joinToString("\n") { path ->
    val file = File(path)

    val status = when {
        !file.exists() -> "不存在或不可见"
        file.isDirectory -> "存在（目录）"
        file.canRead() -> "存在（可读取）"
        else -> "存在但无法读取"
    }

    "$path：$status"
}

val kernelConfigFiles = listOf(
    File("/proc/config.gz"),
    File("/proc/config")
)

val kernelConfigReport = buildString {
    val configFile = kernelConfigFiles.firstOrNull {
        it.exists() && it.canRead()
    }

    if (configFile == null) {
        append("内核配置文件：当前应用无法读取或文件不存在")
    } else {
        try {
            val lines = if (configFile.name == "config.gz") {
                java.util.zip.GZIPInputStream(
                    configFile.inputStream()
                ).bufferedReader().use {
                    it.readLines()
                }
            } else {
                configFile.readLines()
            }

            val keys = listOf(
                "CONFIG_KVM=",
                "CONFIG_KVM_ARM_HOST=",
                "CONFIG_VIRTUALIZATION="
            )

            for (key in keys) {
                val result = lines.firstOrNull {
                    it.startsWith(key) ||
                    it.startsWith("# $key")
                }

                append(result ?: "$key 未找到")
                append("\n")
            }
        } catch (e: Exception) {
            append("读取失败：${e.javaClass.simpleName}")
        }
    }
}

val cpuFeatureReport = try {
    File("/proc/cpuinfo").readLines()
        .filter {
            it.startsWith("Features") ||
            it.startsWith("flags")
        }
        .distinct()
        .joinToString("\n")
        .ifBlank { "CPU 特征信息不可读取" }
} catch (e: Exception) {
    "读取失败：${e.javaClass.simpleName}"
}

    }
}

val cpuFeatureReport = try {
    File("/proc/cpuinfo").readLines()
        .filter {
            it.startsWith("Features") ||
            it.startsWith("flags")
        }
        .distinct()
        .joinToString("\n")
        .ifBlank { "CPU 特征信息不可读取" }
} catch (e: Exception) {
    "读取失败：${e.javaClass.simpleName}"
}

    "$path：$status"
}

    val kvmStatus = when {
        !kvmFile.exists() -> "未发现 /dev/kvm"
        !kvmFile.canRead() || !kvmFile.canWrite() ->
            "存在，但当前应用无法访问"
        else -> "存在且当前应用可读写"
    }

    val report = """
设备型号：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}

Android 版本：${android.os.Build.VERSION.RELEASE}
Android SDK：${android.os.Build.VERSION.SDK_INT}

CPU 架构：
$cpuArchitecture

可用 CPU 核心数：$cpuCores

Linux 内核版本：
$kernelVersion

总内存：${totalMemory} MB
当前可用内存：${availableMemory} MB

虚拟化设备节点：
$kvmStatus

系统虚拟化接口检查：
$virtualizationReport

内核配置检查：
$kernelConfigReport

CPU 特征信息：
$cpuFeatureReport

注意：
以上结果仅用于初步设备环境检查，
不能证明设备能够启动 iOS。
    """.trimIndent()

    android.app.AlertDialog.Builder(this)
        .setTitle("设备环境检测报告")
        .setMessage(report)
        .setPositiveButton("确定", null)
        .setNeutralButton("复制报告") { _, _ ->
            val clipboard =
                getSystemService(CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager

            val clip =
                android.content.ClipData.newPlainText(
                    "设备环境检测报告",
                    report
                )

            clipboard.setPrimaryClip(clip)

            Toast.makeText(
                this,
                "检测报告已复制",
                Toast.LENGTH_SHORT
            ).show()
        }
        .show()
}
    
    private fun refreshFiles() {
    fileList.removeAllViews()

    val keyword = if (::searchInput.isInitialized) {
        searchInput.text.toString().trim()
    } else {
        ""
    }

    val allFiles = containerDir.listFiles()
        ?.filter { it.isFile }
        ?: emptyList()

    val filteredFiles = allFiles.filter {
        it.name.contains(keyword, ignoreCase = true)
    }

    val files = if (sortNewestFirst) {
        filteredFiles.sortedByDescending { it.lastModified() }
    } else {
        filteredFiles.sortedBy { it.lastModified() }
    }

    if (files.isEmpty()) {
        val empty = TextView(this).apply {
            text = if (keyword.isEmpty()) {
                "暂无文件\n\n导入你的第一个 iOS 文件"
            } else {
                "没有找到匹配的文件\n\n试试其他关键词"
            }
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(12, 55, 12, 55)
        }

        fileList.addView(empty)
        statusText.text = if (keyword.isEmpty()) {
            "容器中已有 0 个文件"
        } else {
            "没有找到匹配的文件"
        }
        return
    }

    for (file in files) {
        val analysis = try {
            FileAnalyzer.analyze(file)
        } catch (_: Exception) {
            null
        }

        val format = analysis?.format ?: "未知格式"
        val size = analysis?.size ?: "未知大小"

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.rgb(27, 39, 53))

            setOnClickListener {
                val previewIntent = Intent(
                    this@MainActivity,
                    FilePreviewActivity::class.java
                ).apply {
                    putExtra("file_path", file.absolutePath)
                }

                startActivity(previewIntent)
            }
        }

        val nameView = TextView(this).apply {
            text = "📄  ${file.name}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val formatView = TextView(this).apply {
            text = "文件格式：$format"
            textSize = 12f
            setTextColor(Color.rgb(54, 185, 246))
            setPadding(0, 9, 0, 0)
        }

        val sizeView = TextView(this).apply {
            text = "文件大小：$size"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 5, 0, 0)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val renameButton = Button(this).apply {
            text = "重命名"
            textSize = 12f

            setOnClickListener {
                val input = EditText(this@MainActivity).apply {
                    setSingleLine(true)
                    setText(file.name)
                    setSelection(text.length)
                    setPadding(24, 12, 24, 12)
                }

                val dialog = android.app.AlertDialog.Builder(
                    this@MainActivity
                )
                    .setTitle("重命名文件")
                    .setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("保存", null)
                    .create()

                dialog.setOnShowListener {
                    dialog.getButton(
                        android.app.AlertDialog.BUTTON_POSITIVE
                    ).setOnClickListener {
                        val newName = input.text.toString().trim()

                        if (newName.isBlank()) {
                            input.error = "文件名不能为空"
                            return@setOnClickListener
                        }

                        if (newName.contains("/") ||
                            newName.contains("\\") ||
                            newName == "." ||
                            newName == ".."
                        ) {
                            input.error = "文件名包含不允许的字符"
                            return@setOnClickListener
                        }

                        val destination = File(containerDir, newName)

                        if (destination.exists()) {
                            input.error = "这个文件名已经存在"
                            return@setOnClickListener
                        }

                        if (file.renameTo(destination)) {
                            Toast.makeText(
                                this@MainActivity,
                                "文件重命名成功",
                                Toast.LENGTH_SHORT
                            ).show()

                            dialog.dismiss()
                            refreshFiles()
                        } else {
                            input.error = "重命名失败，请重试"
                        }
                    }
                }

                dialog.show()
            }
        }

        val deleteButton = Button(this).apply {
            text = "删除"
            textSize = 12f

            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("删除文件")
                    .setMessage("确定要删除「${file.name}」吗？此操作无法撤销。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确认删除") { _, _ ->
                        if (file.delete()) {
                            Toast.makeText(
                                this@MainActivity,
                                "文件已删除",
                                Toast.LENGTH_SHORT
                            ).show()

                            refreshFiles()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "删除失败，请重试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .show()
            }
        }

        actionRow.addView(
            renameButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        actionRow.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        item.addView(nameView)
        item.addView(formatView)
        item.addView(sizeView)
        item.addView(actionRow)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = 12
        fileList.addView(item, params)
    }

    statusText.text = "显示 ${files.size} 个文件，共 ${allFiles.size} 个文件"
}

}
