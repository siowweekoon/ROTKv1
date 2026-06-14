package com.siowweekoon.threekingdomsduel

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // ── AdMob ad units — replace both IDs with real ones before production build ──
    private val rewardedIntUnitId = "ca-app-pub-6373194906630225/8339095958"
    private val interstitialUnitId = "ca-app-pub-6373194906630225/8487228205"

    private var rewardedIntAd: RewardedInterstitialAd? = null
    private var interstitialAd: InterstitialAd? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive — hides status bar and nav bar
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        webView = WebView(this)
        setContentView(webView)

        // Initialise AdMob — load ads only after initialisation completes
        MobileAds.initialize(this) {
            loadRewardedInterstitial()
            loadInterstitial()
        }

        // Serve local assets from a safe HTTPS-like origin so canvas/storage work
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message).setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }.show()
                return true
            }
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }.show()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            // Re-apply immersive mode if the system pulls it back
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyImmersive()
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true              // localStorage for PR save data
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false   // Web Audio plays immediately
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
        }

        webView.addJavascriptInterface(object {

            @JavascriptInterface
            fun exitApp() { finish() }

            @JavascriptInterface
            fun requestBattleStart() {
                runOnUiThread {
                    // Internet gate — ads require connectivity
                    if (!isOnline()) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("No Internet Connection")
                            .setMessage("An internet connection is required to play. Please connect and try again.")
                            .setPositiveButton("OK", null)
                            .show()
                        webView.evaluateJavascript("onAdCancelled()", null)
                        return@runOnUiThread
                    }
                    // Ad 1: Rewarded Interstitial (long, non-skippable)
                    val rAd = rewardedIntAd
                    if (rAd != null) {
                        rAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                rewardedIntAd = null
                                loadRewardedInterstitial()
                                showInterstitialThenBattle()
                            }
                            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                                rewardedIntAd = null
                                loadRewardedInterstitial()
                                showInterstitialThenBattle()
                            }
                        }
                        rAd.show(this@MainActivity) { _: RewardItem -> }
                    } else {
                        showInterstitialThenBattle()
                    }
                }
            }

        }, "AndroidBridge")

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    // ── Ad 2: standard interstitial, then signal JS to start battle ───────────
    private fun showInterstitialThenBattle() {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial()
                    webView.evaluateJavascript("onAdComplete()", null)
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    interstitialAd = null
                    loadInterstitial()
                    webView.evaluateJavascript("onAdComplete()", null)
                }
            }
            ad.show(this)
        } else {
            webView.evaluateJavascript("onAdComplete()", null)
        }
    }

    // ── Ad loaders ────────────────────────────────────────────────────────────
    private fun loadRewardedInterstitial() {
        RewardedInterstitialAd.load(this, rewardedIntUnitId, AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) { rewardedIntAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { rewardedIntAd = null }
            })
    }

    private fun loadInterstitial() {
        InterstitialAd.load(this, interstitialUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitialAd = null }
            })
    }

    // ── Connectivity check ────────────────────────────────────────────────────
    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    private var lastBackPress = 0L

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            super.onBackPressed()
        } else {
            lastBackPress = now
            android.widget.Toast.makeText(this, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
