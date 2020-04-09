package piuk.blockchain.android.coincore.xlm

import com.blockchain.sunriver.models.XlmTransaction
import com.blockchain.swap.nabu.extensions.fromIso8601ToUtc
import com.blockchain.swap.nabu.extensions.toLocalTime
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.compareTo
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Observable
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class XlmActivitySummaryItem(
    private val xlmTransaction: XlmTransaction,
    override val exchangeRates: ExchangeRateDataManager
) : NonCustodialActivitySummaryItem() {
    override val cryptoCurrency = CryptoCurrency.XLM

    override val direction: TransactionSummary.Direction
        get() = if (xlmTransaction.value > CryptoValue.ZeroXlm) {
            TransactionSummary.Direction.RECEIVED
        } else {
            TransactionSummary.Direction.SENT
        }

    override val timeStampMs: Long
        get() = xlmTransaction.timeStamp.fromIso8601ToUtc()!!.toLocalTime().time

    override val totalCrypto: CryptoValue by unsafeLazy {
        CryptoValue.fromMinor(CryptoCurrency.XLM, xlmTransaction.accountDelta.amount.abs())
    }

    override val description: String? = null

    override val fee: Observable<CryptoValue>
        get() = Observable.just(
            CryptoValue.fromMinor(CryptoCurrency.XLM, xlmTransaction.fee.amount)
        )

    override val txId: String
        get() = xlmTransaction.hash

    override val inputsMap: Map<String, CryptoValue>
        get() = hashMapOf(xlmTransaction.from.accountId to CryptoValue.ZeroXlm)

    override val outputsMap: Map<String, CryptoValue>
        get() = hashMapOf(xlmTransaction.to.accountId to CryptoValue.fromMinor(CryptoCurrency.XLM, totalCrypto.amount))

    override val confirmations: Int
        get() = CryptoCurrency.XLM.requiredConfirmations
}
