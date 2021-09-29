package piuk.blockchain.android.ui.createwallet

import com.blockchain.core.user.NabuUserDataManager
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvents
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.wallet.payload.data.Wallet
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import piuk.blockchain.android.R
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.android.util.FormatChecker
import piuk.blockchain.androidcore.data.access.AccessState
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.PersistentPrefs

class CreateWalletPresenterTest {

    private lateinit var subject: CreateWalletPresenter
    private val view: CreateWalletView = mock()
    private val appUtil: AppUtil = mock()
    private val accessState: AccessState = mock()
    private val payloadDataManager: PayloadDataManager = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val prefsUtil: PersistentPrefs = mock()
    private val analytics: Analytics = mock()
    private val environmentConfig: EnvironmentConfig = mock()
    private val formatChecker: FormatChecker = mock()
    private val nabuUserDataManager: NabuUserDataManager = mock()

    @Before
    fun setUp() {
        subject = CreateWalletPresenter(
            payloadDataManager = payloadDataManager,
            prefs = prefsUtil,
            appUtil = appUtil,
            accessState = accessState,
            specificAnalytics = mock(),
            analytics = analytics,
            environmentConfig = environmentConfig,
            formatChecker = formatChecker,
            nabuUserDataManager = nabuUserDataManager
        )
        subject.initView(view)
    }

    @Test
    fun onViewReady() {
        // Nothing to test
    }

    @Test
    fun `create wallet`() {
        // Arrange
        val email = "john@snow.com"
        val pw1 = "MyTestWallet"
        val accountName = "AccountName"
        val sharedKey = "SHARED_KEY"
        val guid = "GUID"
        val recoveryPhrase = ""

        whenever(view.getDefaultAccountName()).thenReturn(accountName)
        whenever(payloadDataManager.createHdWallet(any(), any(), any())).thenReturn(
            Single.just(
                Wallet()
            )
        )

        whenever(payloadDataManager.wallet!!.guid).thenReturn(guid)
        whenever(payloadDataManager.wallet!!.sharedKey).thenReturn(sharedKey)

        // Act
        subject.createOrRestoreWallet(email, pw1, recoveryPhrase, "", "")
        // Assert
        val observer = payloadDataManager.createHdWallet(pw1, accountName, email).test()
        observer.assertComplete()
        observer.assertNoErrors()

        verify(view).showProgressDialog(any())
        verify(prefsUtil).email = email
        verify(prefsUtil).walletGuid = guid
        verify(prefsUtil).sharedKey = sharedKey
        verify(accessState).isNewlyCreated = true
        verify(view).startPinEntryActivity()
        verify(view).dismissProgressDialog()
        verify(analytics).logEvent(AnalyticsEvents.WalletCreation)
    }

    @Test
    fun `restore wallet`() {
        // Arrange
        val email = "john@snow.com"
        val pw1 = "MyTestWallet"
        val accountName = "AccountName"
        val sharedKey = "SHARED_KEY"
        val guid = "GUID"
        val recoveryPhrase = "all all all all all all all all all all all all"

        whenever(view.getDefaultAccountName()).thenReturn(accountName)
        whenever(payloadDataManager.restoreHdWallet(any(), any(), any(), any()))
            .thenReturn(Single.just(Wallet()))
        whenever(payloadDataManager.wallet!!.guid).thenReturn(guid)
        whenever(payloadDataManager.wallet!!.sharedKey).thenReturn(sharedKey)

        // Act
        subject.createOrRestoreWallet(email, pw1, recoveryPhrase, "", "")

        // Assert
        val observer = payloadDataManager.restoreHdWallet(email, pw1, accountName, recoveryPhrase)
            .test()
        observer.assertComplete()
        observer.assertNoErrors()

        verify(view).showProgressDialog(any())
        verify(prefsUtil).email = email
        verify(prefsUtil).isOnBoardingComplete = true
        verify(prefsUtil).walletGuid = guid
        verify(prefsUtil).sharedKey = sharedKey
        verify(accessState).isNewlyCreated = true
        verify(view).startPinEntryActivity()
        verify(view).dismissProgressDialog()
    }

    @Test
    fun `validateCredentials are valid`() {
        // Arrange
        val email = "john@snow.com"
        val pw1 = "MyTestWallet"
        val pw2 = "MyTestWallet"
        whenever(formatChecker.isValidEmailAddress(anyString())).thenReturn(true)
        // Act
        val result = subject.validateCredentials(email, pw1, pw2)
        // Assert
        assert(result)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials invalid email`() {
        val pw1 = "MyTestWallet"
        // Arrange
        whenever(formatChecker.isValidEmailAddress(anyString())).thenReturn(false)
        // Act
        val result = subject.validateCredentials("john", pw1, pw1)
        // Assert
        assert(!result)
        verify(view).showError(R.string.invalid_email)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials short password`() {
        val pw1 = "aaa"
        // Arrange
        whenever(formatChecker.isValidEmailAddress(anyString())).thenReturn(true)
        // Act
        val result = subject.validateCredentials("john@snow.com", pw1, pw1)
        // Assert
        assert(!result)
        verify(view).showError(R.string.invalid_password_too_short)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials password mismatch`() {
        val pw1 = "MyTestWallet"
        // Arrange
        whenever(formatChecker.isValidEmailAddress(anyString())).thenReturn(true)
        // Act
        val result = subject.validateCredentials("john@snow.com", pw1, "MyTestWallet2")
        // Assert
        assert(!result)
        verify(view).showError(R.string.password_mismatch_error)
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials weak password running on non debug mode`() {
        val pw1 = "aaaaaa"
        // Arrange
        whenever(formatChecker.isValidEmailAddress(anyString())).thenReturn(true)
        whenever(environmentConfig.isRunningInDebugMode()).thenReturn(false)
        // Act
        val result = subject.validateCredentials("john@snow.com", pw1, pw1)
        // Assert
        assert(!result)
        verify(view).warnWeakPassword(any(), any())
        verifyNoMoreInteractions(view)
    }

    @Test
    fun `validateCredentials weak password running on debug mode`() {
        val pw1 = "aaaaaa"
        // Arrange
        whenever(formatChecker.isValidEmailAddress(anyString())).thenReturn(true)
        whenever(environmentConfig.isRunningInDebugMode()).thenReturn(true)
        // Act
        val result = subject.validateCredentials("john@snow.com", pw1, pw1)
        // Assert
        assert(result)
        verifyZeroInteractions(view)
    }

    @Test
    fun `validateGeolocation country != US is selected`() {
        val result = subject.validateGeoLocation("DE")

        assert(result)
        verifyZeroInteractions(view)
    }

    @Test
    fun `validateGeolocation country not selected`() {
        val result = subject.validateGeoLocation()

        assert(!result)
        verify(view).showError(R.string.country_not_selected)
        verifyZeroInteractions(view)
    }

    @Test
    fun `validateGeolocation country == US is selected and state is not selected`() {
        val result = subject.validateGeoLocation("US")

        assert(!result)
        verify(view).showError(R.string.state_not_selected)
        verifyZeroInteractions(view)
    }
}
