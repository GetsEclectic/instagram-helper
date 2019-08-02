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

    fun addToBlacklist(our_pk: Long, pk_to_blacklist: Long) {
        create.insertInto(FOLLOW_BLACKLIST, FOLLOW_BLACKLIST.OUR_PK, FOLLOW_BLACKLIST.BLACKLISTED_PK, FOLLOW_BLACKLIST.BLACKLIST_REASON)
            .values(our_pk, pk_to_blacklist, BLACKLIST_REASONS.SCANNED_WHEN_COPYING.reasonString).execute()
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

    fun addToWhitelist(our_pk: Long, pk_to_whitelist: Long, whitelistReasons: WHITELIST_REASONS) {
        create.insertInto(UNFOLLOW_WHITELIST, UNFOLLOW_WHITELIST.OUR_PK, UNFOLLOW_WHITELIST.WHITELISTED_PK, UNFOLLOW_WHITELIST.WHITELIST_REASON)
            .values(our_pk, pk_to_whitelist, whitelistReasons.reasonString).execute()
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