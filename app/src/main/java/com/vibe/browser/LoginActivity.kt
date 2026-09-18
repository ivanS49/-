package com.vibe.browser

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private var failedAttempts = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = LanguageHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_login)

        settingsManager = SettingsManager(this)

        val btnLoginLang = findViewById<Button>(R.id.btnLoginLang)
        val currentLangCode = LanguageHelper.getSavedLanguage(this)
        val currentLangName = LanguageHelper.supportedLanguages.firstOrNull { it.second == currentLangCode }?.first ?: currentLangCode.uppercase()
        btnLoginLang.text = "🌐 $currentLangName"
        btnLoginLang.setOnClickListener {
            val options = LanguageHelper.supportedLanguages.map { it.first }.toTypedArray()
            val currentIndex = LanguageHelper.supportedLanguages.indexOfFirst { it.second == currentLangCode }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_lang)
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    val chosen = LanguageHelper.supportedLanguages[which].second
                    LanguageHelper.saveLanguage(this, chosen)
                    settingsManager.appLanguage = chosen
                    dialog.dismiss()
                    recreate()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        val setupLayout = findViewById<LinearLayout>(R.id.setupLayout)
        val loginLayout = findViewById<LinearLayout>(R.id.loginLayout)
        val subtitleText = findViewById<TextView>(R.id.subtitleText)

        if (settingsManager.realPassword.isEmpty() || settingsManager.fakePassword.isEmpty()) {
            setupLayout.visibility = View.VISIBLE
            loginLayout.visibility = View.GONE
            
            findViewById<Button>(R.id.btnSaveKeys).setOnClickListener {
                val real = findViewById<EditText>(R.id.setupRealKey).text.toString()
                val fake = findViewById<EditText>(R.id.setupFakeKey).text.toString()
                
                if (real.isNotEmpty() && fake.isNotEmpty() && real != fake) {
                    settingsManager.realPassword = real
                    settingsManager.fakePassword = fake
                    Toast.makeText(this, getString(R.string.login_keys_saved), Toast.LENGTH_SHORT).show()
                    
                    setupLayout.visibility = View.GONE
                    loginLayout.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this, getString(R.string.login_keys_invalid_pair), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            setupLayout.visibility = View.GONE
            loginLayout.visibility = View.VISIBLE
        }

        findViewById<EditText>(R.id.setupRealKey)?.let { IncognitoKeyboardHelper.apply(it) }
        findViewById<EditText>(R.id.setupFakeKey)?.let { IncognitoKeyboardHelper.apply(it) }

        val inputKey = findViewById<EditText>(R.id.inputKey)
        IncognitoKeyboardHelper.apply(inputKey)
        val btnEnter = findViewById<Button>(R.id.btnEnter)

        val attemptLogin = {
            val key = inputKey.text.toString()
            inputKey.setText("")
            if (key.isNotEmpty() && key == settingsManager.realPassword) {
                failedAttempts = 0
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else if (key.isNotEmpty() && key == settingsManager.fakePassword) {
                failedAttempts = 0
                startActivity(Intent(this, FakeAiActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, getString(R.string.login_invalid_key), Toast.LENGTH_SHORT).show()
            }
        }

        btnEnter.setOnClickListener { attemptLogin() }
        
        inputKey.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }
}
