package me.barmural.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

fun interface SessionCookies { fun header(): String }

/** Read-only API. No passwords, device impersonation, background polling or automatic retries. */
class InstagramApi(
    private val cookies: SessionCookies,
    private val userAgent: String,
    private val base: HttpUrl = "https://www.instagram.com/".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
) {
    suspend fun profile(username: String): Profile = profileContent(username).profile
    suspend fun profileContent(username: String): ProfileContent {
        val clean = username.trim().removePrefix("@")
        require(Regex("[A-Za-z0-9._]{1,30}").matches(clean)) { "Informe um nome de usuário válido." }
        val root = get("api/v1/users/web_profile_info/", mapOf("username" to clean))
        val embedded = root.optJSONObject("data")?.optJSONObject("user")?.optJSONObject("edge_owner_to_timeline_media")
        // A GraphQL cursor cannot be reused as a v1 max_id. Display embedded posts without
        // inventing cross-protocol pagination; v1 pages paginate only with their own cursor.
        val page = if (embedded != null) InstagramJson.page(root).copy(cursor = null) else null
        return ProfileContent(InstagramJson.profile(root), page)
    }
    suspend fun userPosts(userId: String, cursor: String? = null): Page {
        require(userId.all(Char::isDigit) && userId.isNotEmpty())
        return InstagramJson.page(get("api/v1/feed/user/$userId/", pageParams(cursor)))
    }
    suspend fun timeline(cursor: String? = null): Page {
        if (!hasSession(cookies.header())) throw ApiFailure.Login()
        return InstagramJson.page(get("api/v1/feed/timeline/", pageParams(cursor)))
    }
    suspend fun comments(postId: String): List<Comment> {
        require(Regex("[0-9_]+").matches(postId))
        return InstagramJson.comments(get("api/v1/media/$postId/comments/", mapOf("can_support_threading" to "true")))
    }
    private fun pageParams(cursor: String?) = buildMap {
        put("count", "12")
        if (!cursor.isNullOrBlank()) put("max_id", cursor)
    }
    private suspend fun get(path: String, params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val url = base.resolve(path)!!.newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val cookie = cookies.header()
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent).header("Accept", "application/json")
            .header("X-IG-App-ID", "936619743392459")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", base.toString())
            .apply {
                if (cookie.isNotBlank()) header("Cookie", cookie)
                cookieValue(cookie, "csrftoken")?.let { header("X-CSRFToken", it) }
            }.build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) throw ApiFailure.RateLimit()
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val message = json?.optString("message").orEmpty()
                if (json?.has("challenge") == true || message.contains("challenge_required") || message.contains("checkpoint_required")) throw ApiFailure.Challenge()
                if (response.code == 401 || response.code in 300..399 || message.contains("login_required")) throw ApiFailure.Login()
                if (response.code == 403 || response.code == 404) throw ApiFailure.Unavailable()
                if (response.code == 405 || response.code == 410) throw ApiFailure.Incompatible()
                if (!response.isSuccessful) throw ApiFailure.Network()
                if (json == null || json.optString("status") == "fail") throw ApiFailure.Incompatible()
                json
            }
        } catch (e: IOException) { throw ApiFailure.Network() }
    }
    companion object {
        fun cookieValue(header: String, name: String): String? = header.split(';').map { it.trim().split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }?.get(1)?.takeIf(String::isNotBlank)
        fun hasSession(header: String) = cookieValue(header, "sessionid") != null
    }
}
