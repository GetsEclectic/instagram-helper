import org.apache.http.impl.client.HttpClientBuilder
import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.*
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.brunocvcunha.instagram4j.requests.payload.StatusResult
import java.io.File
import org.apache.http.client.config.RequestConfig
import javax.net.ssl.SSLProtocolException


const val BLACKLIST_FILE_PATH = "data/follow_blacklist"

class Instagram4K(instaName: String, instaPW: String) {

    private val instagram4j = Instagram4jWithTimeout(instaName, instaPW)

    private val instagramUser =  getInstagramUser(instaName)


    fun getInstagramUser(name: String): InstagramUser {
        Thread.sleep(1000)
        return sendRequestWithRetry(InstagramSearchUsernameRequest(name)).user
    }

    fun getFollowers(instagramUser: InstagramUser = this.instagramUser): Sequence<InstagramUserSummary> {
        println("getting followers for: ${instagramUser.username}")

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
        println("getting following for: ${instagramUser.username}")
        return HashSet(sendRequestWithRetry(InstagramGetUserFollowingRequest(instagramUser.pk)).getUsers())
    }

    fun getUnfollowerPKs(): List<Long> {
        val followerPKs = getFollowers().map { it.pk }.toHashSet()
        println("followers size: ${followerPKs.size}")

        val followingPKs = getFollowing().map { it.pk }
        println("following size: ${followingPKs.size}")

        val unfollowerPKs = followingPKs.minus(followerPKs)
        println("unfollowers size: ${unfollowerPKs.size}")

        return unfollowerPKs
    }

    // unfollows users that aren't following you
    fun unfollowUnfollowers() {
        val unfollowerPKs = getUnfollowerPKs()

        unfollowerPKs.map {
            println("unfollowing: $it")
            unfollowByPK(it)
        }
    }

    fun unfollowByPK(pk: Long): StatusResult {
        val statusResult = sendRequestWithRetry(InstagramUnfollowRequest(pk))
        File(BLACKLIST_FILE_PATH).appendText("$pk,")
        return statusResult
    }

    fun followByPK(pk: Long): StatusResult {
        return sendRequestWithRetry(InstagramFollowRequest(pk))
    }

    enum class RequestStatus {
        SUCCESS, FAIL_RATE_LIMIT, FAIL_SOCKET_TIMEOUT
    }

    data class Instagram4JResult<T>(val statusResult: T?, val requestStatus: RequestStatus)

    // // sleep and retry if we got rate limited or got a socket timeout
    fun <T: StatusResult> sendRequestWithRetry(request: InstagramRequest<T>): T {
        var instagram4JResult: Instagram4JResult<T>

        do {
            instagram4JResult = sendRequestWithCatchSSLProtocolException(request)

            if(instagram4JResult.requestStatus == RequestStatus.FAIL_RATE_LIMIT) {
                val sleepTimeInMinutes = 10.toLong()
                println("got rate limited, sleeping for $sleepTimeInMinutes minutes and retrying")
                Thread.sleep(sleepTimeInMinutes * 60 * 1000)
            } else if (instagram4JResult.requestStatus == RequestStatus.FAIL_SOCKET_TIMEOUT) {
                println("socket timeout, retrying")
            }
        } while(instagram4JResult.requestStatus != RequestStatus.SUCCESS)

        return instagram4JResult.statusResult!!
    }

    // returns a StatusResult if the call was successful, returns null if it was rate limited or encountered an exception
    fun <T: StatusResult> sendRequestWithCatchSSLProtocolException(request: InstagramRequest<T>): Instagram4JResult<T> {
        val statusResult: T?

        try {
            statusResult = instagram4j.sendRequest(request)

            if(statusResult.status.equals("fail") && statusResult.message.startsWith("Please wait a few minutes")) {
                return Instagram4JResult(statusResult, RequestStatus.FAIL_RATE_LIMIT)
            }
        } catch (e: SSLProtocolException) {
            return Instagram4JResult(null, RequestStatus.FAIL_SOCKET_TIMEOUT)
        }

        return Instagram4JResult(statusResult, RequestStatus.SUCCESS)
    }

    // unfollows users that are unlikely to unfollow you, at least 100 followers and following at least 3x as many people as followers
    fun pruneMutualFollowers() {
        val followingMap = getFollowing().associateBy({it.pk}, {it})
        val followersMap = getFollowers().associateBy({it.pk}, {it})

        // filter by intersecting keys
        val mutualFollowersMap = followingMap.filterKeys { followersMap.containsKey(it) }

        println("mutual followers: ${mutualFollowersMap.size}")

        mutualFollowersMap.map {
            val followinger = getInstagramUser(it.value.username)
            val followerRatio = followinger.follower_count / followinger.following_count.toDouble()

            if(followinger.follower_count > 100 && followerRatio < 0.3) {
                println("unfollowing ${followinger.username}")
                unfollowByPK(followinger.pk)
            }

            Thread.sleep(1000)
        }
    }

    fun getBlacklist(): HashSet<Long> {
        val blacklistString = File(BLACKLIST_FILE_PATH).readText()
        // always ends in a comma, so drop the last item
        return blacklistString.split(',').dropLast(1).map { it.toLong() }.toHashSet()
    }

    fun getRatioForUser(username: String): Double {
        val user = getInstagramUser(username)
        return user.follower_count / user.following_count.toDouble()
    }

    // copies followers from another user, ignoring:
    //     users in the blacklist
    //     users we are already following
    //     users with a ratio > 0.5
    fun copyFollowers(username: String, numberToCopy: Int = 200) {
        val userToCopyFrom = getInstagramUser(username)
        val otherUsersFollowers = getFollowers(userToCopyFrom)

        val blacklist = getBlacklist()
        val myFollowingPKs = getFollowing().toList().map { it.pk }

        otherUsersFollowers.filter { !blacklist.contains(it.pk) }
            .filter { !myFollowingPKs.contains(it.pk) }
            .filter { getRatioForUser(it.username) < 0.5 }
            .map {
                println("following: ${it.pk}")
                followByPK(it.pk)
                Thread.sleep(1000)
            }
            .take(numberToCopy)
            .toList()
    }
}

// Instagram4j with a 30 second socket timeout on the http client
class Instagram4jWithTimeout(username: String, password: String): Instagram4j(username, password) {
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