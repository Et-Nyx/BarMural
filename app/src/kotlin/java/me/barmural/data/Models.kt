package me.barmural.data

import org.json.JSONArray
import org.json.JSONObject

data class Profile(val id: String, val username: String, val name: String, val biography: String,
                   val avatar: String?, val followers: Long, val privateAccount: Boolean)
data class ProfileContent(val profile: Profile, val initialPosts: Page?)
data class MediaAsset(val image: String?, val video: String?)
data class Post(val id: String, val author: String, val caption: String, val assets: List<MediaAsset>,
                val likes: Long, val comments: Long)
data class Page(val posts: List<Post>, val cursor: String?)
data class Comment(val author: String, val text: String)

/** Accept both web GraphQL and v1 payloads. Missing optional fields stay optional. */
object InstagramJson {
    fun profile(root: JSONObject): Profile {
        val user = root.optJSONObject("data")?.optJSONObject("user") ?: root.optJSONObject("user")
            ?: throw ApiFailure.Incompatible()
        val id = user.optString("pk").ifBlank { user.optString("id") }
        val username = user.optString("username")
        if (id.isBlank() || username.isBlank()) throw ApiFailure.Incompatible()
        return Profile(id, username, user.optString("full_name"), user.optString("biography"),
            (user.nullableString("profile_pic_url_hd") ?: user.nullableString("profile_pic_url")).httpsMediaUrl(),
            user.optJSONObject("edge_followed_by")?.optLong("count") ?: user.optLong("follower_count"),
            user.optBoolean("is_private"))
    }

    fun page(root: JSONObject): Page {
        val graph = root.optJSONObject("data")?.optJSONObject("user")
            ?.optJSONObject("edge_owner_to_timeline_media")
        val posts = when {
            graph != null -> graph.optJSONArray("edges").objects().mapNotNull { it.optJSONObject("node") }
            root.has("items") -> root.optJSONArray("items").objects()
            root.has("feed_items") -> root.optJSONArray("feed_items").objects().mapNotNull { it.optJSONObject("media_or_ad") }
            else -> throw ApiFailure.Incompatible()
        }.filter { !it.has("ad_id") && !it.optBoolean("is_paid_partnership") }.mapNotNull(::post)
        val cursor = if (graph != null) {
            graph.optJSONObject("page_info")?.takeIf { it.optBoolean("has_next_page") }?.nullableString("end_cursor")
        } else if (root.optBoolean("more_available", root.has("next_max_id"))) root.nullableString("next_max_id") else null
        return Page(posts, cursor)
    }

    fun post(item: JSONObject): Post? {
        val id = item.nullableString("pk") ?: item.nullableString("id") ?: return null
        val owner = item.optJSONObject("user") ?: item.optJSONObject("owner")
        val caption = item.optJSONObject("caption")?.optString("text")
            ?: item.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)
                ?.optJSONObject("node")?.optString("text").orEmpty()
        val children = item.optJSONArray("carousel_media")?.objects()
            ?: item.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")?.objects()
                ?.mapNotNull { it.optJSONObject("node") } ?: listOf(item)
        val assets = children.map { child ->
            MediaAsset((child.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)
                ?.nullableString("url") ?: child.nullableString("display_url")).httpsMediaUrl(),
                (child.optJSONArray("video_versions")?.optJSONObject(0)?.nullableString("url") ?: child.nullableString("video_url")).httpsMediaUrl())
        }.filter { it.image != null || it.video != null }
        return Post(id, owner?.optString("username").orEmpty(), caption, assets,
            item.optJSONObject("edge_media_preview_like")?.optLong("count") ?: item.optLong("like_count"),
            item.optJSONObject("edge_media_to_comment")?.optLong("count") ?: item.optLong("comment_count"))
    }

    fun comments(root: JSONObject): List<Comment> {
        if (!root.has("comments")) throw ApiFailure.Incompatible()
        return root.optJSONArray("comments").objects().map {
            Comment(it.optJSONObject("user")?.optString("username").orEmpty(), it.optString("text"))
        }
    }
}
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

sealed class ApiFailure(message: String) : Exception(message) {
    class Login : ApiFailure("O Instagram exige login. Entre ou renove sua sessão.")
    class Challenge : ApiFailure("O Instagram pediu uma verificação. Abra o login oficial para concluir.")
    class RateLimit : ApiFailure("O Instagram limitou as consultas. Aguarde antes de tentar novamente.")
    class Incompatible : ApiFailure("A resposta do Instagram mudou. Este recurso precisa de atualização.")
    class Unavailable : ApiFailure("Conteúdo indisponível ou privado para esta conta.")
    class Network : ApiFailure("Não foi possível conectar. Verifique sua conexão e tente novamente.")
}

internal fun String?.httpsMediaUrl(): String? {
    if (this == null) return null
    val uri = runCatching { java.net.URI(this) }.getOrNull() ?: return null
    return takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null }
}
