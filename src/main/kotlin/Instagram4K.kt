

class Instagram4K(private val apiClient: ApiClient, private val database: Database = Database()) {
    constructor(instaName: String, instaPW: String) : this(ApiClient(instaName, instaPW))

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

        unfollowerPKs.filter { !database.getWhitelist().contains(it) }
            .map {
            println("unfollowing: $it")
            apiClient.unfollowByPK(it)
        }
    }

    // follows a user and adds them to the whitelist, so they are never automatically unfollowed
    fun followAndAddToWhitelist(username: String) {
        println("whitelisting: $username")
        val pk_to_whitelist = apiClient.getInstagramUser(username).pk
        apiClient.followByPK(pk_to_whitelist)
        database.addToWhitelist(apiClient.getOurPK(), pk_to_whitelist, Database.WHITELIST_REASONS.MANUAL)
    }

    // unfollows users that are unlikely to unfollow you, at least 100 followers and following at least 3x as many people as followers
    fun pruneMutualFollowers() {
        val followingMap = apiClient.getFollowing().associateBy({it.pk}, {it})
        val followersMap = apiClient.getFollowers().associateBy({it.pk}, {it})

        // filter by intersecting keys
        val mutualFollowersMap = followingMap.filterKeys { followersMap.containsKey(it) }

        println("mutual followers: ${mutualFollowersMap.size}")

        mutualFollowersMap.filter { !database.getWhitelist().contains(it.value.pk) }
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

        val blacklist = database.getBlacklist()
        val myFollowingPKs = apiClient.getFollowing().toList().map { it.pk }

        otherUsersFollowers.filter { !blacklist.contains(it.pk) }
            .filter { !myFollowingPKs.contains(it.pk) }
            .map {
                // blacklist everyone we scan, saves us from having to calculate a ratio every time we see them
                database.addToBlacklist(apiClient.getOurPK(), it.pk)
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