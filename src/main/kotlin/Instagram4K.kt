import java.io.File


const val BLACKLIST_FILE_PATH = "data/follow_blacklist"
const val WHITELIST_FILE_PATH = "data/unfollow_whitelist"

class Instagram4K(instaName: String, instaPW: String) {
    private val apiClient = ApiClient(instaName, instaPW)

    fun getUnfollowerPKs(): List<Long> {
        val followerPKs = apiClient.getFollowers().map { it.pk }.toHashSet()
        println("followers size: ${followerPKs.size}")

        val followingPKs = apiClient.getFollowing().map { it.pk }
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
            apiClient.unfollowByPK(it)
        }
    }

    fun addToBlacklist(pk: Long) {
        File(BLACKLIST_FILE_PATH).appendText("$pk,")
    }

    // follows a user and adds them to the whitelist, so they are never automatically unfollowed
    fun addToWhitelist(username: String) {
        println("whitelisting: $username")
        val pk = apiClient.getInstagramUser(username).pk
        apiClient.followByPK(pk)
        File(WHITELIST_FILE_PATH).appendText("$pk,")
    }

    // unfollows users that are unlikely to unfollow you, at least 100 followers and following at least 3x as many people as followers
    fun pruneMutualFollowers() {
        val followingMap = apiClient.getFollowing().associateBy({it.pk}, {it})
        val followersMap = apiClient.getFollowers().associateBy({it.pk}, {it})

        // filter by intersecting keys
        val mutualFollowersMap = followingMap.filterKeys { followersMap.containsKey(it) }

        println("mutual followers: ${mutualFollowersMap.size}")

        val whitelist = getWhitelist()

        mutualFollowersMap.filter { !whitelist.contains(it.value.pk) }
            .map {
                val followinger = apiClient.getInstagramUser(it.value.username)
                val followerRatio = followinger.follower_count / followinger.following_count.toDouble()

                if(followinger.follower_count > 100 && followerRatio < 0.3) {
                    println("unfollowing ${followinger.username}")
                    apiClient.unfollowByPK(followinger.pk)
                }

                Thread.sleep(1000)
            }
    }

    fun getBlacklist(): HashSet<Long> {
        val blacklistString = File(BLACKLIST_FILE_PATH).readText()
        // always ends in a comma, so drop the last item
        return blacklistString.split(',').dropLast(1).map { it.toLong() }.toHashSet()
    }

    fun getWhitelist(): HashSet<Long> {
        val whitelistString = File(WHITELIST_FILE_PATH).readText()
        // always ends in a comma, so drop the last item
        return whitelistString.split(',').dropLast(1).map { it.toLong() }.toHashSet()
    }

    fun getRatioForUser(username: String): Double {
        val user = apiClient.getInstagramUser(username)
        return user.follower_count / user.following_count.toDouble()
    }

    // copies followers from another user, ignoring:
    //     users in the blacklist
    //     users we are already following
    //     users with a ratio > 0.5
    fun copyFollowers(username: String, numberToCopy: Int = 200) {
        val userToCopyFrom = apiClient.getInstagramUser(username)
        val otherUsersFollowers = apiClient.getFollowers(userToCopyFrom)

        val blacklist = getBlacklist()
        val myFollowingPKs = apiClient.getFollowing().toList().map { it.pk }

        otherUsersFollowers.filter { !blacklist.contains(it.pk) }
            .filter { !myFollowingPKs.contains(it.pk) }
            .map {
                // blacklist everyone we scan, saves us from having to calculate a ratio every time we see them
                addToBlacklist(it.pk)
                it
            }
            .filter { getRatioForUser(it.username) < 0.5 }
            .map {
                println("following: ${it.pk}")
                apiClient.followByPK(it.pk)
                Thread.sleep(1000)
            }
            .take(numberToCopy)
            .toList()
    }
}