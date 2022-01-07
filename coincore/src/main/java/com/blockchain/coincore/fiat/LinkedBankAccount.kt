package com.blockchain.coincore.fiat

import com.blockchain.coincore.AccountBalance
import com.blockchain.coincore.ActivitySummaryList
import com.blockchain.coincore.AvailableActions
import com.blockchain.coincore.BankAccount
import com.blockchain.coincore.FiatAccount
import com.blockchain.coincore.ReceiveAddress
import com.blockchain.coincore.TxSourceState
import com.blockchain.core.price.ExchangeRate
import com.blockchain.core.price.ExchangeRates
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.datamanagers.repositories.interest.IneligibilityReason
import com.blockchain.nabu.models.data.FiatWithdrawalFeeAndLimit
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

class LinkedBankAccount(
    override val label: String,
    val accountNumber: String,
    val accountId: String,
    val accountType: String,
    val currency: String,
    val custodialWalletManager: CustodialWalletManager,
    val type: PaymentMethodType
) : FiatAccount, BankAccount {

    init {
        check(type == PaymentMethodType.BANK_ACCOUNT || type == PaymentMethodType.BANK_TRANSFER) {
            "Attempting to initialise a LinkedBankAccount with an incorrect PaymentMethodType of $type"
        }
    }

    fun getWithdrawalFeeAndMinLimit(): Single<FiatWithdrawalFeeAndLimit> =
        custodialWalletManager.fetchFiatWithdrawFeeAndMinLimit(currency, Product.BUY, paymentMethodType = type)

    override val fiatCurrency: String
        get() = currency

    override val balance: Observable<AccountBalance>
        get() = FiatValue.zero(currency).let { zero ->
            Observable.just(
                AccountBalance(
                    total = zero,
                    pending = zero,
                    actionable = zero,
                    exchangeRate = ExchangeRate.InvalidRate
                )
            )
        }

    override val accountBalance: Single<Money>
        get() = balance.map { it.total }.firstOrError()

    override val actionableBalance: Single<Money>
        get() = balance.map { it.actionable }.firstOrError()

    override val pendingBalance: Single<Money>
        get() = balance.map { it.pending }.firstOrError()

    override fun fiatBalance(fiatCurrency: String, exchangeRates: ExchangeRates): Single<Money> =
        Single.just(FiatValue.zero(fiatCurrency))

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(BankAccountAddress(accountId, label))

    override val isDefault: Boolean
        get() = false

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.CAN_TRANSACT)

    override val activity: Single<ActivitySummaryList>
        get() = Single.just(emptyList())

    override val actions: Single<AvailableActions>
        get() = Single.just(emptySet())

    override val isFunded: Boolean
        get() = false

    override val hasTransactions: Boolean
        get() = false

    override val isEnabled: Single<Boolean>
        get() = Single.just(true)

    override val disabledReason: Single<IneligibilityReason>
        get() = Single.just(IneligibilityReason.NONE)

    override fun canWithdrawFunds(): Single<Boolean> = Single.just(false)

    internal class BankAccountAddress(
        override val address: String,
        override val label: String = address
    ) : ReceiveAddress
}
