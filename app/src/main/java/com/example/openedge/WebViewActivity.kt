package com.example.openedge

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/**
 * WebView activity for displaying web content.
 * Simple implementation with basic WebView setup.
 */
class WebViewActivity : ComponentActivity() {
    
    private val TAG = "WebViewActivity"
    private lateinit var webView: WebView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        
        webView = findViewById(R.id.webView)
        setupWebView()
        
        // Load a default page (can be changed)
        webView.loadUrl("https://www.google.com")
        
        Log.d(TAG, "WebViewActivity created")
    }
    
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Load URLs within WebView
                return false
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        Log.d(TAG, "WebViewActivity destroyed")
    }
}

