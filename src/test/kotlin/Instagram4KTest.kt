import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.brunocvcunha.instagram4j.requests.payload.StatusResult
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class Instagram4KTest {
    val apiClient: ApiClient = mockk()

    val testObject: Instagram4K = Instagram4K(apiClient)

    fun createInstagramUserSummary(pk: Long = 1): InstagramUserSummary {
        val instagramUserSummary = InstagramUserSummary()
        instagramUserSummary.pk = pk
        return instagramUserSummary
    }

    @Nested
    inner class UnfollowTest {
        init {
            val instagramUserSummary1 = createInstagramUserSummary(pk = 1)
            val instagramUserSummary2 = createInstagramUserSummary(pk = 2)

            every { apiClient.getFollowers() } returns (
                    sequence {
                        yieldAll(listOf(instagramUserSummary1))
                    })

            every { apiClient.getFollowing() } returns (
                    setOf(instagramUserSummary1, instagramUserSummary2)
                    )

            every { apiClient.unfollowByPK(any()) } returns (
                    StatusResult()
                    )

            mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")

            every { File(WHITELIST_FILE_PATH).readText() } returns ("7,")
        }

        @Test
        fun `getUnfollowerPKs should return only users that are in the following list but not the follower list`() {
            val unfollowerPKs = testObject.getUnfollowerPKs()

            assertThat(unfollowerPKs).containsExactly(
                2.toLong()
            )
        }

        @Test
        fun `unfollowUnfollowers should call unfollowByPK only with the PKs of users in the following list but not the follower list`() {
            testObject.unfollowUnfollowers()

            verify { apiClient.unfollowByPK(2) }
        }

        @Test
        fun `unfollowUnfollowers should not unfollow users in the whitelist`() {
            every { File(WHITELIST_FILE_PATH).readText() } returns ("2,")

            testObject.unfollowUnfollowers()

            verify(exactly = 0) { apiClient.unfollowByPK(any()) }
        }
    }
}