package com.vibe.browser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    private val dohOptions = listOf(
        Pair("Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query"),
        Pair("Quad9", "https://dns.quad9.net/dns-query"),
        Pair("AdGuard", "https://dns.adguard.com/dns-query"),
        Pair("Custom...", "CUSTOM")
    )

    private val uaOptions = listOf(
        Pair("Default", ""),
        Pair("Windows 11 PC (Chrome)", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"),
        Pair("Windows 10 PC (Firefox)", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:122.0) Gecko/20100101 Firefox/122.0"),
        Pair("MacBook Pro (Safari)", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"),
        Pair("MacBook Air (Chrome)", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"),
        Pair("iPhone 15 (Safari)", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"),
        Pair("iPad Pro (Safari)", "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"),
        Pair("Android Samsung (Chrome)", "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"),
        Pair("Android Pixel (Firefox)", "Mozilla/5.0 (Android 14; Mobile; rv:122.0) Gecko/122.0 Firefox/122.0")
    )
    
    private val resOptions = listOf(
        Pair("16:9 (1280x720 - HD)", "1280x720"),
        Pair("16:9 (1366x768 - WXGA)", "1366x768"),
        Pair("16:9 (1600x900 - HD+)", "1600x900"),
        Pair("16:9 (1920x1080 - FHD)", "1920x1080"),
        Pair("16:9 (2560x1440 - QHD)", "2560x1440"),
        Pair("16:9 (3840x2160 - 4K)", "3840x2160"),
        
        Pair("16:10 (1280x800)", "1280x800"),
        Pair("16:10 (1440x900)", "1440x900"),
        Pair("16:10 (1680x1050)", "1680x1050"),
        Pair("16:10 (1920x1200)", "1920x1200"),
        Pair("16:10 (2560x1600)", "2560x1600"),
        
        Pair("4:3 (800x600)", "800x600"),
        Pair("4:3 (1024x768)", "1024x768"),
        Pair("4:3 (1280x960)", "1280x960"),
        Pair("4:3 (1600x1200)", "1600x1200"),
        Pair("4:3 (2048x1536)", "2048x1536"),
        
        Pair("21:9 (2560x1080 - Ultrawide)", "2560x1080"),
        Pair("21:9 (3440x1440 - Ultrawide)", "3440x1440"),

        Pair("Mobile 16:9 (360x640)", "360x640"),
        Pair("Mobile 16:9 (414x736 - iPhone Plus)", "414x736"),
        Pair("Mobile 18:9 (360x720)", "360x720"),
        Pair("Mobile 19.5:9 (390x844 - iPhone)", "390x844"),
        Pair("Mobile 19.5:9 (428x926 - iPhone Max)", "428x926"),
        Pair("Mobile 20:9 (412x915 - Android)", "412x915")
    )
    
    private val langOptions = LanguageHelper.supportedLanguages

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = LanguageHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsManager = SettingsManager(this)

        val switchScreenshots = findViewById<Switch>(R.id.switchScreenshots)
        val switchWebRtc = findViewById<Switch>(R.id.switchWebRtc)
        val switchRfp = findViewById<Switch>(R.id.switchRfp)
        val switchReferrer = findViewById<Switch>(R.id.switchReferrer)
        val switchSlow3g = findViewById<Switch>(R.id.switchSlow3g)
        val editSlow3gDelay = findViewById<EditText>(R.id.editSlow3gDelay)
        
        val switchDoh = findViewById<Switch>(R.id.switchDoh)
        val spinnerDoh = findViewById<Spinner>(R.id.spinnerDoh)
        val editCustomDoh = findViewById<EditText>(R.id.editCustomDoh)
        val spinnerUa = findViewById<Spinner>(R.id.spinnerUa)
        val spinnerResolution = findViewById<Spinner>(R.id.spinnerResolution)
        val spinnerLang = findViewById<Spinner>(R.id.spinnerLang)
        
        val btnOpenLlmSettings = findViewById<Button>(R.id.btnOpenLlmSettings)
        btnOpenLlmSettings.setOnClickListener {
            val intent = Intent(this, LlmSettingsActivity::class.java)
            intent.putExtra("FROM_BROWSER_SETTINGS", true)
            startActivity(intent)
        }
        
        val editUserscript = findViewById<EditText>(R.id.editUserscript)

        val editRealKey = findViewById<EditText>(R.id.editRealKey)
        val editFakeKey = findViewById<EditText>(R.id.editFakeKey)
        val btnSave = findViewById<Button>(R.id.btnSave)

        listOf(editSlow3gDelay, editCustomDoh, editUserscript, editRealKey, editFakeKey).forEach {
            IncognitoKeyboardHelper.apply(it)
        }

        switchScreenshots.isChecked = settingsManager.blockScreenshots
        switchWebRtc.isChecked = settingsManager.blockWebRtc
        switchRfp.isChecked = settingsManager.antiFingerprinting
        switchReferrer.isChecked = settingsManager.stripReferrer
        switchSlow3g.isChecked = settingsManager.slow3g
        editSlow3gDelay.setText(settingsManager.slow3gDelay.toString())
        switchDoh.isChecked = settingsManager.dohEnabled
        
        
        editUserscript.setText(settingsManager.userscript)

        switchSlow3g.setOnCheckedChangeListener { _, isChecked ->
            editSlow3gDelay.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        editSlow3gDelay.visibility = if (settingsManager.slow3g) View.VISIBLE else View.GONE

        // Setup Spinners
        val dohAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dohOptions.map { it.first })
        spinnerDoh.adapter = dohAdapter
        val currentDoh = settingsManager.dohProvider
        val isCustomDoh = currentDoh.isNotEmpty() && dohOptions.none { it.second == currentDoh }
        if (isCustomDoh) {
            spinnerDoh.setSelection(dohOptions.size - 1)
            editCustomDoh.visibility = if (settingsManager.dohEnabled) View.VISIBLE else View.GONE
            editCustomDoh.setText(currentDoh)
        } else {
            spinnerDoh.setSelection(dohOptions.indexOfFirst { it.second == currentDoh }.takeIf { it >= 0 } ?: 0)
        }
        spinnerDoh.visibility = if (settingsManager.dohEnabled) View.VISIBLE else View.GONE

        switchDoh.setOnCheckedChangeListener { _, isChecked ->
            spinnerDoh.visibility = if (isChecked) View.VISIBLE else View.GONE
            val selected = dohOptions.getOrNull(spinnerDoh.selectedItemPosition)?.second
            editCustomDoh.visibility = if (isChecked && selected == "CUSTOM") View.VISIBLE else View.GONE
        }

        spinnerDoh.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                editCustomDoh.visibility = if (switchDoh.isChecked && dohOptions[position].second == "CUSTOM") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val uaAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, uaOptions.map { it.first })
        spinnerUa.adapter = uaAdapter
        val currentUa = settingsManager.customUserAgent
        spinnerUa.setSelection(uaOptions.indexOfFirst { it.second == currentUa }.takeIf { it >= 0 } ?: 0)
        
        val resAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resOptions.map { it.first })
        spinnerResolution.adapter = resAdapter
        val currentRes = settingsManager.emulatedResolution
        spinnerResolution.setSelection(resOptions.indexOfFirst { it.second == currentRes }.takeIf { it >= 0 } ?: 0)
        
        val switchEmulateScreen = findViewById<Switch>(R.id.switchEmulateScreen)
        switchEmulateScreen.isChecked = settingsManager.emulateScreen
        spinnerResolution.visibility = if (settingsManager.emulateScreen) View.VISIBLE else View.GONE
        
        switchEmulateScreen.setOnCheckedChangeListener { _, isChecked ->
            spinnerResolution.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langOptions.map { it.first })
        spinnerLang.adapter = langAdapter
        spinnerLang.setSelection(langOptions.indexOfFirst { it.second == settingsManager.appLanguage }.takeIf { it >= 0 } ?: 0)

        btnSave.setOnClickListener {
            settingsManager.blockScreenshots = switchScreenshots.isChecked
            settingsManager.blockWebRtc = switchWebRtc.isChecked
            settingsManager.antiFingerprinting = switchRfp.isChecked
            settingsManager.stripReferrer = switchReferrer.isChecked
            settingsManager.dohEnabled = switchDoh.isChecked
            settingsManager.emulateScreen = switchEmulateScreen.isChecked
            settingsManager.updateGeckoConfig(this)
            
            settingsManager.slow3g = switchSlow3g.isChecked
            settingsManager.slow3gDelay = editSlow3gDelay.text.toString().toIntOrNull() ?: 1200
            MainActivity.updateThrottlingConfig(this)
            
            settingsManager.userscript = editUserscript.text.toString().trim()
            
            val selectedDoh = dohOptions[spinnerDoh.selectedItemPosition].second
            settingsManager.dohProvider = if (selectedDoh == "CUSTOM") editCustomDoh.text.toString() else selectedDoh
            
            val selectedUa = uaOptions[spinnerUa.selectedItemPosition].second
            settingsManager.customUserAgent = selectedUa
            
            val selectedRes = resOptions[spinnerResolution.selectedItemPosition].second
            settingsManager.emulatedResolution = selectedRes
            
            val selectedLang = langOptions[spinnerLang.selectedItemPosition].second
            settingsManager.appLanguage = selectedLang
            
            val rKey = editRealKey.text.toString()
            val fKey = editFakeKey.text.toString()
            if (rKey.isNotEmpty()) settingsManager.realPassword = rKey
            if (fKey.isNotEmpty()) settingsManager.fakePassword = fKey

            Toast.makeText(this, getString(R.string.settings_saved_restarting), Toast.LENGTH_SHORT).show()
            
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
    }
}


