
package com.skillnexus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity

class MainActivity : Activity() {

    inner class SkillNexusBridge {

        @JavascriptInterface
        fun openSearch(query: String): Boolean {
            if (query.isBlank()) return false

            return try {
                val url = Uri.parse(
                    "https://www.google.com/search?q=${Uri.encode(query)}"
                )

                val intent = Intent(Intent.ACTION_VIEW, url)
                startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = WebViewClient()

        // 로컬 앱 화면에만 JavaScript 브리지를 연결
        webView.addJavascriptInterface(
            SkillNexusBridge(),
            "SkillNexusAndroid"
        )

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }
}
