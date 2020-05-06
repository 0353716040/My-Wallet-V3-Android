package piuk.blockchain.android.coincore.pax

import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.Completable
import io.reactivex.Observable
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import piuk.blockchain.androidcore.data.erc20.Erc20Transfer
import piuk.blockchain.androidcore.data.erc20.FeedErc20Transfer
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.math.BigInteger

internal class PaxActivitySummaryItem(
    private val feedTransfer: FeedErc20Transfer,
    private val accountHash: String,
    private val ethDataManager: EthDataManager,
    override val exchangeRates: ExchangeRateDataManager,
    lastBlockNumber: BigInteger
) : NonCustodialActivitySummaryItem() {

    override val cryptoCurrency = CryptoCurrency.PAX

    private val transfer: Erc20Transfer = feedTransfer.transfer

    override val direction: TransactionSummary.Direction by unsafeLazy {
        when {
            transfer.isToAccount(accountHash)
                && transfer.isFromAccount(accountHash) -> TransactionSummary.Direction.TRANSFERRED
            transfer.isFromAccount(accountHash) -> TransactionSummary.Direction.SENT
            else -> TransactionSummary.Direction.RECEIVED
        }
    }

    override val timeStampMs: Long = transfer.timestamp * 1000

    override val cryptoValue: CryptoValue by unsafeLazy {
        CryptoValue.fromMinor(CryptoCurrency.PAX, transfer.value)
    }

    override val description: String?
        get() = ethDataManager.getErc20TokenData(CryptoCurrency.PAX).txNotes[txId]

    override val fee: Observable<CryptoValue>
        get() = feedTransfer.feeObservable
            .map { CryptoValue.etherFromWei(it) }

    override val txId: String = transfer.transactionHash

    override val inputsMap: Map<String, CryptoValue> =
        mapOf(transfer.from to CryptoValue.fromMinor(CryptoCurrency.PAX, transfer.value))

    override val outputsMap: Map<String, CryptoValue> =
        mapOf(transfer.to to CryptoValue.fromMinor(CryptoCurrency.PAX, transfer.value))

    override val confirmations: Int = (lastBlockNumber - transfer.blockNumber).toInt()

    override fun updateDescription(description: String): Completable =
        ethDataManager.updateErc20TransactionNotes(txId, description)
}
