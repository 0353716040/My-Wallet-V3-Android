package piuk.blockchain.android.coincore.pax

import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.impl.CryptoSingleAccountCustodialBase
import piuk.blockchain.android.coincore.impl.CryptoSingleAccountNonCustodialBase
import piuk.blockchain.androidcore.data.erc20.Erc20Account
import piuk.blockchain.androidcore.data.erc20.FeedErc20Transfer
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.extensions.mapList

internal class PaxCryptoAccountCustodial(
    override val label: String,
    override val custodialWalletManager: CustodialWalletManager,
    override val exchangeRates: ExchangeRateDataManager
) : CryptoSingleAccountCustodialBase() {
    override val cryptoCurrencies = setOf(CryptoCurrency.PAX)
}

internal class PaxCryptoAccountNonCustodial(
    override val label: String,
    private val address: String,
    private val paxAccount: Erc20Account,
    override val exchangeRates: ExchangeRateDataManager
) : CryptoSingleAccountNonCustodialBase() {
    override val isDefault: Boolean = true // Only one account, so always default

    override val cryptoCurrencies = setOf(CryptoCurrency.PAX)

    override val balance: Single<CryptoValue>
        get() = paxAccount.getBalance()
            .map { CryptoValue.fromMinor(CryptoCurrency.PAX, it) }

    override val receiveAddress: Single<String>
        get() = Single.just(address)

    override val activity: Single<ActivitySummaryList>
        get() {
            val ethDataManager = paxAccount.ethDataManager

            val feedTransactions =
                paxAccount.fetchErc20Address()
                    .flatMap { paxAccount.getTransactions() }
                    .mapList {
                        val feeObservable = ethDataManager
                            .getTransaction(it.transactionHash)
                            .map { transaction ->
                                transaction.gasUsed * transaction.gasPrice
                            }
                        FeedErc20Transfer(it, feeObservable)
                    }

            return Singles.zip(
                feedTransactions,
                paxAccount.getAccountHash(),
                ethDataManager.getLatestBlockNumber().singleOrError()
            ).map { (transactions, accountHash, latestBlockNumber) ->
                transactions.map { transaction ->
                    PaxActivitySummaryItem(
                        feedTransfer = transaction,
                        accountHash = accountHash,
                        ethDataManager = ethDataManager,
                        exchangeRates = exchangeRates,
                        lastBlockNumber = latestBlockNumber.number,
                        account = this
                    ) as ActivitySummaryItem
                }
            }
            .doOnSuccess { setHasTransactions(it.isNotEmpty()) }
        }
}
