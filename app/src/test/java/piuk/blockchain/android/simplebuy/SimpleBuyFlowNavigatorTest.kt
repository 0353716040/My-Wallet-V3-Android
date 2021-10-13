package piuk.blockchain.android.simplebuy

import com.blockchain.core.price.ExchangeRate
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.service.TierService
import com.blockchain.preferences.CurrencyPrefs
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.CryptoCurrency
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.tiers

class SimpleBuyFlowNavigatorTest {

    private val simpleBuyModel: SimpleBuyModel = mock()
    private val tierService: TierService = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val assetCatalogue: AssetCatalogue = mock()
    private val exchangeRates: ExchangeRatesDataManager = mock()
    private lateinit var subject: SimpleBuyFlowNavigator

    @Before
    fun setUp() {
        subject = SimpleBuyFlowNavigator(
            simpleBuyModel, tierService, currencyPrefs, custodialWalletManager, exchangeRates
        )
    }

    @Test
    fun `if currency is not  supported  and startedFromDashboard then screen should be currency selector`() {
        mockCurrencyIsSupported(false)
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))
        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf("GBP,EUR")))

        val test =
            subject.navigateTo(
                startedFromKycResume = false, startedFromDashboard = true, startedFromApprovalDeepLink = false,
                preselectedCrypto = null
            )
                .test()
        test.assertValueAt(0, BuyNavigation.CurrencySelection(listOf("GBP,EUR")))
    }

    @Test
    fun `if currency is  supported and state is clear and startedFromDashboard then screen should be enter amount`() {
        mockCurrencyIsSupported(true)
        whenever(exchangeRates.cryptoToUserFiatRate(CryptoCurrency.BTC))
            .thenReturn(Observable.just(btcExchangeRate))
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                startedFromApprovalDeepLink = false,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(0, BuyNavigation.FlowScreenWithCurrency(FlowScreen.ENTER_AMOUNT, CryptoCurrency.BTC))
    }

    @Test
    fun `if currency is supported and state is clear and startedFromApprovalDeepLink then screen should be payment`() {
        mockCurrencyIsSupported(true)
        whenever(exchangeRates.cryptoToUserFiatRate(CryptoCurrency.BTC))
            .thenReturn(Observable.just(btcExchangeRate))
        whenever(simpleBuyModel.state).thenReturn(Observable.just(SimpleBuyState()))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = false,
                startedFromApprovalDeepLink = true,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(0, BuyNavigation.OrderInProgressScreen)
    }

    // KYC tests
    @Test
    fun `if  current is screen is KYC and tier 2 approved then screen should be enter amount`() {
        mockCurrencyIsSupported(true)
        whenever(exchangeRates.cryptoToUserFiatRate(CryptoCurrency.BTC))
            .thenReturn(Observable.just(btcExchangeRate))
        whenever(simpleBuyModel.state)
            .thenReturn(
                Observable.just(
                    SimpleBuyState().copy(currentScreen = FlowScreen.KYC)
                )
            )
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.Verified)))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                startedFromApprovalDeepLink = false,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(0, BuyNavigation.FlowScreenWithCurrency(FlowScreen.ENTER_AMOUNT, CryptoCurrency.BTC))
    }

    @Test
    fun `if  current is screen is KYC and tier 2 is pending then screen should be kyc verification`() {
        mockCurrencyIsSupported(true)
        whenever(exchangeRates.cryptoToUserFiatRate(CryptoCurrency.BTC))
            .thenReturn(Observable.just(btcExchangeRate))
        whenever(simpleBuyModel.state)
            .thenReturn(
                Observable.just(
                    SimpleBuyState().copy(currentScreen = FlowScreen.KYC)
                )
            )
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.Pending)))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                startedFromApprovalDeepLink = false,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(
            0,
            BuyNavigation.FlowScreenWithCurrency(FlowScreen.KYC_VERIFICATION, CryptoCurrency.BTC)
        )
    }

    @Test
    fun `if  current is screen is KYC and tier 2 is none then screen should be kyc`() {
        mockCurrencyIsSupported(true)
        whenever(exchangeRates.cryptoToUserFiatRate(CryptoCurrency.BTC))
            .thenReturn(Observable.just(btcExchangeRate))
        whenever(simpleBuyModel.state)
            .thenReturn(
                Observable.just(
                    SimpleBuyState().copy(currentScreen = FlowScreen.KYC)
                )
            )
        whenever(tierService.tiers()).thenReturn(Single.just(tiers(KycTierState.Verified, KycTierState.None)))

        val test =
            subject.navigateTo(
                startedFromKycResume = false,
                startedFromDashboard = true,
                startedFromApprovalDeepLink = false,
                preselectedCrypto = CryptoCurrency.BTC
            ).test()
        test.assertValueAt(
            0,
            BuyNavigation.FlowScreenWithCurrency(FlowScreen.KYC, CryptoCurrency.BTC)
        )
    }

    private fun mockCurrencyIsSupported(supported: Boolean) {
        whenever(
            custodialWalletManager
                .isCurrencySupportedForSimpleBuy("GBP")
        ).thenReturn(Single.just(supported))
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn(("GBP"))
    }

    companion object {
        private val btcExchangeRate = ExchangeRate.FiatToCrypto("GBP", CryptoCurrency.BTC, null)
    }
}