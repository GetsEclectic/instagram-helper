import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.logging.log4j.LogManager
import org.brunocvcunha.instagram4j.requests.payload.InstagramFeedItem
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import java.io.Closeable
import java.lang.Exception
import java.util.regex.Pattern

class Instagram4K(val apiClient: ApiClient, val database: Database = Database()): Closeable {
    override fun close() {
        apiClient.close()
    }

    constructor(instaName: String, instaPW: String) : this(ApiClient(instaName, instaPW))

    val logger = LogManager.getLogger(javaClass)
    val autoremoteClient = AutoremoteClient(apiClient.getOurUsername())

    init {
        logger.info("logged in as ${apiClient.getOurUsername()}")
    }

    fun getUnfollowerPKs(): List<Long> {
        val followerPKs = apiClient.getFollowers().map { it.pk }.toHashSet()
        val followingPKs = apiClient.getFollowing().map { it.pk }
        val unfollowerPKs = followingPKs.minus(followerPKs)
        logger.info("unfollowers size: ${unfollowerPKs.size}")

        return unfollowerPKs
    }

    // unfollows users that aren't following you
    fun unfollowUnfollowers(numToUnfollow: Int = 100) {
        logger.info("unfollowing unfollowers")
        try {
            val unfollowerPKs = getUnfollowerPKs()

            val whitelist = database.getWhitelist(apiClient.getOurPK(), Database.WhitelistReason.MANUAL)

            unfollowerPKs.filter { !whitelist.contains(it) }
                .take(numToUnfollow)
                .map {
                    database.getUsernameByPK(it)?.let { username ->
                        logger.info("unfollowing: $username")
                        autoremoteClient.unfollowByUsername(username)
                    }
                }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // follows a user and adds them to the whitelist, so they are never automatically unfollowed
    fun followAndAddToWhitelist(username: String) {
        logger.info("whitelisting: $username")
        try {
            val pkToWhitelist = getInstagramUserAndSaveJsonToDB(username).pk
            autoremoteClient.followByUserName(username)
            database.addToWhitelist(apiClient.getOurPK(), pkToWhitelist, Database.WhitelistReason.MANUAL)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // finds mutual followers and calls unfollowUserUnlikelyToUnfollowBack on the ones that aren't whitelisted
    fun pruneMutualFollowers(numToUnfollow: Int = 100) {
        logger.info("pruning mutual followers")
        try {
            val followingMap = apiClient.getFollowing().associateBy({ it.pk }, { it })
            val followersMap = apiClient.getFollowers().associateBy({ it.pk }, { it })

            // filter by intersecting keys
            val mutualFollowersMap = followingMap.filterKeys { followersMap.containsKey(it) }

            logger.info("mutual followers: ${mutualFollowersMap.size}")

            val whitelist = database.getWhitelist(apiClient.getOurPK())

            mutualFollowersMap.filter { !whitelist.contains(it.value.pk) }
                .entries.take(numToUnfollow)
                .map {
                    database.addToWhitelist(
                        apiClient.getOurPK(),
                        it.value.pk,
                        Database.WhitelistReason.SCANNED_WHEN_PRUNING
                    )
                    unfollowUserUnlikelyToUnfollowBack(it.value)
                }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // unfollows users that are unlikely to unfollow you, following at least 3x as many people as followers
    fun unfollowUserUnlikelyToUnfollowBack(user: InstagramUserSummary) {
        val followinger = getInstagramUserAndSaveJsonToDB(user.username)
        val followerRatio = followinger.follower_count / followinger.following_count.toDouble()

        if(followerRatio < 0.5) {
            logger.info("unfollowing ${followinger.username}")
            autoremoteClient.unfollowByUsername(followinger.getUsername())
        }
    }

    fun getRatioForUser(username: String): Double {
        val user = getInstagramUserAndSaveJsonToDB(username)
        return user.follower_count / user.following_count.toDouble()
    }

    fun getInstagramUserAndSaveJsonToDB(username: String): InstagramUser {
        val user = apiClient.getInstagramUser(username)
        database.upsertUserJson(user)
        return user
    }

    // copies followers from another user, ignoring:
    //     users in the blacklist
    //     users we are already following
    //     users with a ratio > 0.5
    fun copyFollowers(username: String, numberToCopy: Int = 200) {
        logger.info("copying followers from $username")
        try {
            val userToCopyFrom = getInstagramUserAndSaveJsonToDB(username)
            val otherUsersFollowers = apiClient.getFollowers(userToCopyFrom)

            applyToGoodUsers(otherUsersFollowers, numberToCopy, true) {
                logger.info("following: ${it.username}")
                autoremoteClient.followByUserName(it.username)
                database.recordAction(apiClient.getOurPK(), it.pk, it.username, username, Database.ActionType.FOLLOW_USER_FOLLOWER)
            }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun followLikersOfTopPostsForTag(tag: String, numberToCopy: Int = 200 ) {
        logger.info("following likers of $tag")
        try {
            val topPosts = apiClient.getTopPostsByTag(tag)
            val likers = topPosts.flatMap { apiClient.getLikersByMediaId(it.pk).asSequence() }

            applyToGoodUsers(likers, numberToCopy, true) {
                logger.info("following: ${it.username}")
                autoremoteClient.followByUserName(it.username)
                database.recordAction(apiClient.getOurPK(), it.pk, it.username, tag, Database.ActionType.FOLLOW_TAG_LIKER)
            }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun getBetaDistributions(actionType: Database.ActionType): List<TagAndBetaDistribution> {
        val numFollowRequestsAndLikebacks = database.getNumActionsAndLikebacks(apiClient.getOurPK(), actionType)
        val setOfRecentTagsFromUserFeed = getSetOfRecentTagsFromUserFeed()

        val tagsNotExploredYet = setOfRecentTagsFromUserFeed.minus(numFollowRequestsAndLikebacks.map { it.tag })
        return numFollowRequestsAndLikebacks.map {
            TagAndBetaDistribution(it.tag, BetaDistribution(it.numLikebacks.toDouble() + 1, (it.numActions - it.numLikebacks).toDouble() + 1))
        }.plus(
            tagsNotExploredYet.map {
                TagAndBetaDistribution(it, BetaDistribution(1.0, 1.0))
            }
        )
    }

    fun getTagsAndActionCountsUsingThompsonSampling(numActionsToGet: Int, actionType: Database.ActionType): Map<String, Int> {
        val betaDistributions = getBetaDistributions(actionType)
        val listOfWinners = (1..numActionsToGet).map { getArgMaxFromBetaDistributions(betaDistributions) }
        return listOfWinners.groupingBy { it }.eachCount()
    }

    fun getArgMaxFromBetaDistributions(betaDistributions: List<TagAndBetaDistribution>): String {
        return betaDistributions.map {
            Pair(it.tag, it.betaDistribution.sample())
        }.maxBy {
            it.second
        }!!.first
    }

    fun applyThompsonSamplingToExploreTagsToFollowFrom(numberToFollow: Int = 200) {
        logger.info("applying thompson sampling for followers")
        val tagsAndFollowCounts = getTagsAndActionCountsUsingThompsonSampling(numberToFollow, Database.ActionType.FOLLOW_TAG_LIKER).toList().sortedByDescending { (_, value) -> value }.toMap()
        logger.info("tags and follow counts: $tagsAndFollowCounts")
        tagsAndFollowCounts.map {
            followLikersOfTopPostsForTag(it.key, it.value)
        }
    }

    fun applyThompsonSamplingToExploreTagsToLikeFrom(numberToLike: Int = 200) {
        logger.info("applying thompson sampling for likers")
        val tagsAndFollowCounts = getTagsAndActionCountsUsingThompsonSampling(numberToLike, Database.ActionType.LIKE_TAG_LIKER).toList().sortedByDescending { (_, value) -> value }.toMap()
        logger.info("tags and follow counts: $tagsAndFollowCounts")
        tagsAndFollowCounts.map {
            likeLikersOfTopPostsForTag(it.key, it.value)
        }
    }

    data class TagAndBetaDistribution(val tag: String, val betaDistribution: BetaDistribution)

    fun likeLikersOfTopPostsForTag(tag: String, numberToLike: Int = 50) {
        logger.info("liking likers of $tag")
        try {
            val topPosts = apiClient.getTopPostsByTag(tag)
            val likers = topPosts.flatMap { apiClient.getLikersByMediaId(it.pk).asSequence() }

            applyToGoodUsers(likers, numberToLike, false) { userSummary ->
                logger.info("liking posts by: ${userSummary.username}")
                autoremoteClient.like3Recent(userSummary.username)
                database.recordAction(apiClient.getOurPK(), userSummary.pk, userSummary.username, tag, Database.ActionType.LIKE_TAG_LIKER)
            }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // iterates through a sequence of InstagramUserSummarys and applies a function to users:
    //      not in the blacklist
    //      not already being followed by us
    //      with a ratio < 0.5
    private fun applyToGoodUsers(users: Sequence<InstagramUserSummary>, numberToApplyTo: Int, includePrivateUsers: Boolean, funToApply: (InstagramUserSummary) -> Unit) {
        val blacklist = database.getBlacklist(apiClient.getOurPK())
        val myFollowingPKs = apiClient.getFollowing().toList().map { it.pk }

        users.filter { !blacklist.contains(it.pk) }
            .filter { !myFollowingPKs.contains(it.pk) }
            .map {
                // blacklist everyone we scan, saves us from having to calculate a ratio every time we see them
                database.addToBlacklist(apiClient.getOurPK(), it.pk)
                it
            }
            .filter { includePrivateUsers || !it.is_private }
            .filter { getRatioForUser(it.username) < 0.5 }
            .map {
                funToApply(it)
            }
            .take(numberToApplyTo)
            .toList()
    }

    // saves likers to the database
    fun recordLikers() {
        logger.info("recording likers")
        try {
            val feed = apiClient.getUserFeed()
            val mediaIDToLikersMap = feed.associateBy({ it.pk }, { apiClient.getLikersByMediaId(it.pk) })
            mediaIDToLikersMap.map {
                val mediaID = it.key
                val ourPK = apiClient.getOurPK()
                val likerPKs = it.value.map { it.pk }
                val existingLikerPKs = database.getLikersForPost(ourPK, mediaID)
                val newLikerPKs = likerPKs.minus(existingLikerPKs)
                database.addToLikerLog(ourPK, mediaID, newLikerPKs)
            }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // saves followers to the database
    fun recordFollowers() {
        logger.info("recording followers")
        try {
            val ourPK = apiClient.getOurPK()
            val followers = apiClient.getFollowers().map { it.pk }
            val existingFollowers = database.getFollowers(ourPK)
            val newFollowers = followers.minus(existingFollowers)
            val unFollowers = existingFollowers.minus(followers)
            database.addToFollowerLog(ourPK, Database.Action.FOLLOWED, newFollowers.toList())
            database.addToFollowerLog(ourPK, Database.Action.UNFOLLOWED, unFollowers.toList())
        }  catch (e: Exception) {
            logger.error(e)
        }
    }

    fun getSetOfRecentTagsFromUserFeed(userPK: Long = apiClient.getOurPK()): Set<String> {
        val userFeed = apiClient.getUserFeed(userPK).toList()
        return getTagsFromCaptionsInFeedItemList(userFeed).toSet()
    }

    // this only looks at the caption
    // to make it work for tags in comments we would need to scan a number of comments, not sure what order they are returned in, might have to scan a lot...
    private fun getTagsFromCaptionsInFeedItemList(instagramFeedItems: List<InstagramFeedItem>): List<String> {
        val tagList: MutableList<String> = mutableListOf()
        instagramFeedItems.map {
            it.caption?.text?.let { captionText ->
                tagList += getTagsFromString(captionText)
            }
        }
        return tagList
    }

    private fun getTagsFromString(string: String): List<String> {
        val tags: MutableList<String> = mutableListOf()
        val validTagCharacters = "a-zA-Z_0-9#"
        val words = string.split(Pattern.compile("[^$validTagCharacters]"))

        words.map { word ->
            if(word.startsWith("#") && word.length > 1) {
                tags += word.drop(1)
            }
        }

        return tags
    }

    // gets the recent tags for a user
    // gets the tags from the top posts for those tags
    // returns a map containing tags as keys and frequencies as values
    fun getTagFrequencyMap(): MutableMap<String, Int> {
        val frequencyMap = mutableMapOf<String, Int>()
        val recentTags = getSetOfRecentTagsFromUserFeed()
        recentTags.map { recentTag ->
            logger.error("recentTag: $recentTag")
            val topPosts = apiClient.getTopPostsByTag(recentTag).take(9).toList()
            val topPostTags = getTagsFromCaptionsInFeedItemList(topPosts)
            topPostTags.map {topPostTag ->
                if(topPostTag != recentTag) {
                    val newCount = frequencyMap.getOrDefault(topPostTag, 0) + 1
                    frequencyMap[topPostTag] = newCount
                }
            }
        }
        return frequencyMap
    }

    fun getTagFrequenciesAndMediaCounts(): MutableMap<String, TagInfo> {
        val frequencyMap = getTagFrequencyMap()
        val frequencyListSortedByFrequencies = frequencyMap.toList().sortedBy { (_, value) -> value }.reversed()
        val frequencyAndMediaCountMap = mutableMapOf<String, TagInfo>()
        frequencyListSortedByFrequencies.subList(0, 199).map {
            val tag = it.first
            val frequency = it.second
            val mediaCount = apiClient.getMediaCountForTag(tag)
            frequencyAndMediaCountMap.put(tag, TagInfo(frequency, mediaCount))
        }
        return frequencyAndMediaCountMap
    }

    fun getTagInformation(tag: String): TagInformation {
        val topPosts = apiClient.getTopPostsByTag(tag).take(9).toList()
        topPosts.map { println(it.user.username) }
        val medianLikes = median(topPosts.map { it.like_count })
        val medianComments = median(topPosts.map { it.comment_count })
        return TagInformation(medianLikes, medianComments)
    }

    private fun median(numbers: List<Int>): Float {
        return numbers.sorted()
            .map { it.toFloat() }
            .let { (it[it.size / 2] + it[(it.size - 1) / 2]) / 2 }
    }

    data class TagInformation(val medianLikes: Float, val medianComments: Float)
    data class TagInfo(val frequency: Int, val mediaCount: Int)
}