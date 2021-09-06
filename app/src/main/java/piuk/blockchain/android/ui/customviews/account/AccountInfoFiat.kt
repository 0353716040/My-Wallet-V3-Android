package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.blockchain.core.price.ExchangeRates
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import org.koin.core.component.KoinComponent
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import com.blockchain.coincore.FiatAccount
import piuk.blockchain.android.databinding.ViewAccountFiatOverviewBinding
import piuk.blockchain.android.ui.transactionflow.analytics.TxFlowAnalytics
import piuk.blockchain.android.ui.transactionflow.engine.TransactionModel
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.EnterAmountCustomisations
import piuk.blockchain.android.ui.transactionflow.plugin.EnterAmountWidget
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class AccountInfoFiat @JvmOverloads constructor(
    ctx: Context,
    attr: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(ctx, attr, defStyle), KoinComponent, EnterAmountWidget {

    private val exchangeRates: ExchangeRates by scopedInject()
    private val currencyPrefs: CurrencyPrefs by scopedInject()
    private val compositeDisposable = CompositeDisposable()

    val binding: ViewAccountFiatOverviewBinding =
        ViewAccountFiatOverviewBinding.inflate(LayoutInflater.from(context), this, true)

    fun updateAccount(account: FiatAccount, cellDecorator: CellDecorator, onAccountClicked: (FiatAccount) -> Unit) {
        compositeDisposable.clear()
        updateView(account, cellDecorator, onAccountClicked)
    }

    private fun updateView(
        account: FiatAccount,
        cellDecorator: CellDecorator,
        onAccountClicked: (FiatAccount) -> Unit
    ) {
        with(binding) {
            val userFiat = currencyPrefs.selectedFiatCurrency

            walletName.text = account.label
            icon.setIcon(account.fiatCurrency)
            assetSubtitle.text = account.fiatCurrency

            compositeDisposable += account.accountBalance
                .flatMap { balanceInAccountCurrency ->
                    if (userFiat == account.fiatCurrency)
                        Single.just(balanceInAccountCurrency to balanceInAccountCurrency)
                    else account.fiatBalance(userFiat, exchangeRates).map { balanceInSelectedCurrency ->
                        balanceInAccountCurrency to balanceInSelectedCurrency
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy { (balanceInAccountCurrency, balanceInWalletCurrency) ->
                    if (userFiat == account.fiatCurrency) {
                        walletBalanceExchangeFiat.gone()
                        walletBalanceFiat.text = balanceInAccountCurrency.toStringWithSymbol()
                    } else {
                        walletBalanceExchangeFiat.visible()
                        walletBalanceFiat.text = balanceInWalletCurrency.toStringWithSymbol()
                        walletBalanceExchangeFiat.text = balanceInAccountCurrency.toStringWithSymbol()
                    }
                }

            setOnClickListener { }
            compositeDisposable += cellDecorator.isEnabled()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { isEnabled ->
                    if (isEnabled) {
                        setOnClickListener { onAccountClicked(account) }
                        container.alpha = 1f
                    } else {
                        container.alpha = .6f
                        setOnClickListener { }
                    }
                }
        }
    }

    fun dispose() {
        compositeDisposable.clear()
    }

    override fun initControl(
        model: TransactionModel,
        customiser: EnterAmountCustomisations,
        analytics: TxFlowAnalytics
    ) {
        // No need to initialise
    }

    override fun update(state: TransactionState) {
        updateAccount(state.sendingAccount as FiatAccount, DefaultCellDecorator()) { }
    }

    override fun setVisible(isVisible: Boolean) {
        binding.root.visibleIf { isVisible }
    }
}
