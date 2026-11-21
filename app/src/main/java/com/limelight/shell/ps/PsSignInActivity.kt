package com.limelight.shell.ps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.limelight.ps.PsAccountId
import com.limelight.ps.PsOAuth
import kotlin.concurrent.thread

/**
 * Sony's PSN sign-in page on its own, full screen, with nothing of the shell over it (spec 3.1). The redirect with the code is
 * caught and never loaded; the code becomes the account id here and is returned as [EXTRA_ACCOUNT] (base64), or a message as
 * [EXTRA_ERROR]. Back goes back in the page, then cancels. Started through [Contract].
 */
class PsSignInActivity : Activity() {
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        CookieManager.getInstance().removeAllCookies(null)
        val view = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val code = PsOAuth.codeFrom(request.url.toString()) ?: return false
                    thread {
                        val result = runCatching { PsAccountId.toBase64(PsOAuth.accountId(code)) }
                        runOnUiThread {
                            result.onSuccess { setResult(RESULT_OK, Intent().putExtra(EXTRA_ACCOUNT, it)) }
                                .onFailure { setResult(RESULT_OK, Intent().putExtra(EXTRA_ERROR, it.message.orEmpty())) }
                            finish()
                        }
                    }
                    return true
                }
            }
            loadUrl(PsOAuth.authorizeUrl())
        }
        web = view
        setContentView(view)
    }

    @Deprecated("Back in the page first")
    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    /** The sign-in's outcome: the account id (base64), a failure message, or null when the user backed out. */
    sealed interface Outcome {
        data class Account(val id: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    object Contract : ActivityResultContract<Unit, Outcome?>() {
        override fun createIntent(context: Context, input: Unit) = Intent(context, PsSignInActivity::class.java)
        override fun parseResult(resultCode: Int, intent: Intent?): Outcome? {
            if (resultCode != RESULT_OK || intent == null) return null
            intent.getStringExtra(EXTRA_ACCOUNT)?.let { return Outcome.Account(it) }
            return Outcome.Failed(intent.getStringExtra(EXTRA_ERROR).orEmpty())
        }
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_ERROR = "error"
    }
}
