import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.*
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.brunocvcunha.instagram4j.requests.payload.StatusResult
import java.io.File

const val BLACKLIST_FILE_PATH = "data/follow_blacklist"

fun main() {
    val instaPW = System.getenv("INSTAPW")
    val instaName = System.getenv("INSTANAME")
    val instagram4K = Instagram4K(instaName, instaPW)

    println("unfollowers: ${instagram4K.getUnfollowerPKs().size}")
}

class Instagram4K(instaName: String, instaPW: String) {

    private val instagram4j = Instagram4j.builder().username(instaName).password(instaPW).build()

    init {
        instagram4j.setup()
        instagram4j.login()
    }

    private val instagramUser =  getInstagramUser(instaName)


    fun getInstagramUser(name: String): InstagramUser {
        Thread.sleep(1000)
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
            unfollowByPK(it)
        }
    }

    fun unfollowByPK(pk: Long) {
        instagram4j.sendRequest(InstagramUnfollowRequest(pk))
        File(BLACKLIST_FILE_PATH).appendText("$pk,")
    }

    fun followByPK(pk: Long): StatusResult {
        return instagram4j.sendRequest(InstagramFollowRequest(pk))
    }

    // unfollows users that are unlikely to unfollow you, at least 100 followers and following at least 3x as many people as followers
    fun pruneMutualFollowers() {
        val following = getFollowing()

        following.map {
            val followinger = getInstagramUser(it.username)
            val followerRatio = followinger.follower_count / followinger.following_count.toDouble()

            println("name: ${followinger.username}, followers: ${followinger.follower_count}, ratio: $followerRatio")

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
        val myFollowerPKs = getFollowers().toList().map { it.pk }

        otherUsersFollowers.filter { !blacklist.contains(it.pk) }
            .filter { !myFollowerPKs.contains(it.pk) }
            .filter { getRatioForUser(it.username) < 0.5 }
            .map {
                println("following: ${it.pk}")
                val statusResult = followByPK(it.pk)

                if(statusResult.status.equals("fail")) {
                    throw Exception("follow call failed, are you rate limited?")
                }

                Thread.sleep(1000)
            }
            .take(numberToCopy)
            .toList()
    }
}