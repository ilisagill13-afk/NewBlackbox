package com.usvisa.slotbooker.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.usvisa.slotbooker.data.ConfigStore
import com.usvisa.slotbooker.databinding.ActivityLoginBinding

/**
 * Visible WebView for the one-time login. The user completes any Cloudflare/CAPTCHA challenge here;
 * the resulting session cookies persist app-wide (shared with the monitor service's WebView).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val config = ConfigStore(this).load()
        val signInUrl = "https://ais.usvisa-info.com/${config.locale}/niv/users/sign_in"

        CookieManager.getInstance().setAcceptCookie(true)
        with(binding.webView) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (config.email.isNotBlank()) prefillCredentials(view, config.email, config.password)
                    CookieManager.getInstance().flush()
                    binding.doneButton.isEnabled = !url.contains("users/sign_in")
                }
            }
            loadUrl(signInUrl)
        }

        binding.doneButton.setOnClickListener {
            CookieManager.getInstance().flush()
            finish()
        }
    }

    private fun prefillCredentials(view: WebView, email: String, password: String) {
        val js = """
            (function(){
              var e=document.getElementById('user_email');
              var p=document.getElementById('user_password');
              if(e && !e.value){ e.value=${quote(email)}; }
              if(p && !p.value){ p.value=${quote(password)}; }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun quote(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
