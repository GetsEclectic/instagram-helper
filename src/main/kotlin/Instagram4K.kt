import org.apache.logging.log4j.LogManager
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary

class Instagram4K(val apiClient: ApiClient, private val database: Database = Database()) {
    constructor(instaName: String, instaPW: String) : this(ApiClient(instaName, instaPW))

    val logger = LogManager.getLogger(javaClass)

    fun getUnfollowerPKs(): List<Long> {
        val followerPKs = apiClient.getFollowers().map { it.pk }.toHashSet()
        logger.info("followers size: ${followerPKs.size}")

        val followingPKs = apiClient.getFollowing().map { it.pk }
        logger.info("following size: ${followingPKs.size}")

        val unfollowerPKs = followingPKs.minus(followerPKs)
        logger.info("unfollowers size: ${unfollowerPKs.size}")

        return unfollowerPKs
    }

    // unfollows users that aren't following you
    fun unfollowUnfollowers() {
        val unfollowerPKs = getUnfollowerPKs()

        val whitelist = database.getWhitelist(apiClient.getOurPK(), Database.WHITELIST_REASONS.MANUAL)

        unfollowerPKs.filter { !whitelist.contains(it) }
            .map {
                logger.info("unfollowing: $it")
            apiClient.unfollowByPK(it)
                Thread.sleep(1000)
        }
    }

    // follows a user and adds them to the whitelist, so they are never automatically unfollowed
    fun followAndAddToWhitelist(username: String) {
        logger.info("whitelisting: $username")
        val pk_to_whitelist = apiClient.getInstagramUser(username).pk
        apiClient.followByPK(pk_to_whitelist)
        database.addToWhitelist(apiClient.getOurPK(), pk_to_whitelist, Database.WHITELIST_REASONS.MANUAL)
    }

    // finds mutual followers and calls unfollowUserUnlikelyToUnfollowBack on the ones that aren't whitelisted
    fun pruneMutualFollowers() {
        val followingMap = apiClient.getFollowing().associateBy({it.pk}, {it})
        val followersMap = apiClient.getFollowers().associateBy({it.pk}, {it})

        // filter by intersecting keys
        val mutualFollowersMap = followingMap.filterKeys { followersMap.containsKey(it) }

        logger.info("mutual followers: ${mutualFollowersMap.size}")

        val whitelist = database.getWhitelist(apiClient.getOurPK())

        mutualFollowersMap.filter { !whitelist.contains(it.value.pk) }
            .map {
                database.addToWhitelist(apiClient.getOurPK(), it.value.pk, Database.WHITELIST_REASONS.SCANNED_WHEN_PRUNING)
                unfollowUserUnlikelyToUnfollowBack(it.value)
            }
    }

    // unfollows users that are unlikely to unfollow you, at least 100 followers and following at least 3x as many people as followers
    fun unfollowUserUnlikelyToUnfollowBack(user: InstagramUserSummary) {
        val followinger = apiClient.getInstagramUser(user.username)
        val followerRatio = followinger.follower_count / followinger.following_count.toDouble()

        if(followinger.follower_count > 100 && followerRatio < 0.3) {
            logger.info("unfollowing ${followinger.username}")
            apiClient.unfollowByPK(followinger.pk)
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

        followGoodUsers(otherUsersFollowers, numberToCopy)
    }

    fun followLikersOfTopPostsForTag(tag: String, numberToCopy: Int = 200 ) {
        val topPosts = apiClient.getTopPostsByTag(tag)
        val likers = topPosts.flatMap { apiClient.getLikersByMediaId(it.pk).asSequence() }

        followGoodUsers(likers, numberToCopy)
    }

    // iterates through a sequence of InstagramUserSummarys and follows users:
    //      not in the blacklist
    //      not already being followed by us
    //      with a ratio < 0.5
    private fun followGoodUsers(users: Sequence<InstagramUserSummary>, numberToFollow: Int) {
        val blacklist = database.getBlacklist(apiClient.getOurPK())
        val myFollowingPKs = apiClient.getFollowing().toList().map { it.pk }

        users.filter { !blacklist.contains(it.pk) }
            .filter { !myFollowingPKs.contains(it.pk) }
            .map {
                // blacklist everyone we scan, saves us from having to calculate a ratio every time we see them
                database.addToBlacklist(apiClient.getOurPK(), it.pk)
                it
            }
            .filter { getRatioForUser(it.username) < 0.5 }
            .map {
                logger.info("following: ${it.username}")
                apiClient.followByPK(it.pk)
                database.recordFollowRequest(apiClient.getOurPK(), it.pk, it.username)
                Thread.sleep(1000)
            }
            .take(numberToFollow)
            .toList()
    }
}