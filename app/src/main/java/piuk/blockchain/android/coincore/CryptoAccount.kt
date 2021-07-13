package piuk.blockchain.android.coincore

import com.blockchain.nabu.datamanagers.repositories.interest.IneligibilityReason
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRates
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount

interface BlockchainAccount {

    val label: String

    val accountBalance: Single<Money> // Total balance, including uncleared and locked

    // Available balance, not including uncleared and locked, that may be used for transactions
    val actionableBalance: Single<Money>

    val pendingBalance: Single<Money>

    val activity: Single<ActivitySummaryList>

    val actions: Single<AvailableActions>

    val isFunded: Boolean

    val hasTransactions: Boolean

    val isEnabled: Single<Boolean>

    val disabledReason: Single<IneligibilityReason>

    fun fiatBalance(fiatCurrency: String, exchangeRates: ExchangeRates): Single<Money>

    val receiveAddress: Single<ReceiveAddress>

    fun requireSecondPassword(): Single<Boolean> = Single.just(false)
}

interface SingleAccount : BlockchainAccount, TransactionTarget {
    val isDefault: Boolean

    // Is this account currently able to operate as a transaction source
    val sourceState: Single<TxSourceState>

    fun doesAddressBelongToWallet(address: String): Boolean = false
}

enum class TxSourceState {
    CAN_TRANSACT,
    NO_FUNDS,
    FUNDS_LOCKED,
    NOT_ENOUGH_GAS,
    TRANSACTION_IN_FLIGHT,
    NOT_SUPPORTED
}

interface InterestAccount
interface TradingAccount
interface NonCustodialAccount
interface BankAccount

typealias SingleAccountList = List<SingleAccount>

interface CryptoAccount : SingleAccount {
    val asset: AssetInfo

    val isArchived: Boolean
        get() = false

    override val pendingBalance: Single<Money>
        get() = Single.just(CryptoValue.zero(asset))

    fun matches(other: CryptoAccount): Boolean

    val hasStaticAddress: Boolean
        get() = true
}

interface FiatAccount : SingleAccount {
    val fiatCurrency: String
    override val pendingBalance: Single<Money>
        get() = Single.just(FiatValue.zero(fiatCurrency))

    fun canWithdrawFunds(): Single<Boolean>
}

interface AccountGroup : BlockchainAccount {
    val accounts: SingleAccountList

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<IneligibilityReason>
        get() = Single.just(IneligibilityReason.NONE)

    fun includes(account: BlockchainAccount): Boolean
}

internal fun BlockchainAccount.isCustodial(): Boolean =
    this is CustodialTradingAccount

object NullCryptoAddress : CryptoAddress {
    override val asset: AssetInfo = CryptoCurrency.BTC
    override val label: String = ""
    override val address = ""
}

// Stub invalid accounts; use as an initialisers to avoid nulls.
class NullCryptoAccount(
    override val label: String = ""
) : CryptoAccount {
    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(NullAddress)

    override val isDefault: Boolean
        get() = false

    override val asset: AssetInfo
        get() = CryptoCurrency.BTC

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.NOT_SUPPORTED)

    override val accountBalance: Single<Money>
        get() = Single.just(CryptoValue.zero(asset))

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val activity: Single<ActivitySummaryList>
        get() = Single.just(emptyList())

    override val actions: Single<AvailableActions> = Single.just(emptySet())

    override val isFunded: Boolean = false

    override val hasTransactions: Boolean = false

    override fun requireSecondPassword(): Single<Boolean> = Single.just(false)

    override fun matches(other: CryptoAccount): Boolean =
        other is NullCryptoAccount

    override fun fiatBalance(
        fiatCurrency: String,
        exchangeRates: ExchangeRates
    ): Single<Money> =
        Single.just(FiatValue.zero(fiatCurrency))

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<IneligibilityReason>
        get() = Single.just(IneligibilityReason.NONE)
}

object NullFiatAccount : FiatAccount {
    override val fiatCurrency: String = "NULL"

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(NullAddress)

    override val isDefault: Boolean
        get() = false

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.NOT_SUPPORTED)

    override val label: String = ""

    override val accountBalance: Single<Money>
        get() = Single.just(FiatValue.zero(fiatCurrency))

    override val actionableBalance: Single<Money>
        get() = accountBalance

    override val activity: Single<ActivitySummaryList>
        get() = Single.just(emptyList())

    override val actions: Single<AvailableActions> = Single.just(emptySet())
    override val isFunded: Boolean = false
    override val hasTransactions: Boolean = false

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<IneligibilityReason>
        get() = Single.just(IneligibilityReason.NONE)

    override fun canWithdrawFunds(): Single<Boolean> = Single.just(false)

    override fun fiatBalance(
        fiatCurrency: String,
        exchangeRates: ExchangeRates
    ): Single<Money> =
        Single.just(FiatValue.zero(fiatCurrency))
}

class NullAccountGroup : AccountGroup {
    override val accounts: SingleAccountList = emptyList()

    override fun includes(account: BlockchainAccount): Boolean = false
    override val label: String = ""
    override val accountBalance: Single<Money> = Single.error(NotImplementedError())
    override val actionableBalance: Single<Money> = Single.error(NotImplementedError())
    override val pendingBalance: Single<Money> = Single.error(NotImplementedError())
    override val activity: Single<ActivitySummaryList> = Single.just(emptyList())
    override val actions: Single<AvailableActions> = Single.just(emptySet())
    override val isFunded: Boolean = false
    override val hasTransactions: Boolean = false

    override fun fiatBalance(fiatCurrency: String, exchangeRates: ExchangeRates): Single<Money> =
        Single.error(NotImplementedError())
    override val receiveAddress: Single<ReceiveAddress> =
        Single.error(NotImplementedError())
}