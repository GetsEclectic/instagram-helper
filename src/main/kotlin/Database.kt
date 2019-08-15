import org.jooq.SQLDialect
import org.jooq.impl.DSL.*
import org.jooq.instagram4k.Tables.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
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
            .values(ourPk, pkToBlacklist, BLACKLIST_REASONS.SCANNED_WHEN_COPYING.reasonString).execute()
    }

    private fun getWhitelist(ourPK: Long, whitelistReasons: List<WHITELIST_REASONS>): HashSet<Long> {
        return create.select()
            .from(UNFOLLOW_WHITELIST)
            .where(UNFOLLOW_WHITELIST.OUR_PK.eq(ourPK))
            .and(UNFOLLOW_WHITELIST.WHITELIST_REASON.`in`(whitelistReasons))
            .fetch()
            .map { it.getValue(UNFOLLOW_WHITELIST.WHITELISTED_PK) }
            .toHashSet()
    }

    fun getWhitelist(ourPK: Long, whitelistReasons: WHITELIST_REASONS): HashSet<Long> {
        return getWhitelist(ourPK, listOf(whitelistReasons))
    }

    fun getWhitelist(ourPK: Long): HashSet<Long> {
        return getWhitelist(ourPK, listOf(WHITELIST_REASONS.MANUAL, WHITELIST_REASONS.SCANNED_WHEN_PRUNING))
    }

    fun addToWhitelist(ourPk: Long, pkToWhitelist: Long, whitelistReasons: WHITELIST_REASONS) {
        create.insertInto(UNFOLLOW_WHITELIST, UNFOLLOW_WHITELIST.OUR_PK, UNFOLLOW_WHITELIST.WHITELISTED_PK, UNFOLLOW_WHITELIST.WHITELIST_REASON)
            .values(ourPk, pkToWhitelist, whitelistReasons.reasonString).execute()
    }

    fun recordFollowRequest(ourPK: Long, requestedPK: Long, requestedUsername: String) {
        create.insertInto(FOLLOW_REQUEST, FOLLOW_REQUEST.OUR_PK, FOLLOW_REQUEST.REQUESTED_PK, FOLLOW_REQUEST.REQUESTED_USERNAME)
            .values(ourPK, requestedPK, requestedUsername).execute()
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

    enum class BLACKLIST_REASONS(val reasonString: String) {
        SCANNED_WHEN_COPYING("scanned when copying followers");

        override fun toString(): String {
            return reasonString
        }
    }

    enum class WHITELIST_REASONS(val reasonString: String) {
        MANUAL("manually whitelisted"),
        SCANNED_WHEN_PRUNING("scanned when pruning mutual followers");

        override fun toString(): String {
            return reasonString
        }
    }
}