import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class Instagram4KTest {
    val apiClient: ApiClient = mockk()

    val testObject: Instagram4K = Instagram4K(apiClient)

    @Test
    fun `getUnfollowerPKs should return only users that are in the following list but not the follower list`() {
        val instagramUserSummary1 = InstagramUserSummary()
        instagramUserSummary1.pk = 1
        val instagramUserSummary2 = InstagramUserSummary()
        instagramUserSummary2.pk = 2

        every { apiClient.getFollowers() } returns (
            sequence {
                yieldAll(listOf(instagramUserSummary1))
            }
        )

        every { apiClient.getFollowing() } returns (
            setOf(instagramUserSummary1, instagramUserSummary2)
        )

        val unfollowerPKs = testObject.getUnfollowerPKs()
        assertThat(unfollowerPKs).containsExactly(
            2.toLong()
        )
    }
}