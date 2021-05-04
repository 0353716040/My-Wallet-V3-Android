package piuk.blockchain.android.coincore.xlm

import com.blockchain.sunriver.models.XlmTransaction
import com.blockchain.utils.fromIso8601ToUtc
import com.blockchain.utils.toLocalTime
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Observable
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.lang.IllegalStateException

class XlmActivitySummaryItem(
    private val xlmTransaction: XlmTransaction,
    override val exchangeRates: ExchangeRateDataManager,
    override val account: CryptoAccount
) : NonCustodialActivitySummaryItem() {
    override val cryptoCurrency = CryptoCurrency.XLM

    override val transactionType: TransactionSummary.TransactionType
        get() = if (xlmTransaction.value > CryptoValue.zero(CryptoCurrency.XLM)) {
            TransactionSummary.TransactionType.RECEIVED
        } else {
            TransactionSummary.TransactionType.SENT
        }

    override val timeStampMs: Long
        get() = xlmTransaction.timeStamp.fromIso8601ToUtc()?.toLocalTime()?.time ?: throw IllegalStateException(
            "xlm timeStamp not found"
        )

    override val value: CryptoValue by unsafeLazy {
        xlmTransaction.accountDelta.abs()
    }

    override val description: String? = null

    override val fee: Observable<CryptoValue>
        get() = Observable.just(xlmTransaction.fee)

    override val txId: String
        get() = xlmTransaction.hash

    override val inputsMap: Map<String, CryptoValue>
        get() = hashMapOf(xlmTransaction.from.accountId to CryptoValue.zero(CryptoCurrency.XLM))

    override val outputsMap: Map<String, CryptoValue>
        get() = hashMapOf(
            xlmTransaction.to.accountId to value
        )

    override val confirmations: Int
        get() = CryptoCurrency.XLM.requiredConfirmations

    val xlmMemo: String
        get() = xlmTransaction.memo.value
}
