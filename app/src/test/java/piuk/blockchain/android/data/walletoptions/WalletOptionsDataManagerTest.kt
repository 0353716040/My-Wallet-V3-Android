package piuk.blockchain.android.data.walletoptions

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.api.data.ShapeShiftOptions
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.Observable
import io.reactivex.subjects.ReplaySubject
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import piuk.blockchain.android.RxTest
import piuk.blockchain.android.data.api.EnvironmentSettings
import piuk.blockchain.android.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import kotlin.test.assertEquals

@Suppress("IllegalIdentifier")
class WalletOptionsDataManagerTest : RxTest() {

    private lateinit var subject: WalletOptionsDataManager

    private val mockAuthDataManager: AuthDataManager = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private var walletOptionsState = WalletOptionsState.getInstance(
            ReplaySubject.create(1),
            ReplaySubject.create(1))
    private val mockSettingsDataManager: SettingsDataManager = mock(defaultAnswer = Mockito.RETURNS_DEEP_STUBS)
    private val environmentSettings: EnvironmentSettings = mock()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        walletOptionsState.destroy()
        walletOptionsState = WalletOptionsState.getInstance(
                ReplaySubject.create(1),
                ReplaySubject.create(1))
        subject = WalletOptionsDataManager(mockAuthDataManager, walletOptionsState, mockSettingsDataManager, environmentSettings)
    }

    @Test
    @Throws(Exception::class)
    fun `showShapeshift flag false`() {

        val showShapeshiftFlag = false

        // Arrange
        //Shapeshift flag
        val walletOptions: WalletOptions = mock()
        val shapeshift: ShapeShiftOptions = mock()
        val flagmap = hashMapOf("showShapeshift" to showShapeshiftFlag)
        whenever(walletOptions.androidFlags).thenReturn(flagmap)
        whenever(walletOptions.shapeshift).thenReturn(shapeshift)
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))

        //Country code
        val settings: Settings = mock()
        whenever(settings.countryCode).thenReturn("GB")
        whenever(mockSettingsDataManager.getSettings()).thenReturn(Observable.just(settings))

        //State code - none

        // Act
        val testObserver = subject.showShapeshift("", "").test()
        // Assert
        assertEquals(showShapeshiftFlag, testObserver.values()[0])
        testObserver.assertComplete()
    }

    @Test
    @Throws(Exception::class)
    fun `showShapeshift flagtrue`() {

        val showShapeshiftFlag = true

        // Arrange
        //Shapeshift flag
        val walletOptions: WalletOptions = mock()
        val shapeshift: ShapeShiftOptions = mock()
        val flagmap = hashMapOf("showShapeshift" to showShapeshiftFlag)
        whenever(walletOptions.androidFlags).thenReturn(flagmap)
        whenever(walletOptions.shapeshift).thenReturn(shapeshift)
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))

        //Country code
        val settings: Settings = mock()
        whenever(settings.countryCode).thenReturn("GB")
        whenever(mockSettingsDataManager.getSettings()).thenReturn(Observable.just(settings))

        //State code - none

        // Act
        val testObserver = subject.showShapeshift("", "").test()
        // Assert
        assertEquals(showShapeshiftFlag, testObserver.values()[0])
        testObserver.assertComplete()
    }

    @Test
    @Throws(Exception::class)
    fun `showShapeshift flag true blacklisted country`() {

        val showShapeshiftFlag = true

        // Arrange
        //Shapeshift flag
        val walletOptions: WalletOptions = mock()
        val shapeshift: ShapeShiftOptions = mock()
        val flagmap = hashMapOf("showShapeshift" to showShapeshiftFlag)
        whenever(walletOptions.androidFlags).thenReturn(flagmap)
        whenever(walletOptions.shapeshift).thenReturn(shapeshift)
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))

        //Country code
        val settings: Settings = mock()
        whenever(settings.countryCode).thenReturn("DE")
        whenever(mockSettingsDataManager.getSettings()).thenReturn(Observable.just(settings))
        //Blacklist me
        whenever(shapeshift.countriesBlacklist).thenReturn(listOf("GB", "DE"))

        //State code - none

        // Act
        val testObserver = subject.showShapeshift("", "").test()
        // Assert
        assertEquals(false, testObserver.values()[0])
        testObserver.assertComplete()
    }


    @Test
    @Throws(Exception::class)
    fun `checkForceUpgrade missing androidUpgrade JSON object`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        val versionCode = 360
        val sdk = 16
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionCode, sdk).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    @Throws(Exception::class)
    fun `checkForceUpgrade empty androidUpgrade JSON object`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpgrade).thenReturn(emptyMap())
        val versionCode = 360
        val sdk = 16
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionCode, sdk).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    @Throws(Exception::class)
    fun `checkForceUpgrade ignore minSdk despite versionCode unsupported`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpgrade).thenReturn(mapOf(
                "minSdk" to 18,
                "minVersionCode" to 361
        ))
        val versionCode = 360
        val sdk = 16
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionCode, sdk).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    @Throws(Exception::class)
    fun `checkForceUpgrade versionCode supported, minSdk lower than supplied`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpgrade).thenReturn(mapOf(
                "minSdk" to 18,
                "minVersionCode" to 360
        ))
        val versionCode = 360
        val sdk = 21
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionCode, sdk).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    @Throws(Exception::class)
    fun `checkForceUpgrade should force upgrade`() {
        // Arrange
        val walletOptions: WalletOptions = mock()
        whenever(walletOptions.androidUpgrade).thenReturn(mapOf(
                "minSdk" to 16,
                "minVersionCode" to 361
        ))
        val versionCode = 360
        val sdk = 16
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(walletOptions))
        // Act
        val testObserver = subject.checkForceUpgrade(versionCode, sdk).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(true)
    }

    @Test
    @Throws(Exception::class)
    fun `getBuyWebviewWalletLink wallet-options set`() {
        // Arrange
        val walletOptionsRoot = "https://blockchain.com/wallet"
        val environmentRoot = "https://blockchain.info/"

        val mockOptions: WalletOptions = mock()
        whenever(mockOptions.buyWebviewWalletLink).thenReturn(walletOptionsRoot)
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(mockOptions))
        whenever(environmentSettings.explorerUrl).thenReturn(environmentRoot)

        // Act
        val result = subject.getBuyWebviewWalletLink()

        // Assert
        assertEquals("https://blockchain.com/wallet/#/intermediate", result)
    }

    @Test
    @Throws(Exception::class)
    fun `getBuyWebviewWalletLink wallet-options unset`() {
        // Arrange
        val walletOptionsRoot = null
        val environmentRoot = "https://blockchain.info/"

        val mockOptions: WalletOptions = mock()
        whenever(mockOptions.buyWebviewWalletLink).thenReturn(walletOptionsRoot)
        whenever(mockAuthDataManager.walletOptions).thenReturn(Observable.just(mockOptions))
        whenever(environmentSettings.explorerUrl).thenReturn(environmentRoot)
        // Act
        val result = subject.getBuyWebviewWalletLink()

        // Assert
        assertEquals("https://blockchain.info/wallet/#/intermediate", result)
    }
}
