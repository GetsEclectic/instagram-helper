import com.fasterxml.jackson.core.JsonParseException
import com.google.gson.Gson
import org.apache.http.ConnectionClosedException
import org.apache.http.client.CookieStore
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.logging.log4j.LogManager
import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.*
import org.brunocvcunha.instagram4j.requests.payload.*
import java.io.*
import java.lang.Exception
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.ZoneId
import javax.net.ssl.SSLException
import javax.net.ssl.SSLProtocolException
import kotlin.collections.HashSet

// this class holds the functions that call Instagram4J and do simple result processing
class ApiClient(instaName: String, instaPW: String): Closeable {
    private val instagram4j: Instagram4JWithTimeout
    private val database: Database = Database()
    val logger = LogManager.getLogger(javaClass)

    init {
        val secretLoginInfo = database.getSecretLoginInfo(instaName)

        if(secretLoginInfo != null) {
            val cookieStore = ObjectInputStream(secretLoginInfo.cookieStoreSerialized.inputStream()).readObject() as CookieStore
            instagram4j = Instagram4JWithTimeout(instaName, cookieStore = cookieStore, uuid = secretLoginInfo.uuid)
        } else {
            instagram4j = Instagram4JWithTimeout(instaName, instaPW)
        }
    }

    private val instagramUser =  getInstagramUser(instaName)

    override fun close() {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(instagram4j.cookieStore)
        val cookieStoreSerialized = byteArrayOutputStream.toByteArray()
        database.upsertSecretLoginInfo(instagramUser.pk, instagramUser.getUsername(), cookieStoreSerialized, instagram4j.uuid)
    }

    enum class RequestStatus {
        SUCCESS, FAIL_RATE_LIMIT, FAIL_NETWORK_EXCEPTION, FAIL_ACTION_BLOCKED
    }

    data class Instagram4JResult<T>(val statusResult: T?, val requestStatus: RequestStatus, val exception: Exception? = null)

    // returns a StatusResult if the call was successful, returns null if it was rate limited or encountered a network exception
    private fun <T: StatusResult> sendRequestWithCatchNetworkExceptions(request: InstagramRequest<T>): Instagram4JResult<T> {
        val statusResult: T?

        try {
            statusResult = instagram4j.sendRequest(request)

            if(statusResult.status == "fail" && statusResult.message.startsWith("Please wait a few minutes")) {
                return Instagram4JResult(statusResult, RequestStatus.FAIL_RATE_LIMIT)
            } else if(statusResult.status == "fail" && statusResult.feedback_message != null && statusResult.feedback_message.startsWith("This action was blocked.")) {
                return Instagram4JResult(statusResult, RequestStatus.FAIL_ACTION_BLOCKED)
            } else if(statusResult.status == "fail") {
                throw Exception("Unrecognized response: $statusResult")
            }
        } catch (e: Exception) {
            when(e) {
                is SSLProtocolException, is SocketException, is SocketTimeoutException, is ConnectionClosedException, is SSLException, is JsonParseException -> {
                    return Instagram4JResult(null, RequestStatus.FAIL_NETWORK_EXCEPTION, e)
                }
                else -> throw e
            }
        }

        return Instagram4JResult(statusResult, RequestStatus.SUCCESS)
    }

    // // sleep and retry if we got rate limited or got a socket timeout
    private fun <T: StatusResult> sendRequestWithRetry(request: InstagramRequest<T>): T {
        var instagram4JResult: Instagram4JResult<T>

        var numRateLimitFailures = 0

        Thread.sleep((3000 + (0..500).random()).toLong())

        do {
            instagram4JResult = sendRequestWithCatchNetworkExceptions(request)

            when (instagram4JResult.requestStatus) {
                RequestStatus.FAIL_RATE_LIMIT -> {
                    if(numRateLimitFailures == 3) {
                        throw Exception("too many rate limit failures")
                    }

                    val sleepTimeInMinutes = 10.toLong()
                    logger.info("got rate limited, sleeping for $sleepTimeInMinutes minutes and retrying")
                    Thread.sleep(sleepTimeInMinutes * 60 * 1000)

                    numRateLimitFailures++
                }
                RequestStatus.FAIL_NETWORK_EXCEPTION -> {
                    val sleepTimeInSeconds = 5.toLong()
                    logger.info("network issue, sleeping for $sleepTimeInSeconds seconds and retrying: " + instagram4JResult.exception.toString() )
                    Thread.sleep(sleepTimeInSeconds * 1000)
                }
                RequestStatus.FAIL_ACTION_BLOCKED -> throw Exception("instagram action was blocked!")
            }
        } while(instagram4JResult.requestStatus != RequestStatus.SUCCESS)

        return instagram4JResult.statusResult!!
    }

    fun getInstagramUser(name: String): InstagramUser {
        logger.debug("getting user info for: $name")
        return sendRequestWithRetry(InstagramSearchUsernameRequest(name)).user
    }

    fun getFollowers(instagramUser: InstagramUser = this.instagramUser): Sequence<InstagramUserSummary> {
        logger.debug("getting followers for: ${instagramUser.username}")
        return sequence {
            var nextMaxId: String? = null

            do {
                val followersResult = sendRequestWithRetry(InstagramGetUserFollowersRequest(instagramUser.pk, nextMaxId))
                yieldAll(followersResult.users)
                nextMaxId = followersResult.next_max_id
            } while (nextMaxId != null)
        }
    }

