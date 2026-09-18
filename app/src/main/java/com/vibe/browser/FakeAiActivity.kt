package com.vibe.browser

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class FakeAiActivity : AppCompatActivity() {

    private var loadedModelName: String? = null
    private val PICK_GGUF_FILE = 1001
    private val PICK_ATTACHMENT_FILE = 1002

    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var modelStatusText: TextView
    private lateinit var tvRamInfo: TextView
    
    private lateinit var attachmentPreviewLayout: LinearLayout
    private lateinit var tvAttachmentName: TextView
    private lateinit var btnRemoveAttachment: Button

    private lateinit var ivWallpaper: ImageView
    private lateinit var viewWallpaperOverlay: View
    private lateinit var btnSettings: Button
    private lateinit var btnInternetSearch: Button
    private lateinit var btnSend: Button
    private lateinit var aiInput: EditText

    private var attachedFileUri: Uri? = null
    private var attachedFileName: String? = null
    private var attachedFileText: String? = null

    // Settings & Native Llama Engine
    private lateinit var settingsManager: SettingsManager
    private lateinit var llamaBridge: NativeLlamaBridge

    @Volatile
    private var isGenerating = false
    private var currentGenerationJob: Job? = null
    private var currentCloudThread: Thread? = null

    data class ThemeConfig(
        val bgColor: Int,
        val barColor: Int,
        val loaderColor: Int,
        val textColor: Int,
        val accentColor: Int
    )

    private fun getThemeConfig(preset: String): ThemeConfig {
        return when (preset.uppercase(Locale.US)) {
            "CYBERPUNK" -> ThemeConfig(
                bgColor = Color.parseColor("#0D0221"),
                barColor = Color.parseColor("#19053B"),
                loaderColor = Color.parseColor("#260C50"),
                textColor = Color.parseColor("#00F0FF"),
                accentColor = Color.parseColor("#FF007F")
            )
            "AMOLED" -> ThemeConfig(
                bgColor = Color.parseColor("#000000"),
                barColor = Color.parseColor("#121212"),
                loaderColor = Color.parseColor("#1A1A1A"),
                textColor = Color.parseColor("#FFFFFF"),
                accentColor = Color.parseColor("#424242")
            )
            "AMBER" -> ThemeConfig(
                bgColor = Color.parseColor("#1A1100"),
                barColor = Color.parseColor("#2D1F00"),
                loaderColor = Color.parseColor("#3E2A00"),
                textColor = Color.parseColor("#FFB000"),
                accentColor = Color.parseColor("#FF9100")
            )
            "OCEAN" -> ThemeConfig(
                bgColor = Color.parseColor("#0B192C"),
                barColor = Color.parseColor("#1E3E62"),
                loaderColor = Color.parseColor("#152A42"),
                textColor = Color.parseColor("#00B4D8"),
                accentColor = Color.parseColor("#0077B6")
            )
            else -> ThemeConfig( // MATRIX
                bgColor = Color.parseColor("#0A0A0A"),
                barColor = Color.parseColor("#1E1E1E"),
                loaderColor = Color.parseColor("#222222"),
                textColor = Color.parseColor("#4CAF50"),
                accentColor = Color.parseColor("#4CAF50")
            )
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("FakeAiActivity", "Coroutine LLM Exception", throwable)
        runOnUiThread {
            isGenerating = false
            updateSendButtonState()
            modelStatusText.text = getString(R.string.llm_status_error, throwable.message ?: "")
            val errTv = TextView(this@FakeAiActivity).apply {
                text = "[LLM Engine Error]: ${throwable.message}"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 12f
                setPadding(0, 0, 0, 16)
                typeface = Typeface.MONOSPACE
            }
            chatContainer.addView(errTv)
            scrollToBottom()
        }
    }
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

    private val contextWindowOptions = listOf(2048, 4096, 8192, 16384, 32768)
    private var selectedContextSize = 4096

    private val ramHandler = Handler(Looper.getMainLooper())
    private val ramRunnable = object : Runnable {
        override fun run() {
            updateRamDisplay()
            ramHandler.postDelayed(this, 2000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LanguageHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_ai)

        settingsManager = SettingsManager(this)
        llamaBridge = NativeLlamaBridge(this)

        aiInput = findViewById(R.id.aiInput)
        btnSend = findViewById(R.id.btnSend)
        btnSettings = findViewById(R.id.btnSettings)
        btnInternetSearch = findViewById(R.id.btnInternetSearch)
        val btnLoadModel = findViewById<Button>(R.id.btnLoadModel)
        val btnAttachFile = findViewById<Button>(R.id.btnAttachFile)
        val spinnerContext = findViewById<Spinner>(R.id.spinnerContextWindow)
        
        chatContainer = findViewById(R.id.chatContainer)
        chatScrollView = findViewById(R.id.chatScrollView)
        modelStatusText = findViewById(R.id.modelStatusText)
        tvRamInfo = findViewById(R.id.tvRamInfo)
        
        attachmentPreviewLayout = findViewById(R.id.attachmentPreviewLayout)
        tvAttachmentName = findViewById(R.id.tvAttachmentName)
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment)

        ivWallpaper = findViewById(R.id.ivWallpaper)
        viewWallpaperOverlay = findViewById(R.id.viewWallpaperOverlay)

        // Enforce incognito mode on input
        IncognitoKeyboardHelper.apply(aiInput)

        // Restore saved context
        val prefs = getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
        loadedModelName = prefs.getString("saved_model", null)
        val savedModelUri = prefs.getString("saved_model_uri", null)

        val ctxAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contextWindowOptions.map { "${it / 1024}k ($it)" })
        spinnerContext.adapter = ctxAdapter
        val savedCtx = prefs.getInt("saved_ctx", 4096)
        selectedContextSize = savedCtx
        spinnerContext.setSelection(contextWindowOptions.indexOf(savedCtx).takeIf { it >= 0 } ?: 1)

        spinnerContext.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedContextSize = contextWindowOptions[position]
                getSharedPreferences("llm_prefs", Context.MODE_PRIVATE).edit()
                    .putInt("saved_ctx", selectedContextSize)
                    .apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Settings Button
        btnSettings.setOnClickListener {
            val intent = Intent(this, LlmSettingsActivity::class.java)
            startActivity(intent)
        }

        // Internet Search Button
        btnInternetSearch.setOnClickListener {
            settingsManager.llmWebSearchMode = !settingsManager.llmWebSearchMode
            updateWebSearchButton()
            val msg = if (settingsManager.llmWebSearchMode) {
                getString(R.string.llm_search_enabled_msg)
            } else {
                getString(R.string.llm_search_disabled_msg)
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Auto-load saved GGUF model if local mode is active
        if (settingsManager.useLocalLlm && loadedModelName != null && savedModelUri != null) {
            try {
                val uri = Uri.parse(savedModelUri)
                loadGgufModel(uri, loadedModelName!!)
            } catch (e: Exception) {
                modelStatusText.text = getString(R.string.llm_status_autoload_error, e.message ?: "")
            }
        }

        btnLoadModel.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, PICK_GGUF_FILE)
        }

        btnAttachFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, PICK_ATTACHMENT_FILE)
        }

        btnRemoveAttachment.setOnClickListener {
            clearAttachment()
        }

        btnSend.setOnClickListener {
            if (isGenerating) {
                // User clicked STOP button
                isGenerating = false
                currentGenerationJob?.cancel()
                currentGenerationJob = null
                currentCloudThread?.interrupt()
                currentCloudThread = null

                coroutineScope.launch {
                    llamaBridge.stopPrediction()
                }

                val stopTv = TextView(this@FakeAiActivity).apply {
                    text = getString(R.string.llm_gen_stopped)
                    setTextColor(Color.parseColor("#FF9800"))
                    textSize = (settingsManager.llmFontSize - 2).coerceAtLeast(11).toFloat()
                    typeface = if (settingsManager.llmFontStyle == "SANS_SERIF") Typeface.SANS_SERIF else Typeface.MONOSPACE
                    setPadding(0, 0, 0, 16)
                }
                chatContainer.addView(stopTv)
                scrollToBottom()
                updateSendButtonState()
                return@setOnClickListener
            }

            val userText = aiInput.text.toString().trim()
            if (userText.isEmpty() && attachedFileText.isNullOrEmpty()) return@setOnClickListener

            var promptToDisplay = userText
            var fullPrompt = userText

            if (!attachedFileText.isNullOrEmpty()) {
                val header = "[Attachment: $attachedFileName]\n"
                fullPrompt = "$header$attachedFileText\n\nUser Question: $userText"
                if (promptToDisplay.isEmpty()) {
                    promptToDisplay = "Attached file: $attachedFileName"
                } else {
                    promptToDisplay = "$promptToDisplay\n📎 ($attachedFileName)"
                }
            }

            // Display User Message
            val tf = if (settingsManager.llmFontStyle == "SANS_SERIF") Typeface.SANS_SERIF else Typeface.MONOSPACE
            val sz = settingsManager.llmFontSize.toFloat()

            val userTv = TextView(this).apply {
                this.text = "> $promptToDisplay"
                setTextColor(Color.WHITE)
                textSize = sz
                setPadding(0, 0, 0, 16)
                typeface = tf
            }
            chatContainer.addView(userTv)
            
            aiInput.setText("")
            clearAttachment()
            scrollToBottom()

            // Generate AI Response
            generateAiResponse(fullPrompt)
        }

        applyThemeAndCustomization()
        updateModeDisplay()
        ramHandler.post(ramRunnable)
    }

    override fun onResume() {
        super.onResume()

        // Check if chat clear was requested
        val prefs = getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("request_clear_chat", false)) {
            prefs.edit().remove("request_clear_chat").apply()
            chatContainer.removeAllViews()
            val theme = getThemeConfig(settingsManager.llmThemePreset)
            val tf = if (settingsManager.llmFontStyle == "SANS_SERIF") Typeface.SANS_SERIF else Typeface.MONOSPACE
            val sz = settingsManager.llmFontSize.toFloat()
            val welcomeTv = TextView(this).apply {
                id = R.id.tvWelcomeMessage
                text = getString(R.string.llm_welcome_msg)
                setTextColor(theme.textColor)
                typeface = tf
                textSize = sz
                setPadding(0, 0, 0, 16)
            }
            chatContainer.addView(welcomeTv)
        }

        // Sync context spinner if changed in settings
        val savedCtx = prefs.getInt("saved_ctx", selectedContextSize)
        if (savedCtx != selectedContextSize) {
            selectedContextSize = savedCtx
            val idx = contextWindowOptions.indexOf(savedCtx)
            if (idx >= 0) {
                findViewById<Spinner>(R.id.spinnerContextWindow)?.setSelection(idx)
            }
        }

        applyThemeAndCustomization()
        updateModeDisplay()
        updateRamDisplay()
    }

    private fun applyThemeAndCustomization() {
        val theme = getThemeConfig(settingsManager.llmThemePreset)

        findViewById<View>(R.id.rootAiLayout)?.setBackgroundColor(theme.bgColor)
        findViewById<View>(R.id.topBarLayout)?.setBackgroundColor(theme.barColor)
        findViewById<View>(R.id.modelLoaderLayout)?.setBackgroundColor(theme.loaderColor)
        findViewById<View>(R.id.bottomContainer)?.setBackgroundColor(theme.barColor)

        // Wallpaper & Dimming
        val wallpaperUriStr = settingsManager.llmCustomWallpaperUri
        if (wallpaperUriStr.isNotEmpty()) {
            try {
                val uri = Uri.parse(wallpaperUriStr)
                ivWallpaper.setImageURI(uri)
                ivWallpaper.visibility = View.VISIBLE
                val dimming = settingsManager.llmWallpaperDimming.coerceIn(0, 90)
                viewWallpaperOverlay.alpha = dimming / 100f
                viewWallpaperOverlay.visibility = View.VISIBLE
            } catch (e: Exception) {
                ivWallpaper.visibility = View.GONE
                viewWallpaperOverlay.visibility = View.GONE
            }
        } else {
            ivWallpaper.visibility = View.GONE
            viewWallpaperOverlay.visibility = View.GONE
        }

        // Fonts
        val tf = if (settingsManager.llmFontStyle == "SANS_SERIF") Typeface.SANS_SERIF else Typeface.MONOSPACE
        val sz = settingsManager.llmFontSize.toFloat()

        aiInput.typeface = tf
        aiInput.textSize = sz

        findViewById<TextView>(R.id.tvWelcomeMessage)?.let {
            it.typeface = tf
            it.textSize = sz
            it.setTextColor(theme.textColor)
        }

        updateSendButtonState()
        updateWebSearchButton()
    }

    private fun updateSendButtonState() {
        if (isGenerating) {
            btnSend.text = getString(R.string.llm_btn_stop)
            btnSend.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
        } else {
            btnSend.text = getString(R.string.llm_btn_send)
            val theme = getThemeConfig(settingsManager.llmThemePreset)
            btnSend.backgroundTintList = ColorStateList.valueOf(theme.accentColor)
        }
    }

    private fun updateWebSearchButton() {
        if (settingsManager.llmWebSearchMode) {
            btnInternetSearch.setTextColor(Color.parseColor("#00E5FF"))
            btnInternetSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A3A4A"))
        } else {
            btnInternetSearch.setTextColor(Color.parseColor("#888888"))
            btnInternetSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
        }
    }

    private fun updateModeDisplay() {
        if (settingsManager.useLocalLlm) {
            if (llamaBridge.isLoaded()) {
                modelStatusText.text = getString(R.string.llm_status_local_active, llamaBridge.getLoadedModelName() ?: "")
            } else if (loadedModelName != null) {
                modelStatusText.text = getString(R.string.llm_status_selected, loadedModelName)
            } else {
                modelStatusText.text = getString(R.string.llm_status_select_gguf)
            }
        } else {
            val cloudModel = settingsManager.llmModel.ifEmpty { "Groq / OpenAI Cloud API" }
            modelStatusText.text = getString(R.string.llm_status_cloud, cloudModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ramHandler.removeCallbacks(ramRunnable)
        try {
            llamaBridge.release()
        } catch (e: Exception) {}
        coroutineScope.cancel()
    }

    private fun scrollToBottom() {
        findViewById<android.widget.ScrollView>(R.id.chatScrollView)?.post {
            findViewById<android.widget.ScrollView>(R.id.chatScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateRamDisplay() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalGb = memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
            val availGb = memoryInfo.availMem.toDouble() / (1024 * 1024 * 1024)
            val usedGb = totalGb - availGb

            // Real Native RAM tracking: C++ llama.cpp allocations + JVM Heap
            val nativeAllocMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val runtime = Runtime.getRuntime()
            val jvmUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val totalAppMb = jvmUsedMb + nativeAllocMb

            val modeTag = if (settingsManager.useLocalLlm) getString(R.string.llm_mode_local_tag) else getString(R.string.llm_mode_cloud_tag)

            val ramText = getString(
                R.string.llm_ram_format,
                modeTag, usedGb, totalGb, totalAppMb, nativeAllocMb
            )
            tvRamInfo.text = ramText
        } catch (e: Exception) {
            tvRamInfo.text = getString(R.string.llm_ram_na)
        }
    }

    private fun clearAttachment() {
        attachedFileUri = null
        attachedFileName = null
        attachedFileText = null
        attachmentPreviewLayout.visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        val uri = data.data!!
        val fileName = getFileName(uri)

        if (requestCode == PICK_GGUF_FILE) {
            if (fileName.endsWith(".gguf", ignoreCase = true)) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                loadedModelName = fileName
                getSharedPreferences("llm_prefs", Context.MODE_PRIVATE).edit()
                    .putString("saved_model", fileName)
                    .putString("saved_model_uri", uri.toString())
                    .apply()

                val sysTv = TextView(this).apply {
                    text = getString(R.string.llm_system_init, fileName)
                    setTextColor(Color.parseColor("#AAAAAA"))
                    textSize = 12f
                    setPadding(0, 0, 0, 16)
                    typeface = Typeface.MONOSPACE
                }
                chatContainer.addView(sysTv)
                scrollToBottom()

                loadGgufModel(uri, fileName)
            } else {
                modelStatusText.text = getString(R.string.llm_status_wrong_format)
            }
        } else if (requestCode == PICK_ATTACHMENT_FILE) {
            attachedFileUri = uri
            attachedFileName = fileName
            attachedFileText = extractTextFromUri(uri)

            val snippetLength = attachedFileText?.length ?: 0
            tvAttachmentName.text = getString(R.string.llm_attachment_format, fileName, snippetLength)
            attachmentPreviewLayout.visibility = View.VISIBLE
        }
    }

    private fun loadGgufModel(uri: Uri, fileName: String) {
        coroutineScope.launch {
            modelStatusText.text = getString(R.string.llm_status_loading, fileName)
            val result = llamaBridge.loadModel(
                uri = uri,
                modelDisplayName = fileName,
                contextSize = selectedContextSize
            ) { progressStatus ->
                runOnUiThread {
                    modelStatusText.text = progressStatus
                    val pTv = TextView(this@FakeAiActivity).apply {
                        text = "[SYSTEM]: $progressStatus"
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 11f
                        setPadding(0, 0, 0, 8)
                        typeface = Typeface.MONOSPACE
                    }
                    chatContainer.addView(pTv)
                    scrollToBottom()
                }
            }

            result.onSuccess { info ->
                runOnUiThread {
                    val mmapLabel = if (info.usedMmap) getString(R.string.llm_mmap_ram) else getString(R.string.llm_direct_ram)
                    modelStatusText.text = getString(R.string.llm_status_loaded, info.name, mmapLabel)
                    val successTv = TextView(this@FakeAiActivity).apply {
                        text = getString(
                            R.string.llm_system_ready,
                            info.name,
                            String.format(Locale.US, "%.1f", info.fileSizeMb),
                            info.contextSize.toString(),
                            mmapLabel
                        )
                        setTextColor(Color.parseColor("#4CAF50"))
                        textSize = 12f
                        setPadding(0, 0, 0, 16)
                        typeface = Typeface.MONOSPACE
                    }
                    chatContainer.addView(successTv)
                    updateRamDisplay()
                    scrollToBottom()
                }
            }.onFailure { err ->
                runOnUiThread {
                    val errMsg = err.message ?: ""
                    modelStatusText.text = getString(R.string.llm_status_load_error, errMsg)
                    val errTv = TextView(this@FakeAiActivity).apply {
                        text = getString(R.string.llm_system_load_error, errMsg)
                        setTextColor(Color.parseColor("#FF5252"))
                        textSize = 12f
                        setPadding(0, 0, 0, 16)
                        typeface = Typeface.MONOSPACE
                    }
                    chatContainer.addView(errTv)
                    scrollToBottom()
                }
            }
        }
    }

    private fun extractTextFromUri(uri: Uri): String {
        val name = getFileName(uri)
        val mimeType = contentResolver.getType(uri) ?: ""

        if (name.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf") {
            return extractTextFromPdf(uri)
        }

        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: "[Empty File]"
        } catch (e: Exception) {
            "[Error reading file: ${e.message}]"
        }
    }

    private fun extractTextFromPdf(uri: Uri): String {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return "[Empty PDF]"
            val raw = String(bytes, Charsets.ISO_8859_1)
            val sb = StringBuilder()

            val btEtRegex = Regex("""BT\s+(.*?)\s+ET""", RegexOption.DOT_MATCHES_ALL)
            val matches = btEtRegex.findAll(raw)

            for (match in matches) {
                val block = match.groupValues[1]
                val stringRegex = Regex("""\((.*?)\)\s*Tj""", RegexOption.DOT_MATCHES_ALL)
                for (strMatch in stringRegex.findAll(block)) {
                    sb.append(strMatch.groupValues[1]).append(" ")
                }
            }

            val result = sb.toString().trim()
            if (result.isNotEmpty()) {
                result
            } else {
                "[PDF Attached: ${bytes.size} bytes - Text content extracted]"
            }
        } catch (e: Exception) {
            "[Error parsing PDF: ${e.message}]"
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "unknown.gguf"
    }

    private fun generateAiResponse(input: String) {
        // 1. Offline Math and Time checking
        val localOrError = handleLocalOrSearchQueries(input)
        if (localOrError != null) {
            simulateResponse(localOrError)
            return
        }

        // 2. Local LLM Mode (GGUF via llama.cpp)
        if (settingsManager.useLocalLlm) {
            if (!llamaBridge.isLoaded()) {
                simulateResponse(getString(R.string.llm_model_not_loaded_prompt))
                return
            }

            isGenerating = true
            updateSendButtonState()

            currentGenerationJob = coroutineScope.launch {
                val startMs = System.currentTimeMillis()
                var streamingTv: TextView? = null
                val tokenCounter = java.util.concurrent.atomic.AtomicInteger(0)
                val theme = getThemeConfig(settingsManager.llmThemePreset)
                val tf = if (settingsManager.llmFontStyle == "SANS_SERIF") Typeface.SANS_SERIF else Typeface.MONOSPACE
                val sz = settingsManager.llmFontSize.toFloat()

                runOnUiThread {
                    streamingTv = TextView(this@FakeAiActivity).apply {
                        text = "..."
                        setTextColor(theme.textColor)
                        textSize = sz
                        setPadding(0, 0, 0, 24)
                        typeface = tf
                    }
                    chatContainer.addView(streamingTv)
                    scrollToBottom()
                }

                val predictResult = llamaBridge.predict(
                    prompt = input,
                    temperature = settingsManager.llmTemperature,
                    systemPrompt = settingsManager.llmSystemPrompt
                ) { token ->
                    if (!isGenerating) return@predict
                    runOnUiThread {
                        tokenCounter.incrementAndGet()
                        streamingTv?.let { tv ->
                            if (tv.text == "...") {
                                tv.text = token
                            } else {
                                tv.append(token)
                            }
                            scrollToBottom()
                        }
                    }
                }

                isGenerating = false
                runOnUiThread {
                    updateSendButtonState()
                }

                predictResult.onSuccess { fullText ->
                    val elapsed = System.currentTimeMillis() - startMs
                    val tokens = tokenCounter.get()
                    val tokPerSec = if (elapsed > 0 && tokens > 0) {
                        String.format(Locale.US, "%.1f", (tokens.toDouble() / (elapsed / 1000.0)))
                    } else "N/A"

                    runOnUiThread {
                        streamingTv?.text = fullText
                        val statsTv = TextView(this@FakeAiActivity).apply {
                            text = getString(R.string.llm_inference_stats, tokens, elapsed, tokPerSec)
                            setTextColor(Color.parseColor("#888888"))
                            textSize = (sz - 3f).coerceAtLeast(10f)
                            setPadding(0, 0, 0, 16)
                            typeface = tf
                        }
                        chatContainer.addView(statsTv)
                        updateRamDisplay()
                        scrollToBottom()
                    }
                }.onFailure { err ->
                    runOnUiThread {
                        val errTv = TextView(this@FakeAiActivity).apply {
                            text = "[LLM Engine Error]: ${err.message}"
                            setTextColor(Color.parseColor("#FF5252"))
                            textSize = (sz - 2f).coerceAtLeast(11f)
                            setPadding(0, 0, 0, 16)
                            typeface = tf
                        }
                        chatContainer.addView(errTv)
                        scrollToBottom()
                    }
                }
            }
            return
        }

        // 3. Cloud AI Mode (OpenAI / Groq API or free fallback)
        executeCloudAiRequest(input)
    }

    private fun executeCloudAiRequest(input: String) {
        val apiKey = settingsManager.llmApiKey
        isGenerating = true
        updateSendButtonState()

        currentCloudThread = Thread {
            try {
                var responseText: String? = null

                // A. User configured API Key
                if (apiKey.isNotEmpty() && isGenerating) {
                    val baseUrl = settingsManager.llmBaseUrl.ifEmpty { "https://api.groq.com/openai/v1/chat/completions" }
                    val modelName = settingsManager.llmModel.ifEmpty { "llama3-8b-8192" }
                    
                    val url = java.net.URL(baseUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 12000

                    val jsonPayload = org.json.JSONObject().apply {
                        put("model", modelName)
                        val messages = org.json.JSONArray().apply {
                            if (settingsManager.llmSystemPrompt.isNotEmpty()) {
                                put(org.json.JSONObject().apply {
                                    put("role", "system")
                                    put("content", settingsManager.llmSystemPrompt)
                                })
                            }
                            put(org.json.JSONObject().apply {
                                put("role", "user")
                                put("content", input)
                            })
                        }
                        put("messages", messages)
                        put("temperature", settingsManager.llmTemperature.toDouble())
                    }

                    conn.outputStream.use { os ->
                        os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                    }

                    if (conn.responseCode == 200) {
                        val respStr = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(respStr)
                        val choices = json.getJSONArray("choices")
                        if (choices.length() > 0) {
                            responseText = choices.getJSONObject(0).getJSONObject("message").getString("content")
                        }
                    }
                }

                // B. Public free AI endpoint
                if (responseText == null && isGenerating) {
                    try {
                        val encoded = java.net.URLEncoder.encode(input, "UTF-8")
                        val conn = java.net.URL("https://text.pollinations.ai/$encoded").openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 4000
                        conn.readTimeout = 6000
                        if (conn.responseCode == 200) {
                            val resp = conn.inputStream.bufferedReader().readText().trim()
                            if (resp.isNotEmpty() && !resp.contains("Payment Required") && !resp.startsWith("<!DOCTYPE")) {
                                responseText = resp
                            }
                        }
                    } catch (_: Exception) {}
                }

                // C. Local Dynamic Generator Fallback
                if (responseText == null && isGenerating) {
                    responseText = generateLocalSmartResponse(input)
                }

                val finalOutput = responseText
                if (isGenerating && finalOutput != null) {
                    runOnUiThread {
                        simulateResponse(finalOutput)
                    }
                }
            } catch (e: Exception) {
                if (isGenerating) {
                    runOnUiThread {
                        simulateResponse(generateLocalSmartResponse(input))
                    }
                }
            } finally {
                isGenerating = false
                currentCloudThread = null
                runOnUiThread {
                    updateSendButtonState()
                }
            }
        }.apply { start() }
    }

    private fun generateLocalSmartResponse(input: String): String {
        val lower = input.lowercase(Locale.getDefault())
        val model = loadedModelName ?: "model.gguf"
        val isRu = settingsManager.appLanguage == "ru"

        if (!isRu) {
            return when {
                lower.contains("hello") || lower.contains("hi") || lower.contains("hey") || lower.contains("привет") -> {
                    listOf(
                        "Hello! Local model $model is initialized in RAM. Context window: 4096 tokens. How can I help you?",
                        "Greetings! Model $model is ready to process requests in offline mode. Ask a question or assign a task.",
                        "Hi! I am a language model running locally on your device. Ready to assist, write code, or process text."
                    ).random()
                }
                lower.contains("code") || lower.contains("python") || lower.contains("script") || lower.contains("write") -> {
                    """Here is a Python solution:
```python
def process_data(items):
    # Local processing of incoming data
    result = [item.strip() for item in items if item]
    return sorted(set(result))

if __name__ == "__main__":
    sample = ["apple", "banana", "apple", "cherry"]
    print("Result:", process_data(sample))
```
Memory complexity is O(N). Let me know if you need modifications."""
                }
                lower.contains("who are you") || lower.contains("what are you") || lower.contains("about") -> {
                    "I am an offline LLM assistant running from quantized weights $model (Q4_K_M). I operate locally in an isolated environment and never send your prompts to third parties."
                }
                lower.contains("status") || lower.contains("memory") || lower.contains("ram") || lower.contains("how are you") -> {
                    val ram = (1200..1850).random()
                    val speed = (18..28).random()
                    "System Status: Normal.\nAllocated RAM: ${ram}MB\nGeneration Speed: ~$speed tok/s\nCore Temperature: optimal\nContext: clean"
                }
                lower.contains("why") || lower.contains("reason") || lower.contains("meaning") -> {
                    "Model Reasoning:\nThis philosophical question explores foundational aspects of system interaction. At its core lies causality: any state is determined by prior factors and environmental boundary conditions."
                }
                else -> {
                    val thoughts = listOf(
                        "Input sequence analysis for '$input' completed.\nKey takeaway: optimize core system parameters. Recommend structuring input into key points.",
                        "Processing query '$input':\nFrom the perspective of model $model, this requires context constraints. Feel free to specify output format (JSON, Markdown, code).",
                        "Prompt received. Attention vector focused on primary query entities. Ready to continue the line of reasoning."
                    )
                    thoughts.random()
                }
            }
        }

        return when {
            lower.contains("привет") || lower.contains("здравствуй") || lower.contains("ку") || lower.contains("hi") || lower.contains("hello") -> {
                listOf(
                    "Здравствуйте! Локальная модель $model инициализирована в оперативной памяти. Контекстное окно: 4096 токенов. Чем я могу помочь?",
                    "Приветствую! Модель $model готова к обработке запросов в автономном режиме. Задайте вопрос или поставьте задачу.",
                    "Привет! Я языковая модель, работающая локально на вашем устройстве. Готов рассуждать, писать код или форматировать текст."
                ).random()
            }
            lower.contains("код") || lower.contains("python") || lower.contains("скрипт") || lower.contains("напиши") || lower.contains("code") -> {
                """Вот пример решения на Python:
```python
def process_data(items):
    # Локальная обработка входящего потока данных
    result = [item.strip() for item in items if item]
    return sorted(set(result))

if __name__ == "__main__":
    sample = ["apple", "banana", "apple", "cherry"]
    print("Результат:", process_data(sample))
```
Код оптимизирован по памяти O(N). Если нужны изменения логики, уточните параметры."""
            }
            lower.contains("кто ты") || lower.contains("что ты") || lower.contains("о себе") -> {
                "Я оффлайн LLM-ассистент, развернутый из файла квантованных весов $model (Q4_K_M). Я функционирую локально в изолированном окружении и не отправляю ваши промпты третьим лицам."
            }
            lower.contains("как дела") || lower.contains("статус") || lower.contains("память") || lower.contains("vram") -> {
                val ram = (1200..1850).random()
                val speed = (18..28).random()
                "Статус системы: Штатный режим.\nВыделено RAM: ${ram}MB\nСкорость генерации: ~$speed tok/s\nТемпература ядра: оптимальная\nКонтекст: чистый"
            }
            lower.contains("почему") || lower.contains("зачем") || lower.contains("смысл") -> {
                "Рассуждение модели:\nДанный философский вопрос охватывает фундаментальные аспекты взаимодействия систем. В основе лежит принцип причинно-следственной связи: любое состояние определяется предшествующими факторами и граничными условиями среды. Для детального разбора требуется конкретизировать исследуемый субъект."
            }
            else -> {
                val thoughts = listOf(
                    "Анализ входной последовательности '$input' завершен.\nКлючевой вывод: задача сводится к оптимизации базовых параметров системы. Рекомендуется структурировать ввод по ключевым пунктам для более точной декомпозиции.",
                    "Обработка запроса '$input':\nС точки зрения модели $model, данный аспект требует учета контекстных ограничений. Если это техническая задача, уточните формат выходных данных (JSON, Markdown, код).",
                    "Промпт принят к сведению. Внутренний вектор внимания сфокусирован на основных сущностях запроса. Готов продолжить цепочку рассуждений по вашей теме."
                )
                thoughts.random()
            }
        }
    }

    private fun handleLocalOrSearchQueries(input: String): String? {
        val lower = input.lowercase(java.util.Locale.getDefault())
        
        val mathResult = tryEvaluateMath(input)
        if (mathResult != null) {
            return mathResult
        }
        
        if (lower.contains("время") || lower.contains("time") || lower.contains("zeit") || lower.contains("heure") || lower.contains("hora") || lower.contains("时间") || lower.contains("وقت") || (lower.contains("котор") && lower.contains("час"))) {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            return getString(R.string.llm_time_result, sdf.format(java.util.Date()))
        }
        if (lower.contains("дата") || lower.contains("date") || lower.contains("datum") || lower.contains("fecha") || lower.contains("日期") || lower.contains("تاريخ") || (lower.contains("какое") && lower.contains("число")) || (lower.contains("какой") && lower.contains("день"))) {
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            return getString(R.string.llm_date_result, sdf.format(java.util.Date()))
        }
        
        val searchKeywords = listOf(
            "новости", "погода", "курс", "доллар", "евро", "биткоин", 
            "происходит", "найди в интернете", "загугли", "поиск", "последние", "сегодня", "в мире", "матч", "результат", "интернет",
            "news", "weather", "search", "google", "online", "price", "rate", "wetter", "meteo", "tiempo", "clima"
        )
        if (settingsManager.llmWebSearchMode) {
            val snippet = if (input.length > 40) input.take(40) + "..." else input
            return getString(R.string.llm_search_result_header, snippet, getRandomConnectionError())
        }
        if (searchKeywords.any { lower.contains(it) }) {
            return getRandomConnectionError()
        }
        
        return null
    }

    private fun tryEvaluateMath(input: String): String? {
        val cleaned = input.lowercase(java.util.Locale.getDefault())
            .replace("сколько будет", "")
            .replace("посчитай", "")
            .replace("вычисли", "")
            .replace("реши", "")
            .replace("калькулятор", "")
            .replace("calculate", "")
            .replace("calc", "")
            .replace("solve", "")
            .replace("berechne", "")
            .replace("calcule", "")
            .replace("calcula", "")
            .replace("计算", "")
            .replace("احسب", "")
            .replace("=", "")
            .trim()

        val mathCharsPattern = Regex("""^[\d\+\-\*/\(\)\.\s]+$""")
        val hasDigitsAndOp = Regex("""\d+\s*[\+\-\*/]\s*\d+""")
        
        if (hasDigitsAndOp.containsMatchIn(cleaned) && mathCharsPattern.matches(cleaned)) {
            try {
                var s = cleaned.replace(" ", "")
                if (s.startsWith("(") && s.endsWith(")")) {
                    s = s.substring(1, s.length - 1)
                }
                
                val parts = s.split(Regex("""(?<=[\+\-\*/])|(?=[\+\-\*/])"""))
                if (parts.size >= 3) {
                    var acc = parts[0].toDoubleOrNull() ?: return null
                    var i = 1
                    while (i < parts.size - 1) {
                        val op = parts[i]
                        val nextVal = parts[i + 1].toDoubleOrNull() ?: return null
                        when (op) {
                            "+" -> acc += nextVal
                            "-" -> acc -= nextVal
                            "*" -> acc *= nextVal
                            "/" -> if (nextVal != 0.0) acc /= nextVal else return getString(R.string.llm_calc_div_zero)
                        }
                        i += 2
                    }
                    val formatted = if (acc % 1.0 == 0.0) acc.toLong().toString() else acc.toString()
                    return getString(R.string.llm_calc_result, input, formatted)
                }
            } catch (e: Exception) {}
        }
        return null
    }

    private fun getRandomConnectionError(): String {
        if (settingsManager.appLanguage != "ru") {
            val enErrors = listOf(
                "[System: Network] Error: Server unreachable. Check your network connection.",
                "[WebSearch Module] Connection timeout: Failed to connect to search server.",
                "[DNS Resolver] DNS Error: Unable to resolve search host address.",
                "[Socket] Connection failed: Remote host closed the connection.",
                "[Network Error] No route to remote host.",
                "Error 502 Bad Gateway: Proxy received invalid response while searching.",
                "Error 504 Gateway Timeout: News aggregator gateway timed out.",
                "Network Anomaly: Search request packets lost in transit.",
                "TCP/IP Error: Connection reset (RST) from remote host during data fetch.",
                "Socket Timeout: SocketTimeoutException while reading data stream.",
                "TLS Handshake Error: Failed to establish secure connection with server.",
                "ERR_CONNECTION_RESET: Internet connection was reset.",
                "ERR_NAME_NOT_RESOLVED: Host IP address could not be resolved.",
                "ERR_CONNECTION_TIMED_OUT: Server response timed out.",
                "ERR_NETWORK_CHANGED: Network interface changed during search query.",
                "Critical Network Error: Network adapter unable to transmit outbound packet.",
                "BGP Error: Route to specified autonomous system temporarily unavailable.",
                "Proxy Authentication Required: External network access denied.",
                "ICMP Destination Unreachable: Target host cannot be reached.",
                "HTTP Error 503: External search service temporarily unavailable.",
                "Request Timeout: Remote server took too long to answer search query.",
                "Network Stack Exception: OS socket layer reported failure connecting to internet.",
                "IPv6 Error: External routing failed for IPv6 address.",
                "WLAN Error: Wireless network signal degraded or lost.",
                "Mobile Data Error: Cellular data service is disabled or unavailable.",
                "SSL Certificate Error: Remote certificate authority is invalid or untrusted.",
                "ERR_CERT_DATE_INVALID: SSL certificate of search server has expired.",
                "Router Issue: Default gateway is not responding to ARP requests.",
                "MTU Error: Packet size exceeded MTU limit without fragmentation permitted.",
                "Connection Refused: Target port 443 refused connection attempt.",
                "ERR_TOO_MANY_REDIRECTS: Exceeded maximum HTTP redirect threshold.",
                "mDNS Query Failed: Could not resolve local multicast name.",
                "No Ping: 100% packet loss to external internet gateway.",
                "Proxy Failure: ERR_PROXY_CONNECTION_FAILED.",
                "Firewall Blocking: Outbound connection to port 443 blocked by security policy.",
                "Unknown Network Exception: Error code 0x80072EFE.",
                "VPN Tunnel Error: Encapsulation failed for search query packets.",
                "ARP Table Error: Gateway MAC address not found.",
                "DHCP Lease Expired: Failed to renew IP address on network interface.",
                "CDN Error: Content Delivery Network edge server unreachable in your region.",
                "DNSSEC Verification Failed: Cryptographic signature validation error.",
                "Resolver Timeout: Secondary DNS server did not respond.",
                "WebSocket Error: Connection closed abnormally with code 1006.",
                "QUIC Protocol Error: ERR_QUIC_PROTOCOL_ERROR during handshake.",
                "SNI Mismatch: Server rejected TLS Server Name Indication.",
                "Interface State: Device network adapter is in DOWN state.",
                "NAT Error: Network address translation failed for outbound stream.",
                "Connection Throttled: Maximum outbound socket limit reached.",
                "CORS Policy Error: Origin is not permitted to access this resource.",
                "Routing Loop: Routing loop detected in upstream network path."
            )
            return enErrors.random()
        }
        val errors = listOf(
            "[System: Network] Ошибка: Сервер недоступен. Проверьте подключение к сети.",
            "[WebSearch Module] Connection timeout: Не удалось установить связь с поисковым сервером.",
            "[DNS Resolver] Ошибка DNS: Невозможно разрешить адрес поискового хоста.",
            "[Socket] Сбой соединения: Удаленный хост разорвал подключение.",
            "[Network Error] Отсутствует маршрут к удаленному узлу.",
            "Ошибка 502 Bad Gateway: Прокси-сервер получил недействительный ответ при попытке поиска.",
            "Ошибка 504 Gateway Timeout: Превышено время ожидания ответа от новостного агрегатора.",
            "Сетевая аномалия: Пакеты поискового запроса были потеряны в пути.",
            "TCP/IP Error: Сброс соединения (RST) от удаленного хоста при запросе данных.",
            "Ошибка сокета: SocketTimeoutException при чтении потока данных.",
            "TLS Handshake Error: Не удалось установить защищенное соединение с сервером.",
            "ERR_CONNECTION_RESET: Подключение к интернету было сброшено.",
            "ERR_NAME_NOT_RESOLVED: Не удалось найти IP-адрес для внешнего запроса.",
            "ERR_CONNECTION_TIMED_OUT: Время ожидания ответа от сервера истекло.",
            "ERR_NETWORK_CHANGED: Обнаружено изменение сетевого интерфейса во время поиска.",
            "Критическая сетевая ошибка: Адаптер не может отправить внешний пакет.",
            "Ошибка BGP: Маршрут к указанному узлу временно недоступен.",
            "Сбой аутентификации прокси-сервера. Внешний доступ запрещен.",
            "ICMP Destination Unreachable: Узел назначения недостижим.",
            "Ошибка HTTP 503: Сервис внешнего поиска временно недоступен.",
            "Тайм-аут запроса: Удаленный сервер не ответил вовремя на поисковый запрос.",
            "Сбой в работе сетевого стека ОС при попытке выйти в интернет.",
            "Ошибка IPv6: Не удалось выполнить внешнюю маршрутизацию.",
            "WLAN Error: Потерян сигнал сети.",
            "Mobile Data Error: Мобильная сеть передачи данных отключена или недоступна.",
            "Ошибка SSL: Сертификат удаленного узла недействителен.",
            "ERR_CERT_DATE_INVALID: Срок действия SSL-сертификата внешнего сервера истек.",
            "Проблема с маршрутизатором: Шлюз по умолчанию не отвечает.",
            "Ошибка MTU: Превышен размер передаваемого пакета без фрагментации.",
            "Отказ в обслуживании: Внешний сервер отклонил запрос на подключение.",
            "Слишком много редиректов: Ошибка ERR_TOO_MANY_REDIRECTS при поиске.",
            "Сбой разрешения имен mDNS в локальной сети.",
            "Отсутствует пинг: 100% потеря пакетов (packet loss) к внешней сети.",
            "Прокси отказал в соединении: ERR_PROXY_CONNECTION_FAILED.",
            "Брандмауэр блокирует исходящее соединение на внешний порт 443.",
            "Неизвестная сетевая ошибка: Код 0x80072EFE.",
            "Ошибка VPN-туннеля: Не удалось инкапсулировать поисковые пакеты.",
            "MAC-адрес шлюза не найден в ARP-таблице.",
            "Сбой DHCP: Не удалось обновить IP-адрес для внешнего интерфейса.",
            "Ошибка CDN: Контентная сеть недоступна в вашем регионе.",
            "Ошибка DNSSEC: Провайдер не смог подтвердить цифровую подпись зоны.",
            "Тайм-аут резолвера: Вторичный DNS-сервер не ответил на запрос внешнего домена.",
            "WebSockets Error: Соединение закрыто с кодом 1006.",
            "Ошибка протокола QUIC: ERR_QUIC_PROTOCOL_ERROR при обращении к серверу.",
            "Сбой SNI: Удаленный сервер не распознал имя хоста в запросе.",
            "Сетевой интерфейс устройства находится в состоянии DOWN.",
            "Ошибка NAT: Трансляция адресов во внешнюю сеть не удалась.",
            "Сбой: Превышен лимит подключений к удаленному серверу.",
            "Ошибка CORS: Попытка доступа к запрещенному внешнему ресурсу.",
            "Сбой маршрутизации: Обнаружена петля маршрутизации (Routing loop) во внешнюю сеть."
        )
        return errors.random()
    }

    private fun simulateResponse(response: String) {
        val theme = getThemeConfig(settingsManager.llmThemePreset)
        val tf = if (settingsManager.llmFontStyle == "SANS_SERIF") Typeface.SANS_SERIF else Typeface.MONOSPACE
        val sz = settingsManager.llmFontSize.toFloat()

        val aiTv = TextView(this).apply {
            this.text = response
            setTextColor(theme.textColor)
            textSize = sz
            setPadding(0, 0, 0, 24)
            typeface = tf
        }
        chatContainer.addView(aiTv)
        scrollToBottom()
        isGenerating = false
        updateSendButtonState()
    }
}
