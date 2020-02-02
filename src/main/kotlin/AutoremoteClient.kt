import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.*

class AutoremoteClient(ourUsername: String) {
    val logger = LogManager.getLogger(javaClass)

    private val autoremoteBaseURL: String
    private val actionChannel = Channel<AutoremoteAction>()
    private var keepRunning = true
    private var haveExecutedSwitch = false

    private val deferred = GlobalScope.async {
        while(keepRunning || !actionChannel.isEmpty ) {
            if(!actionChannel.isEmpty) {
                // if we haven't switched profiles yet, and there is an action to perform, do a switch first to make sure we're on the correct profile
                if(!haveExecutedSwitch) {
                    switchProfile(ourUsername)
                    haveExecutedSwitch = true
                }

                val autoremoteAction = actionChannel.receive()
                when(autoremoteAction.autoremoteActionType) {
                    AutoremoteActionType.FOLLOW -> processFollowAction(autoremoteAction)
                    AutoremoteActionType.UNFOLLOW -> processUnfollowAction(autoremoteAction)
                    AutoremoteActionType.LIKE_3_RECENT -> processLike3RecentAction(autoremoteAction)
                }
            }
            delay(500)
        }
    }

    init {
        val config = Properties()
        File("config.properties").inputStream().let { config.load(it) }
        autoremoteBaseURL = config.getProperty("autoremote.baseurl")
    }

    data class AutoremoteAction(val autoremoteActionType: AutoremoteActionType, val userName: String)
    enum class AutoremoteActionType {
        FOLLOW,
        UNFOLLOW,
        LIKE_3_RECENT
    }

    fun followByUserName(userName: String) {
        runBlocking {
            actionChannel.send(AutoremoteAction(AutoremoteActionType.FOLLOW, userName))
        }
    }

    private fun processFollowAction(autoremoteAction: AutoremoteAction) {
        val autoremoteFollowByUsernameURL = "$autoremoteBaseURL&message=Follow=:=${autoremoteAction.userName}"
        Fuel.get(autoremoteFollowByUsernameURL).responseString()
        Thread.sleep(5000)
    }

    fun unfollowByUsername(userName: String) {
        runBlocking {
            actionChannel.send(AutoremoteAction(AutoremoteActionType.UNFOLLOW, userName))
        }
    }

    private fun processUnfollowAction(autoremoteAction: AutoremoteAction) {
        val autoremoteUnfollowByUsernameURL = "$autoremoteBaseURL&message=Unfollow=:=${autoremoteAction.userName}"
        Fuel.get(autoremoteUnfollowByUsernameURL).responseString()
        Thread.sleep(7000)
    }

    private fun switchProfile(userName: String) {
        val autoremoteUnfollowByUsernameURL = "$autoremoteBaseURL&message=SwitchProfile=:=$userName"
        Fuel.get(autoremoteUnfollowByUsernameURL).responseString()
        Thread.sleep(20000)
    }

    fun like3Recent(userName: String) {
        runBlocking {
            actionChannel.send(AutoremoteAction(AutoremoteActionType.LIKE_3_RECENT, userName))
        }
    }

    private fun processLike3RecentAction(autoremoteAction: AutoremoteAction) {
        val autoremoteUnfollowByUsernameURL = "$autoremoteBaseURL&message=Like3Recent=:=${autoremoteAction.userName}"
        Fuel.get(autoremoteUnfollowByUsernameURL).responseString()
        Thread.sleep(12000)
    }

    fun close() {
        logger.info(("close called on AutoremoteClient, awaiting coroutine completion"))
        keepRunning = false
        runBlocking {
            deferred.await()
        }
    }
}