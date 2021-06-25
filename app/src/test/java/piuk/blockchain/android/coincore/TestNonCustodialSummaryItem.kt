package piuk.blockchain.android.coincore

import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Observable
import org.amshove.kluent.mock
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

internal class TestNonCustodialSummaryItem(
    override val exchangeRates: ExchangeRateDataManager = mock(),
    override val asset: AssetInfo = CryptoCurrency.BTC,
    override val transactionType: TransactionSummary.TransactionType = TransactionSummary.TransactionType.RECEIVED,
    override val timeStampMs: Long = 0,
    override val value: CryptoValue = CryptoValue.zero(CryptoCurrency.BTC),
    override val fee: Observable<CryptoValue> = Observable.just(CryptoValue.zero(CryptoCurrency.BTC)),
    override val txId: String = "",
    override val inputsMap: Map<String, CryptoValue> = emptyMap(),
    override val outputsMap: Map<String, CryptoValue> = emptyMap(),
    override val description: String? = null,
    override val confirmations: Int = 0,
    override val isFeeTransaction: Boolean = false,
    override val account: CryptoAccount = mock()
) : NonCustodialActivitySummaryItem()