package piuk.blockchain.android.coincore.erc20.pax

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.prices.TimeInterval
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.CryptoSingleAccount
import piuk.blockchain.android.coincore.CryptoSingleAccountList
import piuk.blockchain.android.coincore.erc20.Erc20TokensBase
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.charts.ChartsDataManager
import piuk.blockchain.androidcore.data.charts.PriceSeries
import piuk.blockchain.androidcore.data.charts.TimeSpan
import piuk.blockchain.androidcore.data.erc20.Erc20Account
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

internal class PaxTokens(
    override val asset: CryptoCurrency = CryptoCurrency.PAX,
    private val erc20Account: Erc20Account,
    custodialManager: CustodialWalletManager,
    exchangeRates: ExchangeRateDataManager,
    historicRates: ChartsDataManager,
    currencyPrefs: CurrencyPrefs,
    labels: DefaultLabels,
    pitLinking: PitLinking,
    crashLogger: CrashLogger
) : Erc20TokensBase(
    erc20Account,
    custodialManager,
    exchangeRates,
    historicRates,
    currencyPrefs,
    labels,
    pitLinking,
    crashLogger
) {

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<CryptoSingleAccountList> =
        Single.just(listOf(getNonCustodialPaxAccount()))

    private fun getNonCustodialPaxAccount(): CryptoSingleAccount {
        val paxAddress = erc20Account.ethDataManager.getEthWallet()?.account?.address
            ?: throw Exception("No PAX wallet found")

        return PaxCryptoWalletAccount(
            labels.getDefaultNonCustodialWalletLabel(CryptoCurrency.PAX), paxAddress, erc20Account,
            exchangeRates)
    }

    override fun historicRateSeries(period: TimeSpan, interval: TimeInterval): Single<PriceSeries> =
        Single.just(emptyList())

    override fun parseAddress(address: String): CryptoAddress? =
        null

    private fun isValidAddress(address: String): Boolean =
        FormatsUtil.isValidEthereumAddress(address)
}

internal class PaxAddress(
    override val address: String,
    override val label: String = address
) : CryptoAddress {
    override val asset: CryptoCurrency = CryptoCurrency.PAX
}