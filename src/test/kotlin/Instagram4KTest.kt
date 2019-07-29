import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class Instagram4KTest {
    @Test
    fun `unfollowUnfollowers should call unfollowByPK for users that are in the following list but not the follower list`() {
        val zeroInsta = Instagram4K("username", "password")
        zeroInsta.unfollowUnfollowers()
    }
}