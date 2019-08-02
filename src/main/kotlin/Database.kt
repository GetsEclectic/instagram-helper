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

    fun getBlacklist(): HashSet<Long> {
        return create.select()
            .from(FOLLOW_BLACKLIST)
            .fetch()
            .map { it.getValue(FOLLOW_BLACKLIST.BLACKLISTED_PK) }
            .toHashSet()
    }

    fun addToBlacklist(pk: Long) {
        create.insertInto(FOLLOW_BLACKLIST, FOLLOW_BLACKLIST.OUR_PK, FOLLOW_BLACKLIST.BLACKLISTED_PK, FOLLOW_BLACKLIST.BLACKLIST_REASON)
            .values(-1, pk, BLACKLIST_REASONS.SCANNED_WHEN_COPYING.reasonString)
    }

    fun getWhitelist(): HashSet<Long> {
        return create.select()
            .from(UNFOLLOW_WHITELIST)
            .fetch()
            .map { it.getValue(UNFOLLOW_WHITELIST.WHITELISTED_PK) }
            .toHashSet()
    }

    fun addToWhitelist(pk: Long, whitelistReasons: WHITELIST_REASONS) {
        create.insertInto(UNFOLLOW_WHITELIST, UNFOLLOW_WHITELIST.OUR_PK, UNFOLLOW_WHITELIST.WHITELISTED_PK, UNFOLLOW_WHITELIST.WHITELIST_REASON)
            .values(-1, pk, whitelistReasons.reasonString)
    }

    enum class BLACKLIST_REASONS(val reasonString: String) {
        SCANNED_WHEN_COPYING("scanned when copying followers")
    }

    enum class WHITELIST_REASONS(val reasonString: String) {
        MANUAL("manually whitelisted"),
        SCANNED_WHEN_PRUNING("scanned when pruning mutual followers")
    }
}