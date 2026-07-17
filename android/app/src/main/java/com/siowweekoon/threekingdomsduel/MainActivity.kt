package com.siowweekoon.threekingdomsduel

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
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
import com.android.billingclient.api.*
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
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // ── AdMob ad units ────────────────────────────────────────────────────────
    private val rewardedIntUnitId = "ca-app-pub-6373194906630225/8339095958"
    private val interstitialUnitId = "ca-app-pub-6373194906630225/8487228205"

    private var rewardedIntAd: RewardedInterstitialAd? = null
    private var interstitialAd: InterstitialAd? = null

    // ── IAP — Premium Unlock $2.99 ────────────────────────────────────────────
    private val PREMIUM_PRODUCT = "premium_unlock"
    private lateinit var billingClient: BillingClient
    private lateinit var prefs: android.content.SharedPreferences
    private var premiumUnlocked = false

    // ── Ad timing: show at most once every 10 minutes ─────────────────────────
    private val AD_INTERVAL_MS = 10 * 60 * 1000L
    private var lastAdShowTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        // Load persisted state
        prefs = getSharedPreferences("tkd_prefs", Context.MODE_PRIVATE)
        premiumUnlocked = prefs.getBoolean("premium_unlocked", false)
        lastAdShowTime = prefs.getLong("last_ad_time", 0L)

        webView = WebView(this)
        setContentView(webView)

        // Initialise AdMob only if needed
        if (BuildConfig.ADS_ENABLED) {
            MobileAds.initialize(this) {
                loadRewardedInterstitial()
                loadInterstitial()
            }
        }

        setupBilling()

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
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyImmersive()
                if (premiumUnlocked || !BuildConfig.ADS_ENABLED) {
                    runOnUiThread {
                        webView.evaluateJavascript("if(typeof onPremiumUnlocked==='function')onPremiumUnlocked()", null)
                    }
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
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
                    // Skip ads: debug build only
                    if (!BuildConfig.ADS_ENABLED) {
                        webView.evaluateJavascript("onAdComplete()", null)
                        return@runOnUiThread
                    }
                    // Internet gate
                    if (!isOnline()) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("No Internet Connection")
                            .setMessage("An internet connection is required for ads. Please connect and try again.")
                            .setPositiveButton("OK", null)
                            .show()
                        webView.evaluateJavascript("onAdCancelled()", null)
                        return@runOnUiThread
                    }
                    // Ad 1: Rewarded Interstitial
                    val rAd = rewardedIntAd
                    if (rAd != null) {
                        rAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                recordAdShown()
                                rewardedIntAd = null; loadRewardedInterstitial()
                                showInterstitialThenBattle()
                            }
                            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                                rewardedIntAd = null; loadRewardedInterstitial()
                                showInterstitialThenBattle()
                            }
                        }
                        rAd.show(this@MainActivity) { _: RewardItem -> }
                    } else {
                        showInterstitialThenBattle()
                    }
                }
            }

            @JavascriptInterface
            fun isPremiumUnlocked(): Boolean = premiumUnlocked

            @JavascriptInterface
            fun purchasePremium() {
                runOnUiThread { launchPremiumPurchase() }
            }

            @JavascriptInterface
            fun restorePurchases() {
                runOnUiThread { queryExistingPurchases() }
            }

        }, "AndroidBridge")

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun recordAdShown() {
        lastAdShowTime = System.currentTimeMillis()
        prefs.edit().putLong("last_ad_time", lastAdShowTime).apply()
    }

    // ── Billing setup ─────────────────────────────────────────────────────────
    private fun setupBilling() {
        billingClient = BillingClient.newBuilder(this)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases -> purchases.forEach { handlePurchase(it) } }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PREMIUM_PRODUCT) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            premiumUnlocked = true
            prefs.edit().putBoolean("premium_unlocked", true).apply()
            if (!purchase.isAcknowledged) {
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()
                ) {}
            }
            runOnUiThread {
                webView.evaluateJavascript("if(typeof onPremiumUnlocked==='function')onPremiumUnlocked()", null)
            }
        }
    }

    private fun launchPremiumPurchase() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        ) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && details.isNotEmpty()) {
                runOnUiThread {
                    billingClient.launchBillingFlow(
                        this,
                        BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(details[0]).build()
                            )).build()
                    )
                }
            }
        }
    }

    // ── Ad 2: interstitial then battle ────────────────────────────────────────
    private fun showInterstitialThenBattle() {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    recordAdShown()
                    interstitialAd = null; loadInterstitial()
                    webView.evaluateJavascript("onAdComplete()", null)
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    interstitialAd = null; loadInterstitial()
                    webView.evaluateJavascript("onAdComplete()", null)
                }
            }
            ad.show(this)
        } else {
            webView.evaluateJavascript("onAdComplete()", null)
        }
    }

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

    // ── Helpers ───────────────────────────────────────────────────────────────
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

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        super.attachBaseContext(newBase.createConfigurationContext(config))
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
