package me.barmural.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class InstagramApiTest {
    private lateinit var server: MockWebServer
    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun close() { server.shutdown() }
    private fun api(cookie: String = "") = InstagramApi(SessionCookies { cookie }, "test-agent", server.url("/"))

    @Test fun anonymousProfileDoesNotInventCredentials() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"user":{"id":"123","username":"alice","full_name":"Alice","is_private":false,"edge_followed_by":{"count":7}}}}"""))
        val profile = api().profile("@alice")
        assertEquals("123", profile.id)
        assertEquals(7L, profile.followers)
        val request = server.takeRequest()
        assertEquals("/api/v1/users/web_profile_info/?username=alice", request.path)
        assertNull(request.getHeader("Cookie"))
    }
    @Test fun cookieAndCsrfTravelOnlyToApiOrigin() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[],"more_available":false}"""))
        api("sessionid=secret=value; csrftoken=csrf").timeline()
        val request = server.takeRequest()
        assertEquals("sessionid=secret=value; csrftoken=csrf", request.getHeader("Cookie"))
        assertEquals("csrf", request.getHeader("X-CSRFToken"))
        assertEquals("test-agent", request.getHeader("User-Agent"))
    }
    @Test fun timelineRequiresSessionBeforeMakingNetworkRequest() = runTest {
        try { api().timeline(); fail("Expected login error") } catch (_: ApiFailure.Login) {}
        assertEquals(0, server.requestCount)
    }
    @Test fun rateLimitIsNotRetried() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        try { api().profile("alice"); fail("Expected rate limit") } catch (_: ApiFailure.RateLimit) {}
        assertEquals(1, server.requestCount)
    }
    @Test fun checkpointHasActionableError() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"challenge_required","challenge":{"url":"https://www.instagram.com/challenge/"}}"""))
        try { api().profile("alice"); fail("Expected challenge") } catch (_: ApiFailure.Challenge) {}
    }
    @Test fun redirectsAreNeverFollowedWithSession() = runTest {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", other.url("/collect")))
            try { api("sessionid=private").profile("alice"); fail("Expected login") } catch (_: ApiFailure.Login) {}
            assertEquals(0, other.requestCount)
        }
    }
    @Test fun htmlLoginResponseDoesNotBecomeEmptyFeed() = runTest {
        server.enqueue(MockResponse().setBody("<html>Login</html>"))
        try { api().profile("alice"); fail("Expected incompatible response") } catch (_: ApiFailure.Incompatible) {}
    }
    @Test fun cursorIsUrlEncodedAndServerPaginationPreserved() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[],"next_max_id":"next/cursor=1","more_available":true}"""))
        val result = api().userPosts("123", "a&b")
        assertEquals("next/cursor=1", result.cursor)
        assertEquals("a&b", server.takeRequest().requestUrl!!.queryParameter("max_id"))
    }
    @Test fun badUsernameDoesNotMakeRequest() = runTest {
        try { api().profile("https://evil.test"); fail("Expected invalid input") } catch (_: IllegalArgumentException) {}
        assertEquals(0, server.requestCount)
    }
    @Test fun expiredSessionIsExplicit() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"login_required"}"""))
        try { api().profile("alice"); fail("Expected login") } catch (_: ApiFailure.Login) {}
    }
    @Test fun malformedSuccessIsNotSilentlyAccepted() {
        try { InstagramJson.page(JSONObject("{}")); fail("Expected incompatible") } catch (_: ApiFailure.Incompatible) {}
    }
    @Test fun carouselAndSponsoredEntriesAreHandled() {
        val result = InstagramJson.page(JSONObject("""{
            "items":[
              {"pk":"1","user":{"username":"alice"},"caption":{"text":"hello"},"carousel_media":[
                {"image_versions2":{"candidates":[{"url":"https://cdn.test/1.jpg"}]}},
                {"image_versions2":{"candidates":[{"url":"https://cdn.test/2.jpg"}]},"video_versions":[{"url":"https://cdn.test/2.mp4"}]}
              ]},
              {"pk":"2","ad_id":"sponsor","user":{"username":"advertiser"}}
            ],"more_available":false} """))
        assertEquals(1, result.posts.size)
        assertEquals(2, result.posts.first().assets.size)
        assertEquals("https://cdn.test/2.mp4", result.posts.first().assets[1].video)
        assertNull(result.cursor)
    }
    @Test fun graphqlProfileMediaCanBeDecoded() {
        val result = InstagramJson.page(JSONObject("""{"data":{"user":{"edge_owner_to_timeline_media":{
          "edges":[{"node":{"id":"9","owner":{"username":"alice"},"display_url":"https://cdn.test/x.jpg","edge_media_to_caption":{"edges":[{"node":{"text":"caption"}}]}}}],
          "page_info":{"has_next_page":true,"end_cursor":"cursor"}
        }}}}"""))
        assertEquals("caption", result.posts.single().caption)
        assertEquals("cursor", result.cursor)
    }
    @Test fun cookieParserRequiresExactNameAndKeepsEquals() {
        assertFalse(InstagramApi.hasSession("not_sessionid=x"))
        assertFalse(InstagramApi.hasSession("sessionid="))
        assertEquals("abc=def", InstagramApi.cookieValue("foo=x; sessionid=abc=def; csrftoken=y", "sessionid"))
    }
    @Test fun mediaNeverLoadsLocalFilesOrCleartext() {
        assertNull("file:///data/data/me.barmural/secrets".httpsMediaUrl())
        assertNull("content://private/photo".httpsMediaUrl())
        assertNull("http://cdn.test/photo".httpsMediaUrl())
        assertNull("https://user:pass@cdn.test/photo".httpsMediaUrl())
        assertEquals("https://cdn.test/photo", "https://cdn.test/photo".httpsMediaUrl())
    }
    @Test fun embeddedProfilePostsDoNotReuseGraphqlCursorAsV1Cursor() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"user":{"id":"1","username":"alice","edge_owner_to_timeline_media":{"edges":[],"page_info":{"has_next_page":true,"end_cursor":"graphql-only"}}}}}"""))
        val content = api().profileContent("alice")
        assertNotNull(content.initialPosts)
        assertNull(content.initialPosts!!.cursor)
        assertEquals(1, server.requestCount)
    }
}
