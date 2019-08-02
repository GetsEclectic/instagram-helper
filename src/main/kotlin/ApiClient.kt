import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.logging.log4j.LogManager
import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.*
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.brunocvcunha.instagram4j.requests.payload.StatusResult
import java.lang.Exception
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLProtocolException

// this class holds the functions that call Instagram4J and do simple result processing
class ApiClient(instaName: String, instaPW: String) {
    private val instagram4j = Instagram4JWithTimeout(instaName, instaPW)
    private val instagramUser =  getInstagramUser(instaName)

    val logger = LogManager.getLogger(javaClass)

    enum class RequestStatus {
        SUCCESS, FAIL_RATE_LIMIT, FAIL_NETWORK_EXCEPTION
    }

    data class Instagram4JResult<T>(val statusResult: T?, val requestStatus: RequestStatus)

    // returns a StatusResult if the call was successful, returns null if it was rate limited or encountered a network exception
    private fun <T: StatusResult> sendRequestWithCatchNetworkExceptions(request: InstagramRequest<T>): Instagram4JResult<T> {
        val statusResult: T?

        try {
            statusResult = instagram4j.sendRequest(request)

            if(statusResult.status == "fail" && statusResult.message.startsWith("Please wait a few minutes")) {
                return Instagram4JResult(statusResult, RequestStatus.FAIL_RATE_LIMIT)
            }
        } catch (e: Exception) {
            when(e) {
                is SSLProtocolException, is SocketException, is SocketTimeoutException -> {
                    return Instagram4JResult(null, RequestStatus.FAIL_NETWORK_EXCEPTION)
                }
                else -> throw e
            }
        }

        return Instagram4JResult(statusResult, RequestStatus.SUCCESS)
    }

    // // sleep and retry if we got rate limited or got a socket timeout
    private fun <T: StatusResult> sendRequestWithRetry(request: InstagramRequest<T>): T {
        var instagram4JResult: Instagram4JResult<T>

        do {
            instagram4JResult = sendRequestWithCatchNetworkExceptions(request)

            if(instagram4JResult.requestStatus == RequestStatus.FAIL_RATE_LIMIT) {
                val sleepTimeInMinutes = 10.toLong()
                logger.info("got rate limited, sleeping for $sleepTimeInMinutes minutes and retrying")
                Thread.sleep(sleepTimeInMinutes * 60 * 1000)
            } else if (instagram4JResult.requestStatus == RequestStatus.FAIL_NETWORK_EXCEPTION) {
                val sleepTimeInSeconds = 5.toLong()
                logger.info("network issue, sleeping for $sleepTimeInSeconds seconds and retrying")
                Thread.sleep(sleepTimeInSeconds * 1000)
            }
        } while(instagram4JResult.requestStatus != RequestStatus.SUCCESS)

        return instagram4JResult.statusResult!!
    }

    fun getInstagramUser(name: String): InstagramUser {
        Thread.sleep(1000)
        return sendRequestWithRetry(InstagramSearchUsernameRequest(name)).user
    }

    fun getFollowers(instagramUser: InstagramUser = this.instagramUser): Sequence<InstagramUserSummary> {
        logger.info("getting followers for: ${instagramUser.username}")

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
        logger.info("getting following for: ${instagramUser.username}")
        return HashSet(sendRequestWithRetry(InstagramGetUserFollowingRequest(instagramUser.pk)).getUsers())
    }

    fun unfollowByPK(pk: Long): StatusResult {
        val statusResult = sendRequestWithRetry(InstagramUnfollowRequest(pk))
        return statusResult
    }

    fun followByPK(pk: Long): StatusResult {
        return sendRequestWithRetry(InstagramFollowRequest(pk))
    }

    fun getOurPK(): Long {
        return instagramUser.pk
    }
}

// Instagram4j with a 30 second socket timeout on the http client
class Instagram4JWithTimeout(username: String, password: String): Instagram4j(username, password) {
    init {
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

        login()
    }
}