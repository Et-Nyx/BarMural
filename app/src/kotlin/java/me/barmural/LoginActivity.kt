package me.barmural

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.barmural.data.InstagramApi
import me.barmural.ui.BarMuralTheme

/** The only web screen: credentials and account checks remain on Instagram's HTTPS origin. */
class LoginActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { BarMuralTheme {
            var error by remember { mutableStateOf<String?>(null) }
            Scaffold(topBar = { TopAppBar(title = { Text("Login oficial") },
                navigationIcon = { TextButton(onClick = { finish() }) { Text("Voltar") } },
                actions = { TextButton(onClick = {
                    val cookie = CookieManager.getInstance().getCookie("https://www.instagram.com/").orEmpty()
                    if (InstagramApi.hasSession(cookie)) {
                        CookieManager.getInstance().flush(); setResult(RESULT_OK); finish()
                    } else error = "Conclua o login e as verificações do Instagram primeiro."
                }) { Text("Concluir") } }) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text("instagram.com • Sua senha não é lida pelo BarMural.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                    AndroidView(factory = { context -> WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !allowed(request.url)
                            @Deprecated("Legacy callback")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !allowed(Uri.parse(url))
                            override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) { handler.cancel() }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                if (request.isForMainFrame) error = "Não foi possível abrir o Instagram. Verifique a conexão e tente novamente."
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                        }
                        loadUrl("https://www.instagram.com/accounts/login/")
                    } }, modifier = Modifier.fillMaxWidth().weight(1f), onRelease = { it.stopLoading(); it.destroy() })
                }
            }
        } }
    }
    private fun allowed(uri: Uri): Boolean = uri.scheme == "https" && uri.userInfo == null &&
        (uri.port == -1 || uri.port == 443) && uri.host in setOf("instagram.com", "www.instagram.com")
}