    fun getFollowing(): Set<InstagramUserSummary> {
        logger.debug("getting list of users we're following")
        return HashSet(sendRequestWithRetry(InstagramGetUserFollowingRequest(instagramUser.pk)).getUsers())
    }

    fun unfollowByPK(pk: Long): StatusResult {
        logger.debug("unfollowing: $pk")
        return sendRequestWithRetry(InstagramUnfollowRequest(pk))
    }

    fun followByPK(pk: Long): StatusResult {
        logger.debug("following: $pk")
        return sendRequestWithRetry(InstagramFollowRequest(pk))
    }

    fun getOurPK(): Long {
        return instagramUser.pk
    }

    fun getTopPostsByTag(tag: String): Sequence<InstagramFeedItem> {
        logger.debug("getting top posts for: $tag")
        return sequence {
            var nextMaxId: String? = null

            do {
                val tagFeedResult = sendRequestWithRetry(InstagramTagFeedRequest(tag, nextMaxId))
                nextMaxId = if(tagFeedResult.ranked_items != null) {
                    yieldAll(tagFeedResult.ranked_items)
                    tagFeedResult.next_max_id
                } else {
                    null
                }
            } while (nextMaxId != null)
        }
    }

    fun getLikersByMediaId(mediaId: Long): List<InstagramUserSummary> {
        logger.debug("getting likers for: $mediaId")
        return sendRequestWithRetry(InstagramGetMediaLikersRequest(mediaId)).users
    }

    fun getUserFeed(userPK: Long = instagramUser.pk): Sequence<InstagramFeedItem> {
        logger.debug("getting user feed for: $userPK")
        val minTimestamp = LocalDateTime.now().minusDays(30).atZone(ZoneId.of("UTC-05:00")).toInstant().toEpochMilli() / 1000
        val maxTimestamp = LocalDateTime.now().plusDays(1).atZone(ZoneId.of("UTC-05:00")).toInstant().toEpochMilli() / 1000

        return sequence {
            var nextMaxId: String? = null

            do {
                val userFeedResult = sendRequestWithRetry(InstagramUserFeedRequest(userPK, nextMaxId, minTimestamp, maxTimestamp))
                yieldAll(userFeedResult.items)
                nextMaxId = userFeedResult.next_max_id
            } while (nextMaxId != null)
        }
    }

    fun getOurUsername(): String {
        return instagramUser.getUsername()
    }

    fun getMediaCountForTag(tag: String): Int {
        val searchResult = sendRequestWithRetry(InstagramSearchTagsRequest(tag))
        val theTag = searchResult.results.filter { it.name == tag }
        return if(theTag.isNotEmpty()) {
            theTag[0].media_count
        } else {
            0
        }
    }

    fun likeMedia(mediaId: Long): StatusResult {
        return sendRequestWithRetry(InstagramLikeRequest(mediaId))
    }
    // iterating doesn't work properly, always returns the same thing
    fun getCommentsForMedia(mediaId: Long): Sequence<InstagramComment> {
        return sequence {
            var nextMaxId: String? = null

            do {
                val commentsResult = sendRequestWithRetry(InstagramGetMediaCommentsRequest(mediaId.toString(), nextMaxId))
                yieldAll(commentsResult.comments.reversed())
                nextMaxId = Gson().fromJson(commentsResult.next_max_id, InstagramGetMediaCommentsResultNextMaxId::class.java).server_cursor
                println("nextMaxId: $nextMaxId")
            } while (nextMaxId != null)
        }
    }

    data class InstagramGetMediaCommentsResultNextMaxId(val server_cursor: String)

//    fun getOurTimeline(): Sequence<InstagramFeedItem> {
//        return sequence {
//            var nextMaxId: String? = null
//            var clientSessionId: String = UUID.randomUUID().toString()
//
//            do {
//                val userTimelineResult = sendRequestWithRetry(InstagramTimelineFeedRequest(nextMaxId, clientSessionId))
//                yieldAll(userTimelineResult.items)
//                nextMaxId = userTimelineResult.next_max_id
//                clientSessionId = userTimelineResult.client_session_id
//                logger.error("nextMaxId: $nextMaxId, clientSessionId: $clientSessionId")
//            } while (nextMaxId != null)
//        }
//    }
}

// Instagram4j with a 30 second socket timeout on the http client
class Instagram4JWithTimeout(username: String, password: String = "fakepassword", cookieStore: CookieStore? = null, uuid: String? = null): Instagram4j(username, password) {
    init {
        if(cookieStore != null) {
            this.cookieStore = cookieStore
            this.uuid = uuid
            this.isLoggedIn = true
        }

        setup()

        val requestConfig =
            RequestConfig.custom().setSocketTimeout(30000)
                .build()
        val builder = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig)

        if (proxy != null) {
            builder.setProxy(proxy)
        }

        if(credentialsProvider != null) {
            builder.setDefaultCredentialsProvider(credentialsProvider)
        }

        builder.setDefaultCookieStore(this.cookieStore)

        this.client = builder.build()

        if(!isLoggedIn) {
            login()
        }
    }
}