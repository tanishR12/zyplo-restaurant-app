package com.zyplo.restaurant.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.zyplo.restaurant.R
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.bridge.OrderDetectorJs
import com.zyplo.restaurant.bridge.WebAppBridge
import com.zyplo.restaurant.data.Config
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.location.RestaurantLocationService
import com.zyplo.restaurant.overlay.OverlayBubbleService
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = result.data?.let { WebChromeClient.FileChooserParams.parseResult(result.resultCode, it) }
        val finalUris = uris ?: cameraUri?.let { arrayOf(it) }
        fileCallback?.onReceiveValue(finalUris)
        fileCallback = null
        cameraUri = null
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        setupWebView()
        refreshFcmToken()
        OrderWatchService.start(this)
        maybeStartPartnerServices()
        val startUrl = if (intent.getBooleanExtra(EXTRA_OPEN_ORDERS, false)) {
            Prefs.lastPortalUrl
        } else {
            Prefs.lastPortalUrl.ifBlank { Config.PORTAL_URL }
        }
        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ORDERS, false)) {
            webView.loadUrl(Prefs.lastPortalUrl)
            OrderWatchService.stopSiren(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setGeolocationEnabled(true)
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString ZyploRestaurantApp/1.0"
        }
        webView.addJavascriptInterface(WebAppBridge(this) {
            runOnUiThread { onPartnerReady() }
        }, "ZyploApp")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (Config.isTrustedUrl(url) || url.startsWith("zyplo://")) {
                    return false
                }
                if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    return true
                }
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                Prefs.lastPortalUrl = url
                if (Config.looksLoggedIn(url)) {
                    Prefs.loggedIn = true
                    onPartnerReady()
                }
                view.evaluateJavascript(OrderDetectorJs.SCRIPT, null)
                Prefs.fcmToken?.let { token ->
                    view.evaluateJavascript(
                        "window.localStorage.setItem('zyplo_fcm_token','$token');",
                        null
                    )
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                cameraPermission.launch(Manifest.permission.CAMERA)
                val capture = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                val photo = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "capture_${System.currentTimeMillis()}.jpg")
                cameraUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", photo)
                capture.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, fileChooserParams?.createIntent())
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(capture))
                }
                return try {
                    fileChooser.launch(chooser)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }
    }

    private fun onPartnerReady() {
        OrderWatchService.start(this)
        OverlayBubbleService.start(this)
        RestaurantLocationService.start(this)
        requestIgnoreBatteryIfNeeded()
    }

    private fun maybeStartPartnerServices() {
        if (Prefs.loggedIn) onPartnerReady()
    }

    private fun refreshFcmToken() {
        val messaging = FirebaseMessaging.getInstance()
        messaging.subscribeToTopic("restaurant_orders")
        messaging.subscribeToTopic("zyplo_restaurant")
        messaging.subscribeToTopic("lovebul_restaurant")
        messaging.token.addOnSuccessListener { token ->
            Prefs.fcmToken = token
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    "window.localStorage.setItem('zyplo_fcm_token','$token'); window.dispatchEvent(new CustomEvent('zyplo-fcm-token',{detail:'$token'}));",
                    null
                )
            }
        }
    }

    private fun requestIgnoreBatteryIfNeeded() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.battery_manual, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_OPEN_ORDERS = "open_orders"
    }
}
