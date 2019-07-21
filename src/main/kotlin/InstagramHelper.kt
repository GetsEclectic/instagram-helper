import org.brunocvcunha.instagram4j.Instagram4j
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowersRequest
import org.brunocvcunha.instagram4j.requests.InstagramGetUserFollowingRequest
import org.brunocvcunha.instagram4j.requests.InstagramSearchUsernameRequest
import org.brunocvcunha.instagram4j.requests.InstagramUnfollowRequest
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary

fun main(args: Array<String>) {
    val instaPW = System.getenv("INSTAPW")
    val instaName = System.getenv("INSTANAME")
    val instagramHelper = InstagramHelper(instaName, instaPW)

    val extendedInstagramUser = instagramHelper.getUser(instaName)

    extendedInstagramUser.unfollowUnfollowers()
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

class ExtendedInstagramUser(val instagramUser: InstagramUser, private val instagram4j: Instagram4j) {
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
        val followerPKs = getFollowers().map { it -> it.pk }
        println("followers size: ${followerPKs.size}")

        val followingPKs = getFollowing().map { it -> it.pk }
        println("following size: ${followingPKs.size}")

        val unfollowerPKs = followingPKs.minus(followerPKs)
        println("unfollowers size: ${unfollowerPKs.size}")

        return unfollowerPKs
    }

    fun unfollowUnfollowers() {
        val unfollowerPKs = getUnfollowerPKs()

        unfollowerPKs.map { it -> instagram4j.sendRequest(InstagramUnfollowRequest(it)) }
    }
}