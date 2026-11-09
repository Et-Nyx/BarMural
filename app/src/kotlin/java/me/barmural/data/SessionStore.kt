package me.barmural.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** WebView's app-private cookie store is the sole persistent authentication store. */
class SessionStore(private val context: Context) : SessionCookies {
    private val prefs = context.getSharedPreferences("local_preferences", Context.MODE_PRIVATE)
    @Volatile private var snapshot = ""
    override fun header() = snapshot
    fun refresh() { snapshot = CookieManager.getInstance().getCookie("https://www.instagram.com/").orEmpty() }
    fun favorites(): List<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().sorted()
    fun toggleFavorite(username: String) {
        val set = favorites().toMutableSet()
        if (!set.add(username)) set.remove(username)
        prefs.edit().putStringSet("favorites", set).apply()
    }
    suspend fun clear() {
        snapshot = ""
        coil3.SingletonImageLoader.get(context).memoryCache?.clear()
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies {
                snapshot = ""
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                android.webkit.WebView(context).apply { clearCache(true); destroy() }
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
}
