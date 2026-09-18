package com.vibe.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class LlmSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private val PICK_WALLPAPER = 2001

    private val contextWindowOptions = listOf(2048, 4096, 8192, 16384, 32768)
    private val themeOptions by lazy {
        listOf(
            Pair(getString(R.string.llm_theme_matrix), "MATRIX"),
            Pair(getString(R.string.llm_theme_cyberpunk), "CYBERPUNK"),
            Pair(getString(R.string.llm_theme_amoled), "AMOLED"),
            Pair(getString(R.string.llm_theme_amber), "AMBER"),
            Pair(getString(R.string.llm_theme_ocean), "OCEAN")
        )
    }
    private val fontStyleOptions by lazy {
        listOf(
            Pair(getString(R.string.llm_font_monospace), "MONOSPACE"),
            Pair(getString(R.string.llm_font_sans_serif), "SANS_SERIF")
        )
    }
    private val fontSizeOptions by lazy {
        listOf(
            Pair(getString(R.string.llm_font_size_small), 12),
            Pair(getString(R.string.llm_font_size_standard), 14),
            Pair(getString(R.string.llm_font_size_large), 16),
            Pair(getString(R.string.llm_font_size_xlarge), 18)
        )
    }

    private var selectedWallpaperUri: String = ""

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = LanguageHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_settings)

        settingsManager = SettingsManager(this)

        val btnBack = findViewById<Button>(R.id.btnBack)
        val switchUseLocalLlm = findViewById<Switch>(R.id.switchUseLocalLlm)
        val spinnerContextWindow = findViewById<Spinner>(R.id.spinnerContextWindow)
        val tvTemperatureLabel = findViewById<TextView>(R.id.tvTemperatureLabel)
        val seekBarTemperature = findViewById<SeekBar>(R.id.seekBarTemperature)
        val editSystemPrompt = findViewById<EditText>(R.id.editSystemPrompt)

        val editLlmBaseUrl = findViewById<EditText>(R.id.editLlmBaseUrl)
        val editLlmModel = findViewById<EditText>(R.id.editLlmModel)
        val editLlmKey = findViewById<EditText>(R.id.editLlmKey)

        listOf(editSystemPrompt, editLlmBaseUrl, editLlmModel, editLlmKey).forEach {
            IncognitoKeyboardHelper.apply(it)
        }

        val spinnerThemePreset = findViewById<Spinner>(R.id.spinnerThemePreset)
        val tvWallpaperStatus = findViewById<TextView>(R.id.tvWallpaperStatus)
        val btnPickWallpaper = findViewById<Button>(R.id.btnPickWallpaper)
        val btnClearWallpaper = findViewById<Button>(R.id.btnClearWallpaper)
        val tvDimmingLabel = findViewById<TextView>(R.id.tvDimmingLabel)
        val seekBarDimming = findViewById<SeekBar>(R.id.seekBarDimming)
        val spinnerFontStyle = findViewById<Spinner>(R.id.spinnerFontStyle)
        val spinnerFontSize = findViewById<Spinner>(R.id.spinnerFontSize)
        val spinnerLlmLang = findViewById<Spinner>(R.id.spinnerLlmLang)

        val btnClearChat = findViewById<Button>(R.id.btnClearChat)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Setup Language Spinner
        val langOptions = LanguageHelper.supportedLanguages
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langOptions.map { it.first })
        spinnerLlmLang.adapter = langAdapter
        val currentLang = settingsManager.appLanguage
        spinnerLlmLang.setSelection(langOptions.indexOfFirst { it.second == currentLang }.takeIf { it >= 0 } ?: 0)

        // Back button
        btnBack.setOnClickListener { finish() }

        // Load values
        switchUseLocalLlm.isChecked = settingsManager.useLocalLlm

        val prefs = getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
        val currentCtx = prefs.getInt("saved_ctx", 4096)
        val ctxAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contextWindowOptions.map { "${it / 1024}k ($it)" })
        spinnerContextWindow.adapter = ctxAdapter
        spinnerContextWindow.setSelection(contextWindowOptions.indexOf(currentCtx).takeIf { it >= 0 } ?: 1)

        // Temperature (0.1 to 1.5, max 14 => step 0.1)
        val currentTemp = settingsManager.llmTemperature
        val tempProgress = ((currentTemp - 0.1f) * 10f).toInt().coerceIn(0, 14)
        seekBarTemperature.progress = tempProgress
        tvTemperatureLabel.text = getString(R.string.llm_settings_temperature, currentTemp)

        seekBarTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val temp = 0.1f + (progress * 0.1f)
                tvTemperatureLabel.text = getString(R.string.llm_settings_temperature, temp)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        editSystemPrompt.setText(settingsManager.llmSystemPrompt)
        editLlmBaseUrl.setText(settingsManager.llmBaseUrl)
        editLlmModel.setText(settingsManager.llmModel)
        editLlmKey.setText(settingsManager.llmApiKey)

        // Themes
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themeOptions.map { it.first })
        spinnerThemePreset.adapter = themeAdapter
        val currentTheme = settingsManager.llmThemePreset
        spinnerThemePreset.setSelection(themeOptions.indexOfFirst { it.second == currentTheme }.takeIf { it >= 0 } ?: 0)

        // Wallpaper
        selectedWallpaperUri = settingsManager.llmCustomWallpaperUri
        updateWallpaperStatus(tvWallpaperStatus)

        btnPickWallpaper.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, PICK_WALLPAPER)
        }

        btnClearWallpaper.setOnClickListener {
            selectedWallpaperUri = ""
            updateWallpaperStatus(tvWallpaperStatus)
        }

        // Dimming
        val currentDimming = settingsManager.llmWallpaperDimming
        seekBarDimming.progress = currentDimming.coerceIn(0, 90)
        tvDimmingLabel.text = getString(R.string.llm_settings_dimming, currentDimming)

        seekBarDimming.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDimmingLabel.text = getString(R.string.llm_settings_dimming, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Font style
        val fontStyleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontStyleOptions.map { it.first })
        spinnerFontStyle.adapter = fontStyleAdapter
        val currentFontStyle = settingsManager.llmFontStyle
        spinnerFontStyle.setSelection(fontStyleOptions.indexOfFirst { it.second == currentFontStyle }.takeIf { it >= 0 } ?: 0)

        // Font size
        val fontSizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontSizeOptions.map { it.first })
        spinnerFontSize.adapter = fontSizeAdapter
        val currentFontSize = settingsManager.llmFontSize
        spinnerFontSize.setSelection(fontSizeOptions.indexOfFirst { it.second == currentFontSize }.takeIf { it >= 0 } ?: 1)

        // Clear chat
        btnClearChat.setOnClickListener {
            prefs.edit().remove("chat_history").putBoolean("request_clear_chat", true).apply()
            Toast.makeText(this, getString(R.string.llm_settings_chat_cleared), Toast.LENGTH_SHORT).show()
        }

        // Save
        btnSave.setOnClickListener {
            settingsManager.useLocalLlm = switchUseLocalLlm.isChecked
            val chosenCtx = contextWindowOptions[spinnerContextWindow.selectedItemPosition]
            prefs.edit().putInt("saved_ctx", chosenCtx).apply()

            val temp = 0.1f + (seekBarTemperature.progress * 0.1f)
            settingsManager.llmTemperature = temp
            settingsManager.llmSystemPrompt = editSystemPrompt.text.toString().trim()

            settingsManager.llmBaseUrl = editLlmBaseUrl.text.toString().trim()
            settingsManager.llmModel = editLlmModel.text.toString().trim()
            settingsManager.llmApiKey = editLlmKey.text.toString().trim()

            val chosenTheme = themeOptions[spinnerThemePreset.selectedItemPosition].second
            settingsManager.llmThemePreset = chosenTheme

            settingsManager.llmCustomWallpaperUri = selectedWallpaperUri
            settingsManager.llmWallpaperDimming = seekBarDimming.progress

            val chosenFontStyle = fontStyleOptions[spinnerFontStyle.selectedItemPosition].second
            settingsManager.llmFontStyle = chosenFontStyle

            val chosenFontSize = fontSizeOptions[spinnerFontSize.selectedItemPosition].second
            settingsManager.llmFontSize = chosenFontSize

            val selectedLang = langOptions[spinnerLlmLang.selectedItemPosition].second
            val langChanged = (selectedLang != currentLang)
            settingsManager.appLanguage = selectedLang

            Toast.makeText(this, getString(R.string.settings_save) + ": OK", Toast.LENGTH_SHORT).show()

            if (langChanged) {
                val isFromBrowser = intent.getBooleanExtra("FROM_BROWSER_SETTINGS", false)
                if (isFromBrowser) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                } else {
                    val intent = Intent(this, FakeAiActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                }
            }
            finish()
        }
    }

    private fun updateWallpaperStatus(tv: TextView) {
        if (selectedWallpaperUri.isNotEmpty()) {
            tv.text = getString(R.string.llm_settings_wallpaper_selected)
            tv.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            tv.text = getString(R.string.llm_settings_wallpaper_none)
            tv.setTextColor(android.graphics.Color.parseColor("#888888"))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_WALLPAPER && resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedWallpaperUri = uri.toString()
            findViewById<TextView>(R.id.tvWallpaperStatus)?.let { updateWallpaperStatus(it) }
        }
    }
}
