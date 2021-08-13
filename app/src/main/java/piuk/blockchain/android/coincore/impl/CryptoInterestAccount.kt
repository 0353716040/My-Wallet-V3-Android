package piuk.blockchain.android.coincore.impl

import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.InterestActivityItem
import com.blockchain.nabu.datamanagers.InterestState
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.repositories.interest.IneligibilityReason
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CustodialInterestActivitySummaryItem
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxSourceState
import piuk.blockchain.androidcore.utils.extensions.mapList
import java.util.concurrent.atomic.AtomicBoolean

internal class CryptoInterestAccount(
    override val asset: AssetInfo,
    override val label: String,
    private val custodialWalletManager: CustodialWalletManager,
    override val exchangeRates: ExchangeRatesDataManager,
    @Suppress("unused")
    private val features: InternalFeatureFlagApi
) : CryptoAccountBase(), InterestAccount {

    override val baseActions: Set<AssetAction> = emptySet() // Not used by this class

    private val hasFunds = AtomicBoolean(false)

    override val receiveAddress: Single<ReceiveAddress>
        get() = custodialWalletManager.getInterestAccountAddress(asset).map {
            makeExternalAssetAddress(
                asset = asset,
                address = it,
                label = label,
                postTransactions = onTxCompleted
            )
        }

    override val onTxCompleted: (TxResult) -> Completable
        get() = { txResult ->
            require(txResult.amount is CryptoValue)
            require(txResult is TxResult.HashedTxResult)
            receiveAddress.flatMapCompletable { receiveAddress ->
                custodialWalletManager.createPendingDeposit(
                    crypto = txResult.amount.currency,
                    address = receiveAddress.address,
                    hash = txResult.txId,
                    amount = txResult.amount,
                    product = Product.SAVINGS
                )
            }
        }

    override val directions: Set<TransferDirection>
        get() = emptySet()

    override fun requireSecondPassword(): Single<Boolean> =
        Single.just(false)

    override fun matches(other: CryptoAccount): Boolean =
        other is CryptoInterestAccount && other.asset == asset

    override val accountBalance: Single<Money>
        get() = custodialWalletManager.getInterestAccountBalance(asset)
            .doOnSuccess { hasFunds.set(it.isPositive) }
            .map { it }

    override val pendingBalance: Single<Money>
        get() = custodialWalletManager.getPendingInterestAccountBalance(asset)
            .map { it }

    override val actionableBalance: Single<Money>
        get() = custodialWalletManager.getActionableInterestAccountBalance(asset)
            .map { it }

    override val activity: Single<ActivitySummaryList>
        get() = custodialWalletManager.getInterestActivity(asset)
            .onErrorReturn { emptyList() }
            .mapList { interestActivityToSummary(it) }
            .filterActivityStates()
            .doOnSuccess {
                setHasTransactions(it.isNotEmpty())
            }

    private fun interestActivityToSummary(item: InterestActivityItem): ActivitySummaryItem =
        CustodialInterestActivitySummaryItem(
            exchangeRates = exchangeRates,
            asset = item.cryptoCurrency,
            txId = item.id,
            timeStampMs = item.insertedAt.time,
            value = item.value,
            account = this,
            status = item.state,
            type = item.type,
            confirmations = item.extraAttributes?.confirmations ?: 0,
            accountRef = item.extraAttributes?.beneficiary?.accountRef ?: "",
            recipientAddress = item.extraAttributes?.address ?: ""
        )

    private fun Single<ActivitySummaryList>.filterActivityStates(): Single<ActivitySummaryList> {
        return flattenAsObservable { list ->
            list.filter {
                it is CustodialInterestActivitySummaryItem && displayedStates.contains(it.status)
            }
        }.toList()
    }

    // No swaps on interest accounts, so just return the activity list unmodified
    override fun reconcileSwaps(
        tradeItems: List<TradeActivitySummaryItem>,
        activity: List<ActivitySummaryItem>
    ): List<ActivitySummaryItem> = activity

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val isDefault: Boolean = false // Default is, presently, only ever a non-custodial account.

    override val sourceState: Single<TxSourceState>
        get() = Single.just(TxSourceState.CAN_TRANSACT)

    override val isEnabled: Single<Boolean>
        get() = custodialWalletManager.getInterestEligibilityForAsset(asset)
            .map { (enabled, _) ->
                enabled
            }

    override val disabledReason: Single<IneligibilityReason>
        get() = custodialWalletManager.getInterestEligibilityForAsset(asset)
            .map { (_, reason) ->
                reason
            }

    override val actions: Single<AvailableActions> =
        actionableBalance.map { balance ->
            setOfNotNull(
                AssetAction.InterestWithdraw.takeIf { balance.isPositive },
                AssetAction.ViewStatement,
                AssetAction.ViewActivity
            )
        }

    companion object {
        private val displayedStates = setOf(
            InterestState.COMPLETE,
            InterestState.PROCESSING,
            InterestState.PENDING,
            InterestState.MANUAL_REVIEW,
            InterestState.FAILED
        )
    }
}