package piuk.blockchain.android.coincore.impl.txEngine

import androidx.annotation.VisibleForTesting
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FeeSelection
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxEngine
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.TxValidationFailure
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.fiat.LinkedBankAccount
import piuk.blockchain.android.coincore.updateTxValidity

class FiatWithdrawalTxEngine(
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val walletManager: CustodialWalletManager
) : TxEngine() {

    override fun assertInputsValid() {
        check(sourceAccount is FiatAccount)
        check(txTarget is LinkedBankAccount)
    }

    override val canTransactFiat: Boolean
        get() = true

    override fun doInitialiseTx(): Single<PendingTx> {
        check(txTarget is LinkedBankAccount)
        check(sourceAccount is FiatAccount)

        return Singles.zip(
            sourceAccount.actionableBalance,
            sourceAccount.accountBalance,
            (txTarget as LinkedBankAccount).getWithdrawalFeeAndMinLimit(),
            { actionableBalance, accountBalance, limitAndFee ->
                val zeroFiat = FiatValue.zero((sourceAccount as FiatAccount).fiatCurrency)
                PendingTx(
                    amount = zeroFiat,
                    maxLimit = actionableBalance,
                    minLimit = limitAndFee.minLimit,
                    availableBalance = actionableBalance,
                    feeForFullAvailable = zeroFiat,
                    totalBalance = accountBalance,
                    feeAmount = limitAndFee.fee,
                    selectedFiat = userFiat,
                    feeSelection = FeeSelection()
                )
            }
        )
    }

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =

        (txTarget as LinkedBankAccount).receiveAddress.flatMapCompletable {
            walletManager.createWithdrawOrder(
                amount = pendingTx.amount,
                bankId = it.address
            )
        }
            .toSingle { TxResult.UnHashedTxResult(amount = pendingTx.amount) }

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> {
        return Single.just(
            pendingTx.copy(
                confirmations = listOfNotNull(
                    TxConfirmationValue.From(sourceAccount),
                    TxConfirmationValue.PaymentMethod(
                        txTarget.label,
                        (txTarget as LinkedBankAccount).accountNumber,
                        (txTarget as LinkedBankAccount).accountType,
                        AssetAction.Withdraw
                    ),
                    TxConfirmationValue.EstimatedCompletion,
                    TxConfirmationValue.Amount(pendingTx.amount, false),
                    if (pendingTx.feeAmount.isPositive) {
                        TxConfirmationValue.TransactionFee(pendingTx.feeAmount)
                    } else null,
                    TxConfirmationValue.Amount(pendingTx.amount.minus(pendingTx.feeAmount), true)
                )
            )
        )
    }

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            pendingTx.copy(
                amount = amount
            )
        )

    override fun doUpdateFeeLevel(pendingTx: PendingTx, level: FeeLevel, customFeeAmount: Long): Single<PendingTx> {
        require(pendingTx.feeSelection.availableLevels.contains(level))
        return Single.just(pendingTx)
    }

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        doValidateAmount(pendingTx).updateTxValidity(pendingTx)

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> {
        return if (pendingTx.validationState == ValidationState.UNINITIALISED && pendingTx.amount.isZero) {
            Single.just(pendingTx)
        } else {
            validateAmount(pendingTx).updateTxValidity(pendingTx)
        }
    }

    private fun validateAmount(pendingTx: PendingTx): Completable =
        Completable.fromCallable {
            if (pendingTx.maxLimit != null && pendingTx.minLimit != null) {
                when {
                    pendingTx.amount < pendingTx.minLimit -> throw TxValidationFailure(
                        ValidationState.UNDER_MIN_LIMIT
                    )
                    pendingTx.amount > pendingTx.maxLimit -> throw TxValidationFailure(
                        ValidationState.OVER_MAX_LIMIT
                    )
                    pendingTx.availableBalance < pendingTx.amount -> throw TxValidationFailure(
                        ValidationState.INSUFFICIENT_FUNDS
                    )
                    else -> Completable.complete()
                }
            } else {
                throw TxValidationFailure(ValidationState.UNKNOWN_ERROR)
            }
        }
}