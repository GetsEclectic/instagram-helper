import com.google.gson.Gson
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.impl.DSL.*
import org.jooq.instagram4k.Tables.*
import org.jooq.instagram4k.tables.FollowerLog
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.*

class Database {
    val create = using(getDatabaseConnection(), SQLDialect.POSTGRES)

    fun getDatabaseConnection() : Connection {
        val dbConfig = Properties()
        File("dbconfig.properties").inputStream().let { dbConfig.load(it) }
        val dbUser: String = dbConfig.getProperty("db.user")
        val dbPassword: String = dbConfig.getProperty("db.password")
        val dbUrl: String = dbConfig.getProperty("db.url")
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    }

    fun getBlacklist(ourPK: Long): HashSet<Long> {
        return create.select()
            .from(FOLLOW_BLACKLIST)
            .where(FOLLOW_BLACKLIST.OUR_PK.eq(ourPK))
            .fetch()
            .map { it.getValue(FOLLOW_BLACKLIST.BLACKLISTED_PK) }
            .toHashSet()
    }

    fun addToBlacklist(ourPk: Long, pkToBlacklist: Long) {
        create.insertInto(FOLLOW_BLACKLIST, FOLLOW_BLACKLIST.OUR_PK, FOLLOW_BLACKLIST.BLACKLISTED_PK, FOLLOW_BLACKLIST.BLACKLIST_REASON)
            .values(ourPk, pkToBlacklist, BlacklistReason.SCANNED_WHEN_COPYING.reasonString).execute()
    }

    private fun getWhitelist(ourPK: Long, whitelistReasons: List<WhitelistReason>): HashSet<Long> {
        return create.select()
            .from(UNFOLLOW_WHITELIST)
            .where(UNFOLLOW_WHITELIST.OUR_PK.eq(ourPK))
            .and(UNFOLLOW_WHITELIST.WHITELIST_REASON.`in`(whitelistReasons))
            .fetch()
            .map { it.getValue(UNFOLLOW_WHITELIST.WHITELISTED_PK) }
            .toHashSet()
    }

    fun getWhitelist(ourPK: Long, whitelistReason: WhitelistReason): HashSet<Long> {
        return getWhitelist(ourPK, listOf(whitelistReason))
    }

    fun getWhitelist(ourPK: Long): HashSet<Long> {
        return getWhitelist(ourPK, listOf(WhitelistReason.MANUAL, WhitelistReason.SCANNED_WHEN_PRUNING))
    }

    fun addToWhitelist(ourPk: Long, pkToWhitelist: Long, whitelistReason: WhitelistReason) {
        create.insertInto(UNFOLLOW_WHITELIST, UNFOLLOW_WHITELIST.OUR_PK, UNFOLLOW_WHITELIST.WHITELISTED_PK, UNFOLLOW_WHITELIST.WHITELIST_REASON)
            .values(ourPk, pkToWhitelist, whitelistReason.reasonString).execute()
    }

    fun recordAction(ourPK: Long, requestedPK: Long, requestedUsername: String, source: String, actionType: ActionType) {
        create.insertInto(ACTION_LOG, ACTION_LOG.OUR_PK, ACTION_LOG.REQUESTED_PK, ACTION_LOG.REQUESTED_USERNAME, ACTION_LOG.SOURCE, ACTION_LOG.ACTION_TYPE)
            .values(ourPK, requestedPK, requestedUsername, source, actionType.typeString).execute()
    }

    fun addToLikerLog(ourPK: Long, mediaID: Long, likerPKList: List<Long>) {
        likerPKList.map {
            create.insertInto(LIKER_LOG, LIKER_LOG.OUR_PK, LIKER_LOG.MEDIA_ID, LIKER_LOG.LIKER_PK)
                .values(ourPK, mediaID, it).execute()
        }
    }

    fun getLikersForPost(ourPK: Long, mediaID: Long): List<Long> {
        return create.select()
            .from(LIKER_LOG)
            .where(LIKER_LOG.OUR_PK.eq(ourPK))
            .and(LIKER_LOG.MEDIA_ID.eq(mediaID))
            .fetch()
            .map { it.getValue(LIKER_LOG.LIKER_PK) }
    }

    fun addToFollowerLog(ourPK: Long, action: Action, followerPKList: List<Long>) {
        followerPKList.map {
            create.insertInto(FOLLOWER_LOG, FOLLOWER_LOG.OUR_PK, FOLLOWER_LOG.ACTION, FOLLOWER_LOG.FOLLOWER_PK)
                .values(ourPK, action.actionString, it).execute()
        }
    }

