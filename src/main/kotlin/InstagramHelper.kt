import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowersRequest
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowingRequest
import org.brunocvcunha.instagram4j.requests.InstagramSearchUsernameRequest
import org.brunocvcunha.instagram4j.requests.InstagramUnfollowRequest
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import java.io.File

fun main(args: Array<String>) {
    val instaPW = System.getenv("INSTAPW")
    val instaName = System.getenv("INSTANAME")
    val instagramHelper = InstagramHelper(instaName, instaPW)

    val extendedInstagramUser = instagramHelper.getUser(instaName)

    extendedInstagramUser.pruneMutualFollowers()
}

class InstagramHelper(val instaName: String, val instaPW: String) {
    private fun login(): Instagram4j {
        val instagram4j = Instagram4j.builder().username(instaName).password(instaPW).build()
        instagram4j.setup()
        instagram4j.login()
        return instagram4j
    }

    private val instagram4j = login()

    fun getUser(userName: String): ExtendedInstagramUser {
        println("getting user: $userName")
        val instagramUser =  instagram4j.sendRequest(InstagramSearchUsernameRequest(userName)).user
        return ExtendedInstagramUser(instagramUser, instagram4j)
    }
}

class ExtendedInstagramUser(val instagramUser: InstagramUser, val instagram4j: Instagram4j) {
    fun getFollowers(): Set<InstagramUserSummary> {
        println("getting followers for: ${instagramUser.username}")
        val followersSet = HashSet<InstagramUserSummary>()
        var nextMaxId: String? = null

        while (true) {
            val followersResult = instagram4j.sendRequest(InstagramGetUserFollowersRequest(instagramUser.pk, nextMaxId))
            followersSet.addAll(followersResult.users)
            nextMaxId = followersResult.next_max_id

            if (nextMaxId == null) {
                break
            }
        }

        return followersSet
    }

    fun getFollowing(): Set<InstagramUserSummary> {
        println("getting following for: ${instagramUser.username}")
        return HashSet(instagram4j.sendRequest(InstagramGetUserFollowingRequest(instagramUser.pk)).getUsers())
    }

    fun getUnfollowerPKs(): List<Long> {
        val followerPKs = getFollowers().map { it.pk }
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

        File("data/follow_blacklist").appendText(unfollowerPKs.joinToString(postfix = ","))
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
}