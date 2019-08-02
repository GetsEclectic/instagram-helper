import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.brunocvcunha.instagram4j.requests.payload.InstagramUser
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.brunocvcunha.instagram4j.requests.payload.StatusResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class Instagram4KTest {
    val apiClient: ApiClient = mockk()

    val database: Database = mockk()

    val testObject: Instagram4K = Instagram4K(apiClient, database)

    @BeforeEach
    fun init() {
        clearAllMocks()
    }

    fun createInstagramUserSummary(pk: Long = 1, username: String = "Alice"): InstagramUserSummary {
        val instagramUserSummary = InstagramUserSummary()
        instagramUserSummary.pk = pk
        instagramUserSummary.username = username
        return instagramUserSummary
    }

    fun createInstagramUser(pk: Long = 1, followerCount: Int = 0, followingCount: Int = 0): InstagramUser {
        val instagramUser = InstagramUser()
        instagramUser.pk = pk
        instagramUser.follower_count = followerCount
        instagramUser.following_count = followingCount
        return instagramUser
    }

    @Nested
    inner class UnfollowTest {
        @BeforeEach
        fun init() {
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

            every { File(WHITELIST_FILE_PATH).readLines() } returns (listOf("7"))
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
            every { File(WHITELIST_FILE_PATH).readLines() } returns (listOf("2"))

            testObject.unfollowUnfollowers()

            verify(exactly = 0) { apiClient.unfollowByPK(any()) }
        }
    }

    @Nested
    inner class ListTest {
        @BeforeEach
        fun init() {
            mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")

            every { File(WHITELIST_FILE_PATH).appendText(any()) } returns (Unit)
        }

        @Test
        fun `addToWhitelist should call followByPK and append the pk for the user followed by a comma to the whitelist file`() {
            val username = "Alice"
            val pk = 2.toLong()

            every { apiClient.getInstagramUser(username) } returns (
                    createInstagramUser(pk = pk)
                    )

            every { apiClient.followByPK(any()) } returns (StatusResult())

            testObject.addToWhitelist(username)

            verify {
                File(WHITELIST_FILE_PATH).appendText("$pk\n")
                apiClient.followByPK(pk)
            }
        }

        @Test
        fun `getWhitelist should turn a file containing the lines 3 and 4 into a HashSet containing 3 and 4`() {
            every { File(WHITELIST_FILE_PATH).readLines() } returns (listOf("3", "4"))

            val whiteList = testObject.getWhitelist()

            assertThat(whiteList).containsExactlyInAnyOrder(
                3.toLong(),
                4.toLong()
            )
        }
    }

    @Nested
    inner class RatioTest {
        @Test
        fun `getRatioForUser should return one half for a user who is following 2 people and followed by 1 person`() {
            val username = "Alice"
            every { apiClient.getInstagramUser(username)} returns (createInstagramUser(followingCount = 2, followerCount = 1))

            val ratio = testObject.getRatioForUser(username)

            assertThat(ratio).isEqualTo(0.5)
        }

        @Test
        fun `getRatioForUser should return 2 for a user who is following 1 person and followed by 2 people`() {
            val username = "Alice"
            every { apiClient.getInstagramUser(username)} returns (createInstagramUser(followingCount = 1, followerCount = 2))

            val ratio = testObject.getRatioForUser(username)

            assertThat(ratio).isEqualTo(2.toDouble())
        }
    }

    @Nested
    inner class PruneTest {
        val username = "Bob"
        val pk: Long = 7

        @BeforeEach
        fun init() {
            val mutualFollowerSummary = createInstagramUserSummary(pk = pk, username = username)
            every { apiClient.getFollowing() } returns (setOf(mutualFollowerSummary))
            every { apiClient.getFollowers(any()) } returns (
                    sequence {
                        yieldAll(listOf(mutualFollowerSummary))
                    })

            every { apiClient.unfollowByPK(any())} returns (StatusResult())

            mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")
            every { File(WHITELIST_FILE_PATH).readLines() } returns (listOf("3", "4"))

            val mutualFollower = createInstagramUser(pk = pk, followerCount = 101, followingCount = 400)
            every { apiClient.getInstagramUser(username)} returns (mutualFollower)
        }

        @Test
        fun `pruneMutualFollowers should call unfollowByPK for a user in followers and following with more than 100 followers and a low ratio, and not in the whitelist`() {
            testObject.pruneMutualFollowers()

            verify {
                apiClient.unfollowByPK(pk)
            }
        }

        @Test
        fun `pruneMutualFollowers should not call unfollowByPK for a user with a high ratio`() {
            val mutualFollower = createInstagramUser(pk = pk, followerCount = 101, followingCount = 10)
            every { apiClient.getInstagramUser(username)} returns (mutualFollower)

            testObject.pruneMutualFollowers()

            verify(exactly = 0) {
                apiClient.unfollowByPK(pk)
            }
        }

        @Test
        fun `pruneMutualFollowers should not call unfollowByPK for a user with less than 100 followers`() {
            val mutualFollower = createInstagramUser(pk = pk, followerCount = 99, followingCount = 400)
            every { apiClient.getInstagramUser(username)} returns (mutualFollower)

            testObject.pruneMutualFollowers()

            verify(exactly = 0) {
                apiClient.unfollowByPK(pk)
            }
        }

        @Test
        fun `pruneMutualFollowers should not call unfollowByPK for a user in the whitelist`() {
            every { File(WHITELIST_FILE_PATH).readLines() } returns (listOf("$pk"))

            testObject.pruneMutualFollowers()

            verify(exactly = 0) {
                apiClient.unfollowByPK(pk)
            }
        }

        @Test
        fun `pruneMutualFollowers should not call unfollowByPK for a user in followers but not following`() {
            every { apiClient.getFollowing() } returns (setOf())

            testObject.pruneMutualFollowers()

            verify(exactly = 0) {
                apiClient.unfollowByPK(pk)
            }
        }

        @Test
        fun `pruneMutualFollowers should not call unfollowByPK for a user not in followers but in following`() {
            every { apiClient.getFollowers(any()) } returns (
                    sequence {
                        yieldAll(listOf())
                    })

            testObject.pruneMutualFollowers()

            verify(exactly = 0) {
                apiClient.unfollowByPK(pk)
            }
        }
    }

    @Nested
    inner class CopyTest {
        val targetName = "Carl"
        val targetUser = createInstagramUser()

        val followerName = "Bob"
        val followerPK = 6.toLong()
        val followerUserSummary = createInstagramUserSummary(pk = followerPK, username = followerName)
        val followerUser = createInstagramUser(pk = followerPK, followerCount = 100, followingCount = 400)

        @BeforeEach
        fun init() {
            every { apiClient.getInstagramUser(targetName)} returns (targetUser)
            every { apiClient.getInstagramUser(followerName)} returns (followerUser)
            every { apiClient.getFollowers(targetUser)} returns (
                    sequence {
                        yieldAll(listOf(followerUserSummary))
                    })
            every { apiClient.getFollowing() } returns (setOf())
            every { apiClient.followByPK(followerPK)} returns (StatusResult())

            mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")

            every { database.addToBlacklist(any()) } returns (Unit)
            every { database.getBlacklist() } returns (hashSetOf())
        }

        @Test
        fun `copyFollowers should call followByPK and addToBlacklist for a user that we aren't already following, is following the target, isn't in the blacklist, and has a poor ratio`() {
            testObject.copyFollowers(targetName)

            verify {
                apiClient.followByPK(followerPK)
                database.addToBlacklist(followerPK)
            }
        }

        @Test
        fun `copyFollowers should not call followByPK or addToBlacklist for a user that we are already following`() {
            every { apiClient.getFollowing() } returns (setOf(followerUserSummary))

            testObject.copyFollowers(targetName)

            verify(exactly = 0) {
                apiClient.followByPK(followerPK)
                database.addToBlacklist(any())
            }
        }

        @Test
        fun `copyFollowers should not call followByPK or addToBlacklist for a user that isn't following the target`() {
            every { apiClient.getFollowers(targetUser)} returns (
                    sequence {
                        yieldAll(listOf())
                    })

            testObject.copyFollowers(targetName)

            verify(exactly = 0) {
                apiClient.followByPK(followerPK)
                database.addToBlacklist(any())
            }
        }

        @Test
        fun `copyFollowers should not call followByPK or addToBlacklist for a user that is in the blacklist`() {
            every { database.getBlacklist() } returns (hashSetOf(followerPK))

            testObject.copyFollowers(targetName)

            verify(exactly = 0) {
                apiClient.followByPK(followerPK)
                database.addToBlacklist(any())
            }
        }

        @Test
        fun `copyFollowers should not call followByPK but should call addToBlacklist for a user that has a good ratio`() {
            every { apiClient.getInstagramUser(followerName)} returns (createInstagramUser(pk = followerPK, followerCount = 100, followingCount = 10))

            testObject.copyFollowers(targetName)

            verify(exactly = 0) {
                apiClient.followByPK(followerPK)
            }

            verify {
                database.addToBlacklist(followerPK)
            }
        }
    }
}