package me.barmural.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.barmural.data.*

data class MuralState(
    val profile: Profile? = null, val posts: List<Post> = emptyList(), val cursor: String? = null,
    val loading: Boolean = false, val error: String? = null, val hasSession: Boolean = false,
    val favorites: List<String> = emptyList(), val comments: List<Comment>? = null,
    val commentsLoading: Boolean = false, val commentsError: String? = null,
    val selectedPost: Post? = null, val timeline: Boolean = false
)
class MuralViewModel(private val api: InstagramApi, private val session: SessionStore) : ViewModel() {
    private val mutable = MutableStateFlow(MuralState(favorites = session.favorites()))
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var commentRequest: Job? = null
    fun refreshSession() { session.refresh(); mutable.value = mutable.value.copy(hasSession = InstagramApi.hasSession(session.header())) }
    fun openProfile(username: String) {
        request?.cancel()
        mutable.value = mutable.value.copy(profile = null, posts = emptyList(), cursor = null, timeline = false, loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val content = api.profileContent(username)
                mutable.value = mutable.value.copy(profile = content.profile)
                val page = content.initialPosts ?: api.userPosts(content.profile.id)
                mutable.value = mutable.value.copy(posts = page.posts, cursor = page.cursor, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, error = e.message ?: "Falha de conexão") }
        }
    }
    fun timeline() {
        request?.cancel()
        mutable.value = mutable.value.copy(profile = null, posts = emptyList(), cursor = null, timeline = true, loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = api.timeline()
                mutable.value = mutable.value.copy(posts = page.posts, cursor = page.cursor, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, error = e.message) }
        }
    }
    fun next() {
        val before = mutable.value
        val cursor = before.cursor ?: return
        if (before.loading) return
        mutable.value = before.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val page = if (before.timeline) api.timeline(cursor) else api.userPosts(before.profile!!.id, cursor)
                mutable.value = mutable.value.copy(posts = (before.posts + page.posts).distinctBy(Post::id),
                    cursor = page.cursor.takeUnless { it == cursor }, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, error = e.message) }
        }
    }
    fun toggleFavorite() {
        mutable.value.profile?.let { session.toggleFavorite(it.username) }
        mutable.value = mutable.value.copy(favorites = session.favorites())
    }
    fun selectPost(post: Post?) {
        commentRequest?.cancel()
        mutable.value = mutable.value.copy(selectedPost = post, comments = null, commentsError = null, commentsLoading = false)
    }
    fun loadComments() {
        val post = mutable.value.selectedPost ?: return
        if (mutable.value.commentsLoading) return
        mutable.value = mutable.value.copy(commentsLoading = true, commentsError = null)
        commentRequest = viewModelScope.launch {
            try { val comments = api.comments(post.id); mutable.value = mutable.value.copy(comments = comments, commentsLoading = false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(commentsLoading = false, commentsError = e.message) }
        }
    }
    fun logout() {
        request?.cancel(); commentRequest?.cancel()
        mutable.value = MuralState(favorites = session.favorites(), loading = true)
        request = viewModelScope.launch { session.clear(); mutable.value = MuralState(favorites = session.favorites()) }
    }
}
