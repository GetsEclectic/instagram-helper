import com.nhaarman.mockitokotlin2.whenever
import org.brunocvcunha.instagram4j.requests.payload.InstagramUserSummary
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Spy

internal class Instagram4KTest {
    @BeforeEach
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Mock
    lateinit var apiClient: ApiClient

    @Spy
    @InjectMocks
    lateinit var testObject: Instagram4K

    @Test
    fun `getUnfollowerPKs should return users that are in the following list but not the follower list`() {

//        whenever(apiClient.getInstagramUser(any())).thenReturn(InstagramUser())

        val instagramUserSummary1 = InstagramUserSummary()
        instagramUserSummary1.pk = 1
        val instagramUserSummary2 = InstagramUserSummary()
        instagramUserSummary2.pk = 2

        whenever(apiClient.getFollowers()).thenReturn(
            sequence {
                yieldAll(listOf(instagramUserSummary1))
            }
        )

        whenever(apiClient.getFollowing()).thenReturn(
            setOf(instagramUserSummary1, instagramUserSummary2)
        )

        val unfollowerPKs = testObject.getUnfollowerPKs()
        assertTrue(unfollowerPKs.equals(listOf(2)), "should contain only 2")
    }
}