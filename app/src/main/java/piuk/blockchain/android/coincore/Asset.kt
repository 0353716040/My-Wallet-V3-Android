package piuk.blockchain.android.coincore

import info.blockchain.balance.AssetInfo
import info.blockchain.balance.ExchangeRate
import info.blockchain.wallet.prices.TimeInterval
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.androidcore.data.exchangerate.PriceSeries
import piuk.blockchain.androidcore.data.exchangerate.TimeSpan

enum class AssetFilter {
    All,
    NonCustodial,
    Custodial,
    Interest
}

enum class AssetAction {
    ViewActivity,
    Send,
    Withdraw,
    Receive,
    Swap,
    Sell,
    Summary,
    InterestDeposit,
    InterestWithdraw,
    FiatDeposit,
    Buy
}

typealias AvailableActions = Set<AssetAction>

interface Asset {
    fun init(): Completable
    val isEnabled: Boolean

    fun accountGroup(filter: AssetFilter = AssetFilter.All): Maybe<AccountGroup>

    fun transactionTargets(account: SingleAccount): Single<SingleAccountList>

    fun parseAddress(address: String, label: String? = null): Maybe<ReceiveAddress>
    fun isValidAddress(address: String): Boolean = false
}

interface CryptoAsset : Asset {
    val asset: AssetInfo

    fun defaultAccount(): Single<SingleAccount>
    fun interestRate(): Single<Double>

    // Fetch exchange rate to user's selected/display fiat
    fun exchangeRate(): Single<ExchangeRate>
    fun historicRate(epochWhen: Long): Single<ExchangeRate>
    fun historicRateSeries(period: TimeSpan, interval: TimeInterval): Single<PriceSeries>

    // Temp feature accessors - this will change, but until it's building these have to be somewhere
    val isCustodialOnly: Boolean
    val multiWallet: Boolean
}
