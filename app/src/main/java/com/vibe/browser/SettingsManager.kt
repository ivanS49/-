package com.vibe.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secret_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val encryptedLang = sharedPreferences.getString("app_language", null)
        if (!encryptedLang.isNullOrEmpty() && LanguageHelper.getSavedLanguage(context) != encryptedLang) {
            LanguageHelper.saveLanguage(context, encryptedLang)
        }
    }

    var blockScreenshots: Boolean
        get() = sharedPreferences.getBoolean("block_screenshots", true)
        set(value) = sharedPreferences.edit().putBoolean("block_screenshots", value).apply()

    var emulateScreen: Boolean
        get() = sharedPreferences.getBoolean("emulateScreen", true)
        set(value) = sharedPreferences.edit().putBoolean("emulateScreen", value).apply()

    var emulatedResolution: String
        get() = sharedPreferences.getString("emulated_resolution", "1920x1080") ?: "1920x1080"
        set(value) = sharedPreferences.edit().putString("emulated_resolution", value).apply()

    var customUserAgent: String
        get() = sharedPreferences.getString("custom_user_agent", "") ?: ""
        set(value) = sharedPreferences.edit().putString("custom_user_agent", value).apply()
        
    var appLanguage: String
        get() = LanguageHelper.getSavedLanguage(context)
        set(value) {
            LanguageHelper.saveLanguage(context, value)
            try {
                sharedPreferences.edit().putString("app_language", value).apply()
            } catch (e: Exception) {}
        }

    var realPassword: String
        get() = sharedPreferences.getString("real_password", "") ?: ""
        set(value) = sharedPreferences.edit().putString("real_password", value).apply()

    var fakePassword: String
        get() = sharedPreferences.getString("fake_password", "") ?: ""
        set(value) = sharedPreferences.edit().putString("fake_password", value).apply()

    var dohEnabled: Boolean
        get() = sharedPreferences.getBoolean("doh_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("doh_enabled", value).apply()

    // DoH Provider. Empty means default, otherwise URL
    var dohProvider: String
        get() = sharedPreferences.getString("doh_provider", "https://cloudflare-dns.com/dns-query") ?: "https://cloudflare-dns.com/dns-query"
        set(value) = sharedPreferences.edit().putString("doh_provider", value).apply()
        
    var disableJs: Boolean
        get() = sharedPreferences.getBoolean("disable_js", false)
        set(value) = sharedPreferences.edit().putBoolean("disable_js", value).apply()

    var blockWebRtc: Boolean
        get() = sharedPreferences.getBoolean("block_webrtc", true)
        set(value) = sharedPreferences.edit().putBoolean("block_webrtc", value).apply()

    var antiFingerprinting: Boolean
        get() = sharedPreferences.getBoolean("anti_fingerprinting", true)
        set(value) = sharedPreferences.edit().putBoolean("anti_fingerprinting", value).apply()

    var stripReferrer: Boolean
        get() = sharedPreferences.getBoolean("strip_referrer", true)
        set(value) = sharedPreferences.edit().putBoolean("strip_referrer", value).apply()

    var llmApiKey: String
        get() = sharedPreferences.getString("llm_api_key", "") ?: ""
        set(value) = sharedPreferences.edit().putString("llm_api_key", value).apply()

    var isDesktopMode: Boolean
        get() = sharedPreferences.getBoolean("is_desktop_mode", false)
        set(value) = sharedPreferences.edit().putBoolean("is_desktop_mode", value).apply()

    var slow3g: Boolean
        get() = sharedPreferences.getBoolean("slow_3g", false)
        set(value) = sharedPreferences.edit().putBoolean("slow_3g", value).apply()
        
    var slow3gDelay: Int
        get() = sharedPreferences.getInt("slow_3g_delay", 1200)
        set(value) = sharedPreferences.edit().putInt("slow_3g_delay", value).apply()

    var llmBaseUrl: String
        get() = sharedPreferences.getString("llm_base_url", "https://api.groq.com/openai/v1/chat/completions") ?: "https://api.groq.com/openai/v1/chat/completions"
        set(value) = sharedPreferences.edit().putString("llm_base_url", value).apply()

    var llmModel: String
        get() = sharedPreferences.getString("llm_model", "llama3-8b-8192") ?: "llama3-8b-8192"
        set(value) = sharedPreferences.edit().putString("llm_model", value).apply()
        
    var useLocalLlm: Boolean
        get() = sharedPreferences.getBoolean("use_local_llm", true)
        set(value) = sharedPreferences.edit().putBoolean("use_local_llm", value).apply()

    var llmTemperature: Float
        get() = sharedPreferences.getFloat("llm_temperature", 0.7f)
        set(value) = sharedPreferences.edit().putFloat("llm_temperature", value).apply()

    var llmSystemPrompt: String
        get() = sharedPreferences.getString("llm_system_prompt", "") ?: ""
        set(value) = sharedPreferences.edit().putString("llm_system_prompt", value).apply()

    var llmThemePreset: String
        get() = sharedPreferences.getString("llm_theme_preset", "MATRIX") ?: "MATRIX"
        set(value) = sharedPreferences.edit().putString("llm_theme_preset", value).apply()

    var llmCustomWallpaperUri: String
        get() = sharedPreferences.getString("llm_wallpaper_uri", "") ?: ""
        set(value) = sharedPreferences.edit().putString("llm_wallpaper_uri", value).apply()

    var llmWallpaperDimming: Int
        get() = sharedPreferences.getInt("llm_wallpaper_dimming", 50)
        set(value) = sharedPreferences.edit().putInt("llm_wallpaper_dimming", value).apply()

    var llmFontStyle: String
        get() = sharedPreferences.getString("llm_font_style", "MONOSPACE") ?: "MONOSPACE"
        set(value) = sharedPreferences.edit().putString("llm_font_style", value).apply()

    var llmFontSize: Int
        get() = sharedPreferences.getInt("llm_font_size", 14)
        set(value) = sharedPreferences.edit().putInt("llm_font_size", value).apply()

    var llmWebSearchMode: Boolean
        get() = sharedPreferences.getBoolean("llm_web_search_mode", false)
        set(value) = sharedPreferences.edit().putBoolean("llm_web_search_mode", value).apply()

    var userscript: String
        get() = sharedPreferences.getString("userscript", "") ?: ""
        set(value) = sharedPreferences.edit().putString("userscript", value).apply()

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    fun updateGeckoConfig(context: Context) {
        try {
            val configFile = java.io.File(context.filesDir, "geckoview-config.yaml")
            val prefsMap = mutableMapOf<String, Any>()

            if (blockWebRtc) {
                prefsMap["media.peerconnection.enabled"] = false
                prefsMap["media.peerconnection.ice.proxy_only"] = true
                prefsMap["media.peerconnection.ice.default_address_only"] = true
                prefsMap["media.peerconnection.ice.no_host"] = true
            } else {
                prefsMap["media.peerconnection.enabled"] = true
            }

            if (stripReferrer) {
                prefsMap["network.http.referer.XOriginPolicy"] = 2
                prefsMap["network.http.referer.trimmingPolicy"] = 2
                prefsMap["network.http.referer.XOriginTrimmingPolicy"] = 2
            } else {
                prefsMap["network.http.referer.XOriginPolicy"] = 0
                prefsMap["network.http.referer.trimmingPolicy"] = 0
                prefsMap["network.http.referer.XOriginTrimmingPolicy"] = 0
            }

            if (dohEnabled) {
                prefsMap["network.trr.mode"] = 3
                val uri = if (dohProvider.isNotBlank()) dohProvider else "https://cloudflare-dns.com/dns-query"
                prefsMap["network.trr.uri"] = uri
                val bootstrap = when {
                    uri.contains("cloudflare") -> "1.1.1.1"
                    uri.contains("quad9") -> "9.9.9.9"
                    uri.contains("adguard") -> "94.140.14.14"
                    else -> "1.1.1.1"
                }
                prefsMap["network.trr.bootstrapAddress"] = bootstrap
            } else {
                prefsMap["network.trr.mode"] = 0
            }

            val yamlBuilder = StringBuilder("prefs:\n")
            for ((key, value) in prefsMap) {
                if (value is String) {
                    yamlBuilder.append("  ").append(key).append(": \"").append(value).append("\"\n")
                } else {
                    yamlBuilder.append("  ").append(key).append(": ").append(value).append("\n")
                }
            }
            configFile.writeText(yamlBuilder.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
