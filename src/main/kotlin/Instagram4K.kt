import org.apache.logging.log4j.LogManager
import org.brunocvcunha.instagram4j.requests.payload.InstagramFeedItem
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import java.io.Closeable
import java.lang.Exception
import java.util.regex.Pattern

class Instagram4K(val apiClient: ApiClient, private val database: Database = Database()): Closeable {
    override fun close() {
        apiClient.close()
    }

    constructor(instaName: String, instaPW: String) : this(ApiClient(instaName, instaPW))

    val logger = LogManager.getLogger(javaClass)

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
                    logger.info("unfollowing: $it")
                    apiClient.unfollowByPK(it)
                }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // follows a user and adds them to the whitelist, so they are never automatically unfollowed
    fun followAndAddToWhitelist(username: String) {
        logger.info("whitelisting: $username")
        try {
            val pk_to_whitelist = getInstagramUserAndSaveJsonToDB(username).pk
            apiClient.followByPK(pk_to_whitelist)
            database.addToWhitelist(apiClient.getOurPK(), pk_to_whitelist, Database.WhitelistReason.MANUAL)
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
            apiClient.unfollowByPK(followinger.pk)
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

            followGoodUsers(otherUsersFollowers, numberToCopy, username, Database.SourceType.USER)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun followLikersOfTopPostsForTag(tag: String, numberToCopy: Int = 200 ) {
        logger.info("following likers of $tag")
        try {
            val topPosts = apiClient.getTopPostsByTag(tag)
            val likers = topPosts.flatMap { apiClient.getLikersByMediaId(it.pk).asSequence() }

            followGoodUsers(likers, numberToCopy, tag, Database.SourceType.TAG_LIKE)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun likeLikersOfTopPostsForTag(tag: String, numberToLike: Int = 50) {
        logger.info("liking likers of $tag")
        try {
            val topPosts = apiClient.getTopPostsByTag(tag)
            val likers = topPosts.flatMap { apiClient.getLikersByMediaId(it.pk).asSequence() }

            followGoodUsers(likers, numberToLike, tag, Database.SourceType.TAG_LIKE)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    // iterates through a sequence of InstagramUserSummarys and follows users:
    //      not in the blacklist
    //      not already being followed by us
    //      with a ratio < 0.5
    private fun followGoodUsers(users: Sequence<InstagramUserSummary>, numberToFollow: Int, source: String, sourceType: Database.SourceType) {
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
                database.recordAction(apiClient.getOurPK(), it.pk, it.username, source, sourceType)
            }
            .take(numberToFollow)
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

    // this only looks at the caption
    // to make it work for tags in comments we would need to scan a number of comments, not sure what order they are returned in, might have to scan a lot...
    fun getSetOfRecentTagsFromUserFeed(userPK: Long = apiClient.getOurPK()): Set<String> {
        val userFeed = apiClient.getUserFeed(userPK).toList()
        return getTagsFromCaptionsInFeedItemList(userFeed).toSet()
    }

    private fun getTagsFromCaptionsInFeedItemList(instagramFeedItems: List<InstagramFeedItem>): List<String> {
        val tagList: MutableList<String> = mutableListOf()
        instagramFeedItems.map {
            it.caption?.text?.let { captionText ->
                val validTagCharacters = "a-zA-Z_0-9#"
                val words = captionText.split(Pattern.compile("[^$validTagCharacters]"))

                words.map { word ->
                    if(word.startsWith("#") && word.length > 1) {
                        tagList += word.drop(1)
                    }
                }
            }
        }
        return tagList
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

    data class TagInfo(val frequency: Int, val mediaCount: Int)
}