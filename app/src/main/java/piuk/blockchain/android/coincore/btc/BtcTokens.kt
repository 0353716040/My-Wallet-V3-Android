package piuk.blockchain.android.coincore.btc

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.payload.PayloadManager
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoSingleAccount
import piuk.blockchain.android.coincore.CryptoSingleAccountList
import piuk.blockchain.android.coincore.impl.BitcoinLikeTokens
import piuk.blockchain.androidcore.data.charts.ChartsDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus

internal class BtcTokens(
    private val payloadDataManager: PayloadDataManager,
    private val payloadManager: PayloadManager,
    custodialManager: CustodialWalletManager,
    exchangeRates: ExchangeRateDataManager,
    historicRates: ChartsDataManager,
    currencyPrefs: CurrencyPrefs,
    labels: DefaultLabels,
    crashLogger: CrashLogger,
    rxBus: RxBus
) : BitcoinLikeTokens(
    exchangeRates,
    historicRates,
    custodialManager,
    currencyPrefs,
    labels,
    crashLogger,
    rxBus
) {

    override val asset: CryptoCurrency
        get() = CryptoCurrency.BTC

    override fun initToken(): Completable =
        updater()

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<CryptoSingleAccountList> =
        Single.fromCallable {
            with(payloadDataManager) {
                val result = mutableListOf<CryptoSingleAccount>()
                val defaultIndex = defaultAccountIndex
                accounts.forEachIndexed { i, a ->
                    result.add(
                        BtcCryptoWalletAccount(
                            a,
                            payloadManager,
                            payloadDataManager,
                            i == defaultIndex,
                            exchangeRates
                        )
                    )
                }

                legacyAddresses.forEach { a ->
                    result.add(
                        BtcCryptoWalletAccount(
                            a,
                            payloadManager,
                            payloadDataManager,
                            exchangeRates
                        )
                    )
                }
                result
            }
        }

    override fun interestRate(): Maybe<Double> = TODO()

    override fun doUpdateBalances(): Completable =
        payloadDataManager.updateAllBalances()
}
