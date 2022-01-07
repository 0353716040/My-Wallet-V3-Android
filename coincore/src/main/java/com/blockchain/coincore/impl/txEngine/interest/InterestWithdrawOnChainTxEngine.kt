package com.blockchain.coincore.impl.txEngine.interest

import androidx.annotation.VisibleForTesting
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.CryptoTarget
import com.blockchain.coincore.FeeLevel
import com.blockchain.coincore.FeeSelection
import com.blockchain.coincore.InterestAccount
import com.blockchain.coincore.NonCustodialAccount
import com.blockchain.coincore.PendingTx
import com.blockchain.coincore.TxConfirmationValue
import com.blockchain.coincore.TxResult
import com.blockchain.coincore.TxValidationFailure
import com.blockchain.coincore.ValidationState
import com.blockchain.coincore.toCrypto
import com.blockchain.coincore.toUserFiat
import com.blockchain.coincore.updateTxValidity
import com.blockchain.core.interest.InterestBalanceDataManager
import com.blockchain.core.limits.TxLimits
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Product
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

class InterestWithdrawOnChainTxEngine(
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val walletManager: CustodialWalletManager,
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val interestBalances: InterestBalanceDataManager
) : InterestBaseEngine(walletManager) {

    private val availableBalance: Single<Money>
        get() = sourceAccount.actionableBalance

    override fun assertInputsValid() {
        check(sourceAccount is InterestAccount)
        check(txTarget is CryptoAccount)
        check(txTarget is NonCustodialAccount)
        check(sourceAsset == (txTarget as CryptoAccount).asset)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        Single.zip(
            walletManager.fetchCryptoWithdrawFeeAndMinLimit(sourceAsset, Product.SAVINGS),
            walletManager.getInterestLimits(sourceAsset),
            availableBalance,
            { minLimits, maxLimits, balance ->
                PendingTx(
                    amount = CryptoValue.zero(sourceAsset),
                    limits = TxLimits.fromAmounts(
                        min = CryptoValue.fromMinor(sourceAsset, minLimits.minLimit),
                        max = maxLimits.maxWithdrawalFiatValue.toCrypto(exchangeRates, sourceAsset)
                    ),
                    feeSelection = FeeSelection(),
                    selectedFiat = userFiat,
                    availableBalance = balance,
                    totalBalance = balance,
                    feeAmount = CryptoValue.fromMinor(sourceAsset, minLimits.fee),
                    feeForFullAvailable = CryptoValue.zero(sourceAsset)
                )
            }
        )

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        availableBalance.map { balance ->
            balance as CryptoValue
        }.map { available ->
            pendingTx.copy(
                amount = amount,
                availableBalance = available,
                totalBalance = available
            )
        }

    private fun checkIfAmountIsBelowMinLimit(pendingTx: PendingTx) =
        when {
            pendingTx.limits == null -> {
                throw TxValidationFailure(ValidationState.UNINITIALISED)
            }
            pendingTx.isMinLimitViolated() -> throw TxValidationFailure(ValidationState.UNDER_MIN_LIMIT)
            else -> Completable.complete()
        }

    override fun doUpdateFeeLevel(pendingTx: PendingTx, level: FeeLevel, customFeeAmount: Long): Single<PendingTx> =
        Single.just(pendingTx)

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        availableBalance.flatMapCompletable { balance ->
            if (pendingTx.amount <= balance) {
                checkIfAmountIsBelowMinLimit(pendingTx)
            } else {
                throw TxValidationFailure(ValidationState.INSUFFICIENT_FUNDS)
            }
        }.updateTxValidity(pendingTx)

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            pendingTx.copy(
                confirmations = listOfNotNull(
                    TxConfirmationValue.From(sourceAccount, sourceAsset),
                    TxConfirmationValue.To(
                        txTarget, AssetAction.InterestDeposit, sourceAccount
                    ),
                    TxConfirmationValue.NetworkFee(
                        pendingTx.feeAmount,
                        pendingTx.feeAmount.toUserFiat(exchangeRates),
                        sourceAsset
                    ),
                    (txTarget as? CryptoTarget)?.memo?.let {
                        TxConfirmationValue.Memo(
                            text = it,
                            id = null,
                            editable = false
                        )
                    },
                    TxConfirmationValue.Total(
                        totalWithFee = (pendingTx.amount as CryptoValue).plus(
                            pendingTx.feeAmount as CryptoValue
                        ),
                        exchange = pendingTx.amount.toUserFiat(exchangeRates)
                            .plus(pendingTx.feeAmount.toUserFiat(exchangeRates))
                    )
                )
            )
        )

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        doValidateAmount(pendingTx)

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        (txTarget as CryptoAccount).receiveAddress.flatMapCompletable { receiveAddress ->
            (txTarget as? CryptoTarget)?.memo?.let {
                executeWithdrawal(
                    sourceAsset, pendingTx.amount, receiveAddress.address, it
                )
            } ?: kotlin.run {
                executeWithdrawal(sourceAsset, pendingTx.amount, receiveAddress.address)
            }
        }.toSingle {
            TxResult.UnHashedTxResult(pendingTx.amount)
        }

    private fun executeWithdrawal(
        sourceAsset: AssetInfo,
        amount: Money,
        receiveAddress: String,
        memo: String? = null
    ) = walletManager.startInterestWithdrawal(
        asset = sourceAsset,
        amount = amount,
        address = addMemoIfNeeded(receiveAddress, memo)
    ).doOnComplete {
        interestBalances.flushCaches(sourceAsset)
    }

    private fun addMemoIfNeeded(receiveAddress: String, memo: String?) =
        receiveAddress + (
            memo?.let {
                ":$it"
            } ?: ""
            )
}
