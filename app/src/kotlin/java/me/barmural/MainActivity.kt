package me.barmural

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.barmural.data.InstagramApi
import me.barmural.data.SessionStore
import me.barmural.ui.*

class MainActivity : ComponentActivity() {
    private val session by lazy { SessionStore(applicationContext) }
    private val model by viewModels<MuralViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MuralViewModel(InstagramApi(session, WebSettings.getDefaultUserAgent(applicationContext)), session) as T
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coil3.SingletonImageLoader.setSafe { context -> coil3.ImageLoader.Builder(context).diskCache(null).build() }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { BarMuralTheme { MuralApp(model) { startActivity(Intent(this, LoginActivity::class.java)) } } }
    }
    override fun onResume() { super.onResume(); model.refreshSession() }
}
