import com.github.kittinunf.fuel.Fuel
import java.io.File
import java.util.*

class AutoremoteClient(ourUsername: String) {
    private val autoremoteBaseURL: String

    init {
        val config = Properties()
        File("config.properties").inputStream().let { config.load(it) }
        autoremoteBaseURL = config.getProperty("autoremote.baseurl")

        switchProfile(ourUsername)
    }

    fun followByUserName(userName: String) {
        val autoremoteFollowByUsernameURL = "$autoremoteBaseURL&message=Follow=:=$userName"
        Fuel.get(autoremoteFollowByUsernameURL).responseString()
        Thread.sleep(5000)
    }

    fun unfollowByUsername(username: String) {
        val autoremoteUnfollowByUsernameURL = "$autoremoteBaseURL&message=Unfollow=:=$username"
        Fuel.get(autoremoteUnfollowByUsernameURL).responseString()
        Thread.sleep(7000)
    }

    private fun switchProfile(userName: String) {
        val autoremoteUnfollowByUsernameURL = "$autoremoteBaseURL&message=SwitchProfile=:=$userName"
        Fuel.get(autoremoteUnfollowByUsernameURL).responseString()
        Thread.sleep(12000)
    }

    fun like3Recent(userName: String) {
        val autoremoteUnfollowByUsernameURL = "$autoremoteBaseURL&message=Like3Recent=:=$userName"
        Fuel.get(autoremoteUnfollowByUsernameURL).responseString()
        Thread.sleep(12000)
    }
}