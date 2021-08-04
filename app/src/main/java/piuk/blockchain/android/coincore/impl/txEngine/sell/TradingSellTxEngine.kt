package piuk.blockchain.android.coincore.impl.txEngine.sell

import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.zipWith
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FeeSelection
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxResult
import piuk.blockchain.android.coincore.impl.CustodialTradingAccount
import piuk.blockchain.android.coincore.impl.txEngine.TransferQuotesEngine

class TradingSellTxEngine(
    walletManager: CustodialWalletManager,
    quotesEngine: TransferQuotesEngine,
    kycTierService: TierService
) : SellTxEngineBase(walletManager, kycTierService, quotesEngine) {

    override val direction: TransferDirection
        get() = TransferDirection.INTERNAL

    override val availableBalance: Single<Money>
        get() = sourceAccount.accountBalance

    override fun assertInputsValid() {
        check(sourceAccount is CustodialTradingAccount)
        check(txTarget is FiatAccount)
    }

    override fun doInitialiseTx(): Single<PendingTx> =
        quotesEngine.pricedQuote.firstOrError()
            .zipWith(sourceAccount.accountBalance)
            .flatMap { (quote, balance) ->
                Single.just(
                    PendingTx(
                        amount = CryptoValue.zero(sourceAsset),
                        totalBalance = balance,
                        availableBalance = balance,
                        feeAmount = CryptoValue.zero(sourceAsset),
                        feeForFullAvailable = CryptoValue.zero(sourceAsset),
                        selectedFiat = target.fiatCurrency,
                        feeSelection = FeeSelection()
                    )
                ).flatMap {
                    updateLimits(target.fiatCurrency, it, quote)
                }
            }.handlePendingOrdersError(
                PendingTx(
                    amount = CryptoValue.zero(sourceAsset),
                    totalBalance = CryptoValue.zero(sourceAsset),
                    availableBalance = CryptoValue.zero(sourceAsset),
                    feeForFullAvailable = CryptoValue.zero(sourceAsset),
                    feeAmount = CryptoValue.zero(sourceAsset),
                    selectedFiat = target.fiatCurrency,
                    feeSelection = FeeSelection()
                )
            )

    override fun doUpdateAmount(amount: Money, pendingTx: PendingTx): Single<PendingTx> {
        return sourceAccount.accountBalance
            .map { it as CryptoValue }
            .map { available ->
                pendingTx.copy(
                    amount = amount,
                    availableBalance = available,
                    totalBalance = available
                )
            }
            .updateQuotePrice()
            .clearConfirmations()
    }

    override fun doUpdateFeeLevel(
        pendingTx: PendingTx,
        level: FeeLevel,
        customFeeAmount: Long
    ): Single<PendingTx> {
        require(pendingTx.feeSelection.availableLevels.contains(level))
        // This engine only supports FeeLevel.None, so
        return Single.just(pendingTx)
    }

    override fun feeInSourceCurrency(pendingTx: PendingTx): Money = pendingTx.feeAmount

    override val requireSecondPassword: Boolean
        get() = false

    override fun doExecute(pendingTx: PendingTx, secondPassword: String): Single<TxResult> =
        createSellOrder(pendingTx).map {
            TxResult.UnHashedTxResult(pendingTx.amount)
        }
}