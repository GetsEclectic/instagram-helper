import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowersRequest
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowingRequest
import org.brunocvcunha.instagram4j.requests.InstagramSearchUsernameRequest
import org.brunocvcunha.instagram4j.requests.InstagramUnfollowRequest
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import java.io.File

const val BLACKLIST_FILE_PATH = "data/follow_blacklist"

fun main() {
    val instaPW = System.getenv("INSTAPW")
    val instaName = System.getenv("INSTANAME")
    val instagramHelper = InstagramHelper(instaName, instaPW)

    instagramHelper.unfollowUnfollowers()
}

class InstagramHelper(instaName: String, instaPW: String) {

    private val instagram4j = Instagram4j.builder().username(instaName).password(instaPW).build()

    init {
        instagram4j.setup()
        instagram4j.login()
    }

    private val instagramUser =  getInstagramUser(instaName)


    fun getInstagramUser(name: String): InstagramUser {
        return instagram4j.sendRequest(InstagramSearchUsernameRequest(name)).user
    }

    fun getFollowers(instagramUser: InstagramUser = this.instagramUser): Sequence<InstagramUserSummary> {
        println("getting followers for: ${instagramUser.username}")

        return sequence {
            var nextMaxId: String? = null

            do {
                val followersResult = instagram4j.sendRequest(InstagramGetUserFollowersRequest(instagramUser.pk, nextMaxId))

                yieldAll(followersResult.users)

                nextMaxId = followersResult.next_max_id
            } while (nextMaxId != null)
        }
    }

    fun getFollowing(): Set<InstagramUserSummary> {
        println("getting following for: ${instagramUser.username}")
        return HashSet(instagram4j.sendRequest(InstagramGetUserFollowingRequest(instagramUser.pk)).getUsers())
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
            instagram4j.sendRequest(InstagramUnfollowRequest(it))
        }

        if( unfollowerPKs.isNotEmpty() ) {
            File(BLACKLIST_FILE_PATH).appendText(unfollowerPKs.joinToString(postfix = ","))
        }
    }

    // unfollows users that are unlikely to unfollow you, at least 100 followers and following at least 3x as many people as followers
    fun pruneMutualFollowers() {
        val following = getFollowing()

        following.map {
            val followinger = instagram4j.sendRequest(InstagramSearchUsernameRequest(it.username)).user
            val followerRatio = followinger.follower_count / followinger.following_count.toDouble()

            println("name: ${followinger.username}, followers: ${followinger.follower_count}, ratio: $followerRatio")

            if(followinger.follower_count > 100 && followerRatio < 0.3) {
                println("unfollowing ${followinger.username}")
                instagram4j.sendRequest(InstagramUnfollowRequest(followinger.pk))

            }

            Thread.sleep(1000)
        }
    }

    fun getBlacklist(): HashSet<Long> {
        val blacklistString = File(BLACKLIST_FILE_PATH).readText()
        return blacklistString.split(',').map { it.toLong() }.toHashSet()
    }

    // copies followers from another user, ignoring users in the blacklist, users we are already following, and users with a ratio > 1
    fun copyFollowers(username: String) {
        val userToCopyFrom = getInstagramUser(username)
        val followersToFollow = getFollowers(userToCopyFrom)
        val blacklist = getBlacklist()

    }
}