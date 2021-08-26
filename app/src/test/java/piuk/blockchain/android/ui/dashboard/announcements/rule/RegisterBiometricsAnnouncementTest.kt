package piuk.blockchain.android.ui.dashboard.announcements.rule

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder

class RegisterBiometricsAnnouncementTest {

    private val dismissRecorder: DismissRecorder = mock()
    private val dismissEntry: DismissRecorder.DismissEntry = mock()
    private val biometricsController: BiometricsController = mock()

    private lateinit var subject: RegisterBiometricsAnnouncement

    @Before
    fun setUp() {
        whenever(dismissRecorder[RegisterBiometricsAnnouncement.DISMISS_KEY])
            .thenReturn(dismissEntry)
        whenever(dismissEntry.prefsKey)
            .thenReturn(RegisterBiometricsAnnouncement.DISMISS_KEY)

        subject =
            RegisterBiometricsAnnouncement(
                dismissRecorder = dismissRecorder,
                biometricsController = biometricsController
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
    fun `should show, when not already shown, and there is no fingerprint hardware`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)

        whenever(biometricsController.isHardwareDetected).thenReturn(false)
        whenever(biometricsController.isBiometricUnlockEnabled).thenReturn(false)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should not show, when not already shown, fingerprint hardware exists and fingerprints are registered`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)

        whenever(biometricsController.isHardwareDetected).thenReturn(true)
        whenever(biometricsController.isBiometricUnlockEnabled).thenReturn(true)

        subject.shouldShow()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `should show, when not already shown, fingerprint hardware exists and fingerprints are not registered`() {
        whenever(dismissEntry.isDismissed).thenReturn(false)

        whenever(biometricsController.isHardwareDetected).thenReturn(true)
        whenever(biometricsController.isBiometricUnlockEnabled).thenReturn(false)

        subject.shouldShow()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }
}