    // get current followers
    // calculated as all follows for our pk where there isn't a later unfollow
    fun getFollowers(ourPK: Long): List<Long> {
        val fl: FollowerLog = FollowerLog().`as`("fl")
        val flLaterUnfollow: FollowerLog = FollowerLog().`as`("fl_later_unfollow")
        return create.select()
            .from(fl)
            .where(fl.OUR_PK.eq(ourPK))
            .and(fl.ACTION.eq(Action.FOLLOWED.actionString))
            .and(notExists(
                create.selectOne()
                    .from(flLaterUnfollow)
                    .where(flLaterUnfollow.OUR_PK.eq(fl.OUR_PK))
                    .and(flLaterUnfollow.FOLLOWER_PK.eq(fl.FOLLOWER_PK))
                    .and(flLaterUnfollow.ID.greaterThan(fl.ID))
                    .and(flLaterUnfollow.ACTION.eq(Action.UNFOLLOWED.actionString))
            ))
            .fetch()
            .map { it.getValue(FOLLOWER_LOG.FOLLOWER_PK) }
    }

    fun upsertUserJson(user: InstagramUser) {
        val jsonString = Gson().toJson(user)
        create.insertInto(INSTAGRAM_USER_JSON, INSTAGRAM_USER_JSON.USER_PK, INSTAGRAM_USER_JSON.JSON)
            .values(user.pk, JSONB.valueOf(jsonString))
            .onDuplicateKeyUpdate()
            .set(INSTAGRAM_USER_JSON.JSON, JSONB.valueOf(jsonString))
            .set(INSTAGRAM_USER_JSON.INSERT_DATE, OffsetDateTime.now())
            .execute()
    }

    fun getSecretLoginInfo(username: String): SecretLoginInfo? {
        return create.select()
            .from(SECRET_LOGIN_INFO)
            .where(SECRET_LOGIN_INFO.USERNAME.eq(username))
            .fetch()
            .firstOrNull()
            ?.let { SecretLoginInfo(it.getValue(SECRET_LOGIN_INFO.COOKIE_STORE_SERIALIZED), it.getValue(
                SECRET_LOGIN_INFO.UUID)) }
    }

    fun upsertSecretLoginInfo(ourPK: Long, username: String, cookieStoreSerialized: ByteArray, uuid: String) {
        create.insertInto(SECRET_LOGIN_INFO, SECRET_LOGIN_INFO.USER_PK, SECRET_LOGIN_INFO.USERNAME, SECRET_LOGIN_INFO.COOKIE_STORE_SERIALIZED, SECRET_LOGIN_INFO.UUID)
            .values(ourPK, username, cookieStoreSerialized, uuid)
            .onDuplicateKeyUpdate()
            .set(SECRET_LOGIN_INFO.USERNAME, username)
            .set(SECRET_LOGIN_INFO.COOKIE_STORE_SERIALIZED, cookieStoreSerialized)
            .set(SECRET_LOGIN_INFO.UUID, uuid)
            .set(SECRET_LOGIN_INFO.INSERT_DATE, OffsetDateTime.now())
            .execute()
    }

    data class SecretLoginInfo(val cookieStoreSerialized: ByteArray, val uuid: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SecretLoginInfo

            if (!cookieStoreSerialized.contentEquals(other.cookieStoreSerialized)) return false
            if (uuid != other.uuid) return false

            return true
        }

        override fun hashCode(): Int {
            var result = cookieStoreSerialized.contentHashCode()
            result = 31 * result + uuid.hashCode()
            return result
        }
    }

    enum class Action(val actionString: String) {
        FOLLOWED("followed"),
        UNFOLLOWED("unfollowed");

        override fun toString(): String {
            return actionString
        }
    }

    enum class BlacklistReason(val reasonString: String) {
        SCANNED_WHEN_COPYING("scanned when copying followers");

        override fun toString(): String {
            return reasonString
        }
    }

    enum class WhitelistReason(val reasonString: String) {
        MANUAL("manually whitelisted"),
        SCANNED_WHEN_PRUNING("scanned when pruning mutual followers");

        override fun toString(): String {
            return reasonString
        }
    }

    enum class ActionType(val typeString: String) {
        TAG_LIKE("tag_like"),
        USER("user");

        override fun toString(): String {
            return typeString
        }
    }
}