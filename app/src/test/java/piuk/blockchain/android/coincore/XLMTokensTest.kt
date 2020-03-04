package piuk.blockchain.android.coincore

import com.blockchain.android.testutils.rxInit
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.sunriver.HorizonKeyPair
import com.blockchain.sunriver.XlmDataManager
import com.blockchain.sunriver.models.XlmTransaction
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Single
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.ui.account.ItemAccount
import piuk.blockchain.androidcore.data.charts.ChartsDataManager
import piuk.blockchain.androidcore.data.currency.CurrencyState
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class XLMTokensTest {

    private val currencyState: CurrencyState = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val historicRates: ChartsDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val rxBus: RxBus = spy()

    private val xlmDataManager: XlmDataManager = mock()

    private val subject: AssetTokensBase = XLMTokens(
        xlmDataManager = xlmDataManager,
        exchangeRates = exchangeRates,
        historicRates = historicRates,
        currencyPrefs = currencyPrefs,
        custodialWalletManager = custodialWalletManager,
        rxBus = rxBus
    )

    @get:Rule
    val rxSchedulers = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setup() {
        whenever(currencyPrefs.selectedFiatCurrency).thenReturn("USD")
        whenever(currencyState.cryptoCurrency).thenReturn(CryptoCurrency.XLM)
    }

    @Test
    fun getXlmTransactionList() {
        // Arrange
        val output = BigInteger.valueOf(1000000L)
        val xlmTransaction = XlmTransaction(
            "2018-10-11T12:54:15Z",
            CryptoValue.lumensFromStroop(output),
            CryptoValue.lumensFromStroop(BigInteger.valueOf(100)),
            "hash",
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_1),
            HorizonKeyPair.Public(HORIZON_ACCOUNT_ID_2)
        )

        whenever(xlmDataManager.getTransactionList())
            .thenReturn(Single.just(listOf(xlmTransaction)))

        val itemAccount = ItemAccount(
            "XLM",
            "1.0 XLM",
            null,
            1L, null,
            "AccountID"
        )

        // Act
        val test = subject.doFetchActivity(itemAccount).test()

        verify(xlmDataManager).getTransactionList()

        val result = test.values()[0]
        assertEquals(1, result.size.toLong())

        val activityItem = result[0]
        assertEquals(CryptoCurrency.XLM, activityItem.cryptoCurrency)
        assertEquals("hash", activityItem.hash)
        assertEquals(TransactionSummary.Direction.RECEIVED, activityItem.direction)
        assertEquals(1, activityItem.confirmations.toLong())
        assertFalse(activityItem.isFeeTransaction)
        assertEquals(output, activityItem.totalCrypto.amount)
        assertEquals(
            mapOf(HORIZON_ACCOUNT_ID_2 to CryptoValue.fromMinor(CryptoCurrency.XLM, BigInteger.ZERO)),
            activityItem.inputsMap
        )
        assertEquals(
            mapOf(HORIZON_ACCOUNT_ID_1 to CryptoValue.fromMinor(CryptoCurrency.XLM, output)),
            activityItem.outputsMap
        )
    }

    companion object {
        private const val HORIZON_ACCOUNT_ID_1 =
            "GAIH3ULLFQ4DGSECF2AR555KZ4KNDGEKN4AFI4SU2M7B43MGK3QJZNSR"
        private const val HORIZON_ACCOUNT_ID_2 =
            "GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4"
    }
}