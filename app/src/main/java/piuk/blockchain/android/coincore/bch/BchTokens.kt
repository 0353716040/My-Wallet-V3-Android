package piuk.blockchain.android.coincore.bch

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Completable
import io.reactivex.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CryptoSingleAccount
import piuk.blockchain.android.coincore.CryptoSingleAccountList
import piuk.blockchain.android.coincore.impl.BitcoinLikeTokens
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.access.AuthEvent
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.charts.ChartsDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.rxjava.RxBus
import piuk.blockchain.androidcore.utils.extensions.then
import timber.log.Timber

internal class BchTokens(
    private val bchDataManager: BchDataManager,
    private val stringUtils: StringUtils,
    custodialManager: CustodialWalletManager,
    private val environmentSettings: EnvironmentConfig,
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
        get() = CryptoCurrency.BCH

    override fun initToken(): Completable =
        bchDataManager.initBchWallet(stringUtils.getString(R.string.bch_default_account_label))
            .then { updater() }
            .doOnError { Timber.e("Unable to init BCH, because: $it") }
            .onErrorComplete()

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<CryptoSingleAccountList> =
        Single.fromCallable {
            with(bchDataManager) {
                val result = mutableListOf<CryptoSingleAccount>()
                val defaultIndex = getDefaultAccountPosition()

                val accounts = getAccountMetadataList()
                accounts.forEachIndexed { i, a ->
                    result.add(
                        BchCryptoAccountNonCustodial(
                            a,
                            bchDataManager,
                            i == defaultIndex,
                            exchangeRates,
                            environmentSettings.bitcoinCashNetworkParameters
                        )
                    )
                }
                result
            }
        }

    override fun doUpdateBalances(): Completable =
        bchDataManager.updateAllBalances()
            .doOnComplete { Timber.d("Got btc balance") }

    override fun onLogoutSignal(event: AuthEvent) {
        if (event != AuthEvent.LOGIN) {
            bchDataManager.clearBchAccountDetails()
        }
        super.onLogoutSignal(event)
    }
}
