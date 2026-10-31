
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

private lateinit var searchInput: EditText
private var sortNewestFirst = true
    
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

        item.addView(nameView)
        item.addView(formatView)
        item.addView(sizeView)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.bottomMargin = 12
        fileList.addView(item, params)
    }

    statusText.text = "显示 ${files.size} 个文件，共 ${allFiles.size} 个文件"
}
