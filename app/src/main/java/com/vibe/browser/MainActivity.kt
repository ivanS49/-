package com.vibe.browser

import android.content.Context
import java.io.File
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        @Volatile
        private var sRuntime: GeckoRuntime? = null

        @Volatile
        private var sExtensionPort: WebExtension.Port? = null

        fun updateThrottlingConfig(context: Context) {
            val sm = SettingsManager(context)
            val json = JSONObject().apply {
                put("slow3g", sm.slow3g)
                put("slow3gDelay", sm.slow3gDelay)
                put("blockWebRtc", sm.blockWebRtc)
                put("antiFingerprinting", sm.antiFingerprinting)
                put("emulateScreen", sm.emulateScreen)
                put("emulatedResolution", sm.emulatedResolution)
            }
            sExtensionPort?.let { port ->
                try {
                    port.postMessage(json)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun setupExtensionCommunication(extension: WebExtension, context: Context) {
            extension.setMessageDelegate(object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    sExtensionPort = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, p: WebExtension.Port) {
                            val sm = SettingsManager(context)
                            val resp = JSONObject().apply {
                                put("slow3g", sm.slow3g)
                                put("slow3gDelay", sm.slow3gDelay)
                                put("blockWebRtc", sm.blockWebRtc)
                                put("antiFingerprinting", sm.antiFingerprinting)
                                put("emulateScreen", sm.emulateScreen)
                                put("emulatedResolution", sm.emulatedResolution)
                            }
                            try {
                                p.postMessage(resp)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun onDisconnect(p: WebExtension.Port) {
                            if (sExtensionPort == p) {
                                sExtensionPort = null
                            }
                        }
                    })

                    val sm = SettingsManager(context)
                    val initialConfig = JSONObject().apply {
                        put("slow3g", sm.slow3g)
                        put("slow3gDelay", sm.slow3gDelay)
                        put("blockWebRtc", sm.blockWebRtc)
                        put("antiFingerprinting", sm.antiFingerprinting)
                        put("emulateScreen", sm.emulateScreen)
                        put("emulatedResolution", sm.emulatedResolution)
                    }
                    try {
                        port.postMessage(initialConfig)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onMessage(
                    nativeApp: String,
                    message: Any,
                    sender: WebExtension.MessageSender
                ): GeckoResult<Any>? {
                    val sm = SettingsManager(context)
                    val resp = JSONObject().apply {
                        put("slow3g", sm.slow3g)
                        put("slow3gDelay", sm.slow3gDelay)
                        put("blockWebRtc", sm.blockWebRtc)
                        put("antiFingerprinting", sm.antiFingerprinting)
                        put("emulateScreen", sm.emulateScreen)
                        put("emulatedResolution", sm.emulatedResolution)
                    }
                    return GeckoResult.fromValue(resp)
                }
            }, "browser")
        }

        fun getOrCreateRuntime(context: Context): GeckoRuntime {
            return sRuntime ?: synchronized(this) {
                sRuntime ?: run {
                    val settingsBuilder = GeckoRuntimeSettings.Builder()
                        .aboutConfigEnabled(false)

                    val settingsManager = SettingsManager(context)
                    settingsManager.updateGeckoConfig(context)
                    val configFile = File(context.filesDir, "geckoview-config.yaml")
                    if (configFile.exists()) {
                        settingsBuilder.configFilePath(configFile.absolutePath)
                    }

                    GeckoRuntime.create(context.applicationContext, settingsBuilder.build()).also { runtime ->
                        sRuntime = runtime
                        try {
                            runtime.webExtensionController.ensureBuiltIn(
                                "resource://android/assets/webrtc_shield/",
                                "webrtc-shield@llm.local"
                            ).accept({ extension ->
                                if (extension != null) {
                                    setupExtensionCommunication(extension, context.applicationContext)
                                }
                            }, { throwable ->
                                throwable?.printStackTrace()
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private lateinit var geckoView: GeckoView
    private lateinit var vpnWarningLayout: LinearLayout
    private lateinit var urlEditText: EditText
    private lateinit var btnMenu: ImageButton
    
    private var geckoRuntime: GeckoRuntime? = null
    private val sessions = mutableListOf<GeckoSession>()
    private var currentSessionIndex = -1

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private lateinit var settingsManager: SettingsManager

    private var isNavigatingToInternalActivity = false

    override fun attachBaseContext(newBase: Context) {
        val lang = LanguageHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        applySecurityFlags()

        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoView)
        vpnWarningLayout = findViewById(R.id.vpnWarningLayout)
        urlEditText = findViewById(R.id.urlEditText)
        IncognitoKeyboardHelper.apply(urlEditText)
        btnMenu = findViewById(R.id.btnMenu)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            sessions.getOrNull(currentSessionIndex)?.goBack()
        }
        
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            sessions.getOrNull(currentSessionIndex)?.goForward()
        }
        
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            sessions.getOrNull(currentSessionIndex)?.reload()
        }

        btnMenu.setOnClickListener {
            showBrowserMenu()
        }

        urlEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                var url = urlEditText.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("resource://")) {
                        url = "https://$url"
                    }
                    loadUrl(url)
                }
                true
            } else {
                false
            }
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        setupNetworkCallback()
        checkVpnAndInitialize()
    }

    private fun showBrowserMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, btnMenu)
        popup.menu.add(0, 1, 0, getString(R.string.menu_tabs) + " (${sessions.size})")
        popup.menu.add(0, 2, 1, if (settingsManager.disableJs) getString(R.string.menu_js_off) else getString(R.string.menu_js_on))
        popup.menu.add(0, 3, 2, if (settingsManager.isDesktopMode) getString(R.string.menu_desktop_on) else getString(R.string.menu_desktop_off))
        popup.menu.add(0, 4, 3, getString(R.string.menu_home))
        popup.menu.add(0, 5, 4, getString(R.string.menu_copy))
        popup.menu.add(0, 8, 5, getString(R.string.menu_reader))
        popup.menu.add(0, 6, 6, getString(R.string.menu_panic))
        popup.menu.add(0, 7, 7, getString(R.string.menu_settings))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showTabsDialog()
                2 -> {
                    settingsManager.disableJs = !settingsManager.disableJs
                    sessions.getOrNull(currentSessionIndex)?.settings?.allowJavascript = !settingsManager.disableJs
                    sessions.getOrNull(currentSessionIndex)?.reload()
                    Toast.makeText(this, if (settingsManager.disableJs) "JS Disabled" else "JS Enabled", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    settingsManager.isDesktopMode = !settingsManager.isDesktopMode
                    val session = sessions.getOrNull(currentSessionIndex)
                    val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    session?.settings?.userAgentOverride = if (settingsManager.isDesktopMode) desktopUa else settingsManager.customUserAgent
                    session?.reload()
                }
                4 -> loadUrl("resource://android/assets/startpage.html")
                5 -> {
                    val url = urlEditText.text.toString()
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("URL", url)
                    clipboard.setPrimaryClip(clip)
                }
                6 -> {
                    destroyAllSessions()
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }
                7 -> {
                    isNavigatingToInternalActivity = true
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                8 -> toggleReaderMode()
            }
            true
        }
        popup.show()
    }

    private fun showTabsDialog() {
        val titles = sessions.mapIndexed { idx, _ -> getString(R.string.tab_title, idx + 1) }.toMutableList()
        titles.add(getString(R.string.tab_new))
        
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.tabs_title, sessions.size))
            .setItems(titles.toTypedArray()) { _, which ->
                if (which == sessions.size) {
                    createNewTab()
                } else {
                    switchToSession(which)
                }
            }
            .setPositiveButton(getString(R.string.tab_close_current)) { _, _ ->
                if (sessions.isNotEmpty()) {
                    val s = sessions.removeAt(currentSessionIndex)
                    try { s.close() } catch (_: Exception) {}
                    if (sessions.isEmpty()) {
                        createNewTab()
                    } else {
                        switchToSession(0.coerceAtMost(sessions.size - 1))
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleReaderMode() {
        val js = "javascript:(function(){" +
            "var h = document.head;" +
            "var css = '* { background: #1e1e1e !important; color: #cccccc !important; font-family: sans-serif !important; }' +" +
            " 'nav, header, footer, aside, iframe, .ad, .banner, .popup { display: none !important; }' +" +
            " 'body { max-width: 800px; margin: 0 auto; padding: 20px; font-size: 18px; line-height: 1.6; }';" +
            "var style = document.createElement('style');" +
            "style.type = 'text/css';" +
            "style.appendChild(document.createTextNode(css));" +
            "h.appendChild(style);" +
            "})();"
        sessions.getOrNull(currentSessionIndex)?.loadUri(js)
    }

    private fun applySecurityFlags() {
        if (settingsManager.blockScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToInternalActivity = false
        applySecurityFlags()
        updateThrottlingConfig(this)
    }

    override fun onStop() {
        super.onStop()
        // If not navigating to internal settings and not in PiP, lock app by finishing
        val isInPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else {
            false
        }
        if (!isNavigatingToInternalActivity && !isInPip) {
            destroyAllSessions()
            finish()
        }
    }

    override fun onBackPressed() {
        if (currentSessionIndex >= 0 && currentSessionIndex < sessions.size) {
            sessions[currentSessionIndex].goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    private fun setupNetworkCallback() {
        val request = NetworkRequest.Builder().build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { checkVpnAndInitialize() }
            }
            override fun onLost(network: Network) {
                runOnUiThread { checkVpnAndInitialize() }
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                runOnUiThread { checkVpnAndInitialize() }
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun isVpnActive(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun checkVpnAndInitialize() {
        if (isVpnActive()) {
            vpnWarningLayout.visibility = View.GONE
            geckoView.visibility = View.VISIBLE
            if (geckoRuntime == null) {
                initializeGeckoRuntime()
            }
            if (sessions.isEmpty()) {
                createNewTab("resource://android/assets/startpage.html")
            }
        } else {
            vpnWarningLayout.visibility = View.VISIBLE
            geckoView.visibility = View.GONE
            destroyAllSessions()
        }
    }

    private fun initializeGeckoRuntime() {
        geckoRuntime = getOrCreateRuntime(this)
    }

    private fun createNewTab(url: String = "resource://android/assets/startpage.html") {
        val runtime = geckoRuntime ?: return

        val sessionSettingsBuilder = GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            
        val ua = settingsManager.customUserAgent
        if (ua.isNotEmpty()) {
            sessionSettingsBuilder.userAgentOverride(ua)
        }

        val session = GeckoSession(sessionSettingsBuilder.build())
        
        // JS Toggle
        session.settings.allowJavascript = !settingsManager.disableJs
        
        // Prevent downloads
        session.promptDelegate = object : PromptDelegate {
            override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptDelegate.PromptResponse> {
                 return GeckoResult.fromValue(prompt.dismiss())
            }
        }

        // URL tracking for the address bar using ProgressDelegate
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (url.contains(".ru/") || url.endsWith(".ru")) {
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.warning_title))
                            .setMessage(getString(R.string.warning_ru_domain))
                            .setPositiveButton(getString(R.string.warning_understand_risk), null)
                            .setNegativeButton(getString(R.string.warning_leave_site)) { _, _ ->
                                session.stop()
                                loadUrl("resource://android/assets/startpage.html")
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
                
                if (settingsManager.stripReferrer) {
                    val stripRefJs = "javascript:(function(){" +
                        "try {" +
                        "  Object.defineProperty(document, 'referrer', { get: function() { return ''; }, configurable: true });" +
                        "} catch(e) {}" +
                        "})();"
                    session.loadUri(stripRefJs)
                }
                
                val customJs = settingsManager.userscript
                if (customJs.isNotEmpty()) {
                    session.loadUri("javascript:(function(){$customJs})();")
                }
                
                if (sessions.getOrNull(currentSessionIndex) == session) {
                    if (url.startsWith("resource://")) {
                        urlEditText.setText("")
                        if (url.contains("startpage.html")) {
                            session.loadUri("javascript:setLanguage('${settingsManager.appLanguage}')")
                        }
                    } else {
                        urlEditText.setText(url)
                    }
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                // Ensure language is applied if onPageStart was too early
                val currentUrl = session.navigationDelegate?.let { "" } ?: ""
                session.loadUri("javascript:if(window.setLanguage){setLanguage('${settingsManager.appLanguage}');}")
            }
        }

        session.open(runtime)

        sessions.add(session)
        currentSessionIndex = sessions.size - 1
        
        switchToSession(currentSessionIndex)
        loadUrl(url)
    }

    private fun switchToSession(index: Int) {
        if (index in sessions.indices) {
            currentSessionIndex = index
            geckoView.setSession(sessions[index])
        }
    }

    private fun loadUrl(url: String) {
        sessions.getOrNull(currentSessionIndex)?.loadUri(url)
    }

    private fun destroyAllSessions() {
        sessions.forEach { 
            try { it.close() } catch (_: Exception) {}
        }
        sessions.clear()
        currentSessionIndex = -1
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        destroyAllSessions()
    }
}
