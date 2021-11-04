package com.blockchain.coincore.impl

import com.blockchain.coincore.AccountBalance
import com.blockchain.coincore.ActivitySummaryItem
import com.blockchain.coincore.ActivitySummaryList
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.AvailableActions
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.CustodialTradingActivitySummaryItem
import com.blockchain.coincore.CustodialTransferActivitySummaryItem
import com.blockchain.coincore.ReceiveAddress
import com.blockchain.coincore.RecurringBuyActivitySummaryItem
import com.blockchain.coincore.TradeActivitySummaryItem
import com.blockchain.coincore.TradingAccount
import com.blockchain.coincore.TxResult
import com.blockchain.coincore.TxSourceState
import com.blockchain.coincore.takeEnabledIf
import com.blockchain.coincore.toFiat
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.nabu.Feature
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.BuySellOrder
import com.blockchain.nabu.datamanagers.CryptoTransaction
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.TransactionState
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.custodialwalletimpl.OrderType
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.atomic.AtomicBoolean
import piuk.blockchain.androidcore.utils.extensions.mapList

class CustodialTradingAccount(
    override val asset: AssetInfo,
    override val label: String,
    override val exchangeRates: ExchangeRatesDataManager,
    val custodialWalletManager: CustodialWalletManager,
    val tradingBalances: TradingBalanceDataManager,
    val isNoteSupported: Boolean = false,
    override val isMemoSupported: Boolean = false,
    private val identity: UserIdentity,
    @Suppress("unused")
    private val features: InternalFeatureFlagApi,
    override val baseActions: Set<AssetAction> = defaultActions
) : CryptoAccountBase(), TradingAccount {

    private val hasFunds = AtomicBoolean(false)

    override val receiveAddress: Single<ReceiveAddress>
        get() = custodialWalletManager.getCustodialAccountAddress(asset).map {
            makeExternalAssetAddress(
                asset = asset,
                address = it,
                label = label,
                postTransactions = onTxCompleted
            )
        }

    override val directions: Set<TransferDirection> = setOf(TransferDirection.INTERNAL)

    override val onTxCompleted: (TxResult) -> Completable
        get() = { txResult ->
            receiveAddress.flatMapCompletable {
                require(txResult.amount is CryptoValue)
                require(txResult is TxResult.HashedTxResult)
                custodialWalletManager.createPendingDeposit(
                    crypto = txResult.amount.currency,
                    address = it.address,
                    hash = txResult.txId,
                    amount = txResult.amount,
                    product = Product.BUY
                )
            }
        }

    override fun requireSecondPassword(): Single<Boolean> =
        Single.just(false)

    override fun matches(other: CryptoAccount): Boolean =
        other is CustodialTradingAccount && other.asset == asset

    override val balance: Observable<AccountBalance>
        get() = Observable.combineLatest(
            tradingBalances.getBalanceForAsset(asset),
            exchangeRates.cryptoToUserFiatRate(asset)
        ) { balance, rate ->
            setHasTransactions(balance.hasTransactions)
            AccountBalance.from(balance, rate)
        }.doOnNext { hasFunds.set(it.total.isPositive) }

    override val activity: Single<ActivitySummaryList>
        get() = custodialWalletManager.getAllOrdersFor(asset)
            .mapList { orderToSummary(it) }
            .flatMap { buySellList ->
                appendTradeActivity(custodialWalletManager, asset, buySellList)
            }
            .flatMap {
                appendTransferActivity(custodialWalletManager, asset, it)
            }.filterActivityStates()
            .doOnSuccess { setHasTransactions(it.isNotEmpty()) }
            .onErrorReturn { emptyList() }

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val isDefault: Boolean =
        false // Default is, presently, only ever a non-custodial account.

    override val sourceState: Single<TxSourceState>
        get() = Single.zip(
            accountBalance,
            actionableBalance
        ) { total, actionable ->
            when {
                total <= CryptoValue.zero(asset) -> TxSourceState.NO_FUNDS
                actionable <= CryptoValue.zero(asset) -> TxSourceState.FUNDS_LOCKED
                else -> TxSourceState.CAN_TRANSACT
            }
        }

    override val actions: Single<AvailableActions>
        get() =
            Single.zip(
                accountBalance.map { it.isPositive },
                actionableBalance.map { it.isPositive },
                identity.isEligibleFor(Feature.SimpleBuy),
                identity.isEligibleFor(Feature.Interest(asset)),
                custodialWalletManager.getSupportedFundsFiats().onErrorReturn { emptyList() }
            ) { hasFunds, hasActionableBalance, isEligibleForSimpleBuy, isEligibleForInterest, fiatAccounts ->
                val isActiveFunded = !isArchived && hasFunds

                val activity = AssetAction.ViewActivity.takeEnabledIf(baseActions) { hasTransactions }
                val receive = AssetAction.Receive.takeEnabledIf(baseActions)
                val buy = AssetAction.Buy.takeEnabledIf(baseActions)

                val send = AssetAction.Send.takeEnabledIf(baseActions) {
                    isActiveFunded && hasActionableBalance
                }
                val interest = AssetAction.InterestDeposit.takeEnabledIf(baseActions) {
                    isActiveFunded && isEligibleForInterest
                }
                val swap = AssetAction.Swap.takeEnabledIf(baseActions) {
                    isActiveFunded && isEligibleForSimpleBuy
                }
                val sell = AssetAction.Sell.takeEnabledIf(baseActions) {
                    isActiveFunded && isEligibleForSimpleBuy && fiatAccounts.isNotEmpty()
                }

                setOfNotNull(
                    buy, sell, swap, send, receive, interest, activity
                )
            }

    override val hasStaticAddress: Boolean = false

    private fun appendTransferActivity(
        custodialWalletManager: CustodialWalletManager,
        asset: AssetInfo,
        summaryList: List<ActivitySummaryItem>
    ) = custodialWalletManager.getCustodialCryptoTransactions(asset.networkTicker, Product.BUY)
        .map { txs ->
            txs.map {
                it.toSummaryItem()
            } + summaryList
        }

    private fun CryptoTransaction.toSummaryItem() =
        CustodialTransferActivitySummaryItem(
            asset = asset,
            exchangeRates = exchangeRates,
            txId = id,
            timeStampMs = date.time,
            value = amount,
            account = this@CustodialTradingAccount,
            fee = fee,
            recipientAddress = receivingAddress,
            txHash = txHash,
            state = state,
            type = type,
            fiatValue = amount.toFiat(currency, exchangeRates) as FiatValue,
            paymentMethodId = paymentId
        )

    private fun orderToSummary(order: BuySellOrder): ActivitySummaryItem =
        when (order.type) {
            OrderType.RECURRING_BUY -> {
                RecurringBuyActivitySummaryItem(
                    exchangeRates = exchangeRates,
                    asset = order.crypto.currency,
                    value = order.crypto,
                    fundedFiat = order.fiat,
                    txId = order.id,
                    timeStampMs = order.created.time,
                    transactionState = order.state,
                    fee = order.fee ?: FiatValue.zero(order.fiat.currencyCode),
                    account = this,
                    type = order.type,
                    paymentMethodId = order.paymentMethodId,
                    paymentMethodType = order.paymentMethodType,
                    failureReason = order.failureReason,
                    recurringBuyId = order.recurringBuyId
                )
            }
            OrderType.BUY -> {
                CustodialTradingActivitySummaryItem(
                    exchangeRates = exchangeRates,
                    asset = order.crypto.currency,
                    value = order.crypto,
                    fundedFiat = order.fiat,
                    price = order.price,
                    txId = order.id,
                    timeStampMs = order.created.time,
                    status = order.state,
                    fee = order.fee ?: FiatValue.zero(order.fiat.currencyCode),
                    account = this,
                    type = order.type,
                    paymentMethodId = order.paymentMethodId,
                    paymentMethodType = order.paymentMethodType,
                    depositPaymentId = order.depositPaymentId
                )
            }
            else -> {
                TradeActivitySummaryItem(
                    exchangeRates = exchangeRates,
                    txId = order.id,
                    timeStampMs = order.created.time,
                    sendingValue = order.crypto,
                    price = order.price,
                    sendingAccount = this,
                    sendingAddress = null,
                    receivingAddress = null,
                    state = order.state.toCustodialOrderState(),
                    direction = TransferDirection.INTERNAL,
                    receivingValue = order.orderValue ?: throw IllegalStateException(
                        "Order missing receivingValue"
                    ),
                    depositNetworkFee = Single.just(CryptoValue.zero(order.crypto.currency)),
                    withdrawalNetworkFee = order.fee ?: FiatValue.zero(order.fiat.currencyCode),
                    currencyPair = CurrencyPair.CryptoToFiatCurrencyPair(
                        order.crypto.currency, order.fiat.currencyCode
                    ),
                    fiatValue = order.fiat,
                    fiatCurrency = order.fiat.currencyCode
                )
            }
        }

    // Stop gap filter, until we finalise which item we wish to display to the user.
    // TODO: This can be done via the API when it's settled
    private fun Single<ActivitySummaryList>.filterActivityStates(): Single<ActivitySummaryList> {
        return flattenAsObservable { list ->
            list.filter {
                (
                    it is CustodialTradingActivitySummaryItem && displayedStates.contains(
                        it.status
                    )
                    ) or (
                    it is CustodialTransferActivitySummaryItem && displayedStates.contains(
                        it.state
                    )
                    ) or (
                    it is TradeActivitySummaryItem && displayedStates.contains(
                        it.state
                    )
                    ) or (it is RecurringBuyActivitySummaryItem)
            }
        }.toList()
    }

    // No need to reconcile sends and swaps in custodial accounts, the BE deals with this
    // Return a list containing both supplied list
    override fun reconcileSwaps(
        tradeItems: List<TradeActivitySummaryItem>,
        activity: List<ActivitySummaryItem>
    ): List<ActivitySummaryItem> = activity + tradeItems

    companion object {
        private val displayedStates = setOf(
            OrderState.FINISHED,
            OrderState.AWAITING_FUNDS,
            OrderState.PENDING_EXECUTION,
            OrderState.FAILED,
            CustodialOrderState.FINISHED,
            TransactionState.COMPLETED,
            TransactionState.PENDING,
            CustodialOrderState.PENDING_DEPOSIT,
            CustodialOrderState.PENDING_EXECUTION,
            CustodialOrderState.FAILED
        )
    }
}

private fun OrderState.toCustodialOrderState(): CustodialOrderState =
    when (this) {
        OrderState.FINISHED -> CustodialOrderState.FINISHED
        OrderState.CANCELED -> CustodialOrderState.CANCELED
        OrderState.FAILED -> CustodialOrderState.FAILED
        OrderState.PENDING_CONFIRMATION -> CustodialOrderState.PENDING_CONFIRMATION
        OrderState.AWAITING_FUNDS -> CustodialOrderState.PENDING_DEPOSIT
        OrderState.PENDING_EXECUTION -> CustodialOrderState.PENDING_EXECUTION
        else -> CustodialOrderState.UNKNOWN
    }
