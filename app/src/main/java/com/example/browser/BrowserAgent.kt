package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class WebTab(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String = "New Tab",
    val url: String = "https://duckduckgo.com",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

class BrowserAgent(private val context: Context) {

    companion object {
        private const val TAG = "BrowserAgent"
    }

    private val _tabs = MutableStateFlow<List<WebTab>>(listOf(WebTab()))
    val tabs: StateFlow<List<WebTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String>(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val webViewMap = mutableMapOf<String, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String): WebView {
        return webViewMap.getOrPut(tabId) {
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        updateTab(tabId) { it.copy(url = url ?: it.url, isLoading = true) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val title = view?.title ?: "Web"
                        updateTab(tabId) {
                            it.copy(
                                title = title,
                                url = url ?: it.url,
                                isLoading = false,
                                canGoBack = view?.canGoBack() ?: false,
                                canGoForward = view?.canGoForward() ?: false
                            )
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (!title.isNullOrBlank()) {
                            updateTab(tabId) { it.copy(title = title) }
                        }
                    }
                }
                loadUrl("https://duckduckgo.com")
            }
        }
    }

    fun openUrl(url: String): Pair<Boolean, String> {
        val targetUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://duckduckgo.com/?q=${url.replace(" ", "+")}"
        }

        val activeId = _activeTabId.value
        val webView = getOrCreateWebView(activeId)
        webView.post {
            webView.loadUrl(targetUrl)
        }
        return Pair(true, "Navigated to $targetUrl")
    }

    fun search(query: String): Pair<Boolean, String> {
        val searchUrl = "https://duckduckgo.com/?q=${query.replace(" ", "+")}"
        return openUrl(searchUrl)
    }

    fun executeMediaControl(action: String): Pair<Boolean, String> {
        val activeId = _activeTabId.value
        val webView = webViewMap[activeId] ?: return Pair(false, "No active browser tab found")

        val jsCode = when (action.lowercase()) {
            "play", "resume" -> "document.querySelectorAll('video').forEach(v => v.play());"
            "pause", "stop" -> "document.querySelectorAll('video').forEach(v => v.pause());"
            "toggle" -> "document.querySelectorAll('video').forEach(v => v.paused ? v.play() : v.pause());"
            "mute" -> "document.querySelectorAll('video').forEach(v => v.muted = true);"
            "unmute" -> "document.querySelectorAll('video').forEach(v => v.muted = false);"
            "forward", "skip" -> "document.querySelectorAll('video').forEach(v => v.currentTime += 10);"
            "backward", "rewind" -> "document.querySelectorAll('video').forEach(v => v.currentTime -= 10);"
            "fullscreen" -> "document.querySelector('video')?.requestFullscreen();"
            else -> ""
        }

        if (jsCode.isBlank()) {
            return Pair(false, "Unknown media action: $action")
        }

        webView.post {
            webView.evaluateJavascript(jsCode, null)
        }
        return Pair(true, "Executed media control: $action")
    }

    fun scroll(direction: String, amount: Int = 400): Pair<Boolean, String> {
        val activeId = _activeTabId.value
        val webView = webViewMap[activeId] ?: return Pair(false, "No active browser tab found")

        val dy = if (direction.uppercase() == "UP") -amount else amount
        webView.post {
            webView.evaluateJavascript("window.scrollBy({ top: $dy, behavior: 'smooth' });", null)
        }
        return Pair(true, "Scrolled browser $direction by $amount pixels")
    }

    fun goBack(): Boolean {
        val activeId = _activeTabId.value
        val webView = webViewMap[activeId] ?: return false
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun goForward(): Boolean {
        val activeId = _activeTabId.value
        val webView = webViewMap[activeId] ?: return false
        if (webView.canGoForward()) {
            webView.goForward()
            return true
        }
        return false
    }

    fun createNewTab(url: String = "https://duckduckgo.com"): String {
        val newTab = WebTab(url = url)
        val updated = _tabs.value + newTab
        _tabs.value = updated
        _activeTabId.value = newTab.id
        getOrCreateWebView(newTab.id).apply {
            post { loadUrl(url) }
        }
        return newTab.id
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) {
            // Keep at least one tab
            createNewTab()
        }
        val remaining = _tabs.value.filter { it.id != tabId }
        _tabs.value = remaining
        webViewMap.remove(tabId)?.destroy()

        if (_activeTabId.value == tabId) {
            _activeTabId.value = remaining.lastOrNull()?.id ?: createNewTab()
        }
    }

    private fun updateTab(tabId: String, transform: (WebTab) -> WebTab) {
        val list = _tabs.value.toMutableList()
        val index = list.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            list[index] = transform(list[index])
            _tabs.value = list
        }
    }
}
