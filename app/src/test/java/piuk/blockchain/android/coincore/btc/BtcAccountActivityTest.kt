package piuk.blockchain.android.coincore.btc

import com.blockchain.android.testutils.rxInit
import com.blockchain.preferences.CurrencyPrefs
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.payload.PayloadManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.data.currency.CurrencyState
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import java.math.BigInteger

class BtcAccountActivityTest {

    private val payloadManager: PayloadManager = mock()
    private val currencyState: CurrencyState = mock()
    private val payloadDataManager: PayloadDataManager = mock()
    private val exchangeRates: ExchangeRateDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()

    private val subject =
        BtcCryptoAccountNonCustodial(
            label = "TestBtcAccount",
            address = "",
            payloadManager = payloadManager,
            payloadDataManager = payloadDataManager,
            isDefault = true,
            exchangeRates = exchangeRates,
            txCache = mock()
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
        whenever(currencyState.cryptoCurrency).thenReturn(CryptoCurrency.BTC)
    }

    @Test
    fun fetchTransactionsOnAccount() {

        val summary = TransactionSummary().apply {
            confirmations = 3
            direction = TransactionSummary.Direction.RECEIVED
            fee = BigInteger.ONE
            total = BigInteger.TEN
            hash = "hash"
            inputsMap = HashMap()
            outputsMap = HashMap()
            time = 1000000L
        }

        val transactionSummaries = listOf(summary)

        whenever(payloadManager.getAccountTransactions(any(), any(), any()))
            .thenReturn(transactionSummaries)

        subject.activity
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(payloadManager).getAccountTransactions(any(), any(), any())

        // TODO: Validate returned list
    }
}
