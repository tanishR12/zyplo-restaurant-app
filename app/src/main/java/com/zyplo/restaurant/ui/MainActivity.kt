package com.zyplo.restaurant.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.zyplo.restaurant.R
import com.zyplo.restaurant.alerts.OrderWatchService
import com.zyplo.restaurant.bridge.OrderDetectorJs
import com.zyplo.restaurant.bridge.WebAppBridge
import com.zyplo.restaurant.bridge.WebCompatJs
import com.zyplo.restaurant.data.Config
import com.zyplo.restaurant.data.Prefs
import com.zyplo.restaurant.device.DeviceSettings
import com.zyplo.restaurant.location.RestaurantLocationService
import com.zyplo.restaurant.overlay.OverlayBubbleService
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var pendingChooserParams: WebChromeClient.FileChooserParams? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null
    private var webPermissionRequest: PermissionRequest? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = result.data?.let { WebChromeClient.FileChooserParams.parseResult(result.resultCode, it) }
        val finalUris = uris ?: cameraUri?.let { arrayOf(it) }
        fileCallback?.onReceiveValue(finalUris)
        fileCallback = null
        cameraUri = null
        pendingChooserParams = null
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchFileChooser() else {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            pendingChooserParams = null
        }
    }

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        grantPendingWebPermissions()
        askBackgroundIfNeeded()
    }

    private val backgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { DeviceSettings.promptNextSpecialSetting(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        setupWebView()
        refreshFcmToken()
        maybeStartPartnerServices()
        requestAllInAppPermissions()
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

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
        injectWebsiteBridge()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
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
            setSupportMultipleWindows(true)
            setGeolocationEnabled(true)
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 ZyploRestaurant/1.3"
        }
        webView.addJavascriptInterface(WebAppBridge(this) {
            runOnUiThread { onPartnerReady() }
        }, "ZyploApp")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleUrl(request.url.toString())
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                injectWebsiteBridge()
            }

            override fun onPageFinished(view: WebView, url: String) {
                Prefs.lastPortalUrl = url
                if (Config.looksLoggedIn(url)) {
                    Prefs.loggedIn = true
                    onPartnerReady()
                }
                injectWebsiteBridge()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                geoOrigin = origin
                geoCallback = callback
                val missing = DeviceSettings.missingRuntimePermissions(this@MainActivity)
                    .filter { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION }
                    .toTypedArray()
                if (missing.isNotEmpty()) {
                    runtimePermissions.launch(missing)
                } else {
                    callback?.invoke(origin, true, true)
                    geoCallback = null
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                webPermissionRequest = request
                val needed = mutableListOf<String>()
                request.resources.forEach { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> needed += Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> needed += Manifest.permission.RECORD_AUDIO
                    }
                }
                val missing = needed.filter {
                    androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
                if (missing.isNotEmpty()) {
                    runtimePermissions.launch(missing)
                } else {
                    request.grant(request.resources)
                    webPermissionRequest = null
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val extra = WebView(this@MainActivity)
                extra.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (handleUrl(url)) return true
                        webView.loadUrl(url)
                        return true
                    }
                }
                transport.webView = extra
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                pendingChooserParams = fileChooserParams
                val cam = androidx.core.content.ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA
                )
                if (cam != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                } else {
                    launchFileChooser()
                }
                return true
            }
        }
    }

    private fun injectWebsiteBridge() {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(WebCompatJs.SCRIPT, null)
        webView.evaluateJavascript(OrderDetectorJs.SCRIPT, null)
        Prefs.fcmToken?.let { token ->
            webView.evaluateJavascript(
                "window.localStorage.setItem('zyplo_fcm_token','$token'); window.localStorage.setItem('zyplo_pending_fcm_token_v1','$token'); window.dispatchEvent(new CustomEvent('zyplo-fcm-token',{detail:'$token'}));",
                null
            )
        }
    }

    private fun handleUrl(url: String): Boolean {
        if (url.startsWith("zyplo://") || url.startsWith("about:") || url.startsWith("blob:") || url.startsWith("data:")) {
            return false
        }
        if (Config.shouldOpenExternally(url)) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            return true
        }
        return false
    }

    private fun grantPendingWebPermissions() {
        geoCallback?.invoke(geoOrigin, true, true)
        geoCallback = null
        webPermissionRequest?.let { request ->
            runCatching { request.grant(request.resources) }
        }
        webPermissionRequest = null
    }

    private fun launchFileChooser() {
        val params = pendingChooserParams
        val capture = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        val photo = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "capture_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
        capture.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
        capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(capture))
        }
        try {
            fileChooser.launch(chooser)
        } catch (_: ActivityNotFoundException) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
    }

    private fun requestAllInAppPermissions() {
        val missing = DeviceSettings.missingRuntimePermissions(this)
        if (missing.isNotEmpty()) {
            runtimePermissions.launch(missing)
        } else {
            askBackgroundIfNeeded()
        }
    }

    private fun askBackgroundIfNeeded() {
        if (DeviceSettings.needsBackgroundLocation(this)) {
            backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            DeviceSettings.promptNextSpecialSetting(this)
        }
    }

    private fun onPartnerReady() {
        OrderWatchService.start(this)
        OrderWatchService.syncNow(this)
        OverlayBubbleService.start(this)
        RestaurantLocationService.start(this)
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
            Prefs.registeredPushToken = null
            runCatching { com.zyplo.restaurant.orders.LiveOrderSync.registerPushIfNeeded() }
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    "window.localStorage.setItem('zyplo_fcm_token','$token'); window.dispatchEvent(new CustomEvent('zyplo-fcm-token',{detail:'$token'}));",
                    null
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ORDERS = "open_orders"
    }
}
