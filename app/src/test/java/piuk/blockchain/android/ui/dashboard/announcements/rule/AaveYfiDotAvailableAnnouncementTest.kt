package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class AaveYfiDotAvailableAnnouncementTest {
    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()

    private lateinit var subject: AaveYfiDotAvailableAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[AaveYfiDotAvailableAnnouncement.DISMISS_KEY]).thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey).thenReturn(AaveYfiDotAvailableAnnouncement.DISMISS_KEY)

        subject =
            AaveYfiDotAvailableAnnouncement(
                dismissRecorder = dismissRecorder
            )
    }

    @Test
    fun `should not show, when already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, when not already shown`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}
