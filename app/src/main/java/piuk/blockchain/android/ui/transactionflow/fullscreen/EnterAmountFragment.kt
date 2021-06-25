package piuk.blockchain.android.ui.transactionflow.fullscreen

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.FeeLevel
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.databinding.FragmentTxFlowEnterAmountBinding
import piuk.blockchain.android.ui.customviews.CurrencyType
import piuk.blockchain.android.ui.customviews.FiatCryptoInputView
import piuk.blockchain.android.ui.customviews.FiatCryptoViewConfiguration
import piuk.blockchain.android.ui.customviews.PrefixedOrSuffixedEditText
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.EnterAmountCustomisations
import piuk.blockchain.android.ui.transactionflow.flow.customisations.IssueType
import piuk.blockchain.android.ui.transactionflow.plugin.ExpandableTxFlowWidget
import piuk.blockchain.android.ui.transactionflow.plugin.TxFlowWidget
import timber.log.Timber
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

class EnterAmountFragment : TransactionFlowFragment<FragmentTxFlowEnterAmountBinding>() {

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTxFlowEnterAmountBinding =
        FragmentTxFlowEnterAmountBinding.inflate(inflater, container, false)

    private val customiser: EnterAmountCustomisations by inject()
    private val compositeDisposable = CompositeDisposable()

    private var lowerSlot: TxFlowWidget? = null
    private var upperSlot: TxFlowWidget? = null

    private val imm: InputMethodManager by lazy {
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            amountSheetCtaButton.setOnClickListener {
                analyticsHooks.onEnterAmountCtaClick(state)
                onCtaClick()
            }
            //            amountSheetBack.setOnClickListener {
            //                analyticsHooks.onStepBackClicked(state)
            //                model.process(TransactionIntent.NavigateBackFromEnterAmount)
            //            }
        }

        compositeDisposable += binding.amountSheetInput.amount
            .debounce(AMOUNT_DEBOUNCE_TIME_MS, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe { amount ->
                state.fiatRate?.let { rate ->
                    check(state.pendingTx != null) { "Px is not initialised yet" }
                    model.process(
                        TransactionIntent.AmountChanged(
                            if (!state.allowFiatInput && amount is FiatValue) {
                                convertFiatToCrypto(amount, rate as ExchangeRate.CryptoToFiat, state).also {
                                    binding.amountSheetInput.fixExchange(it)
                                }
                            } else {
                                amount
                            }
                        )
                    )
                }
            }

        compositeDisposable += binding.amountSheetInput
            .onImeAction
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe {
                when (it) {
                    PrefixedOrSuffixedEditText.ImeOptions.NEXT -> {
                        if (state.nextEnabled) {
                            onCtaClick()
                        }
                    }
                    PrefixedOrSuffixedEditText.ImeOptions.BACK -> {
                        hideKeyboard()
                        // dismiss()
                    }
                    else -> {
                        // do nothing
                    }
                }
            }

        compositeDisposable += binding.amountSheetInput.onInputToggle
            .subscribe {
                analyticsHooks.onCryptoToggle(it, state)
                model.process(TransactionIntent.DisplayModeChanged(it))
            }
    }

    @SuppressLint("SetTextI18n")
    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! EnterAmountSheet")
        cacheState(newState)
        with(binding) {
            amountSheetCtaButton.isEnabled = newState.nextEnabled

            if (!amountSheetInput.configured) {
                newState.pendingTx?.let {
                    amountSheetInput.configure(newState, customiser.defInputType(state, it.selectedFiat))
                }
            }

            val availableBalance = newState.availableBalance
            if (availableBalance.isPositive || availableBalance.isZero) {
                // The maxLimit set here controls the number of digits that can be entered,
                // but doesn't restrict the input to be always under that value. Which might be
                // strange UX, but is currently by design.
                if (amountSheetInput.configured) {
                    if (customiser.shouldShowMaxLimit(newState)) {
                        amountSheetInput.maxLimit = newState.availableBalance
                    }
                    if (amountSheetInput.customInternalExchangeRate != newState.fiatRate) {
                        amountSheetInput.customInternalExchangeRate = newState.fiatRate
                    }
                }

                if (state.setMax) {
                    amountSheetInput.updateValue(state.maxSpendable)
                }

                amountSheetTitle.text = customiser.enterAmountTitle(newState)

                initialiseLowerSlotIfNeeded(newState)
                initialiseUpperSlotIfNeeded(newState)

                lowerSlot?.update(newState)
                upperSlot?.update(newState)

                showFlashMessageIfNeeded(newState)
            }
        }
    }

    override fun getActionName(): String = customiser.enterAmountTitle(state)

    private fun FragmentTxFlowEnterAmountBinding.showFlashMessageIfNeeded(
        newState: TransactionState
    ) {
        customiser.issueFlashMessage(newState, amountSheetInput.configuration.inputCurrency)?.let {
            when (customiser.selectIssueType(newState)) {
                IssueType.ERROR -> {
                    amountSheetInput.showError(it, customiser.shouldDisableInput(state.errorState))
                }
                IssueType.INFO -> {
                    amountSheetInput.showInfo(it) {
                        // dismiss()
                        KycNavHostActivity.start(requireActivity(), CampaignType.Swap, true)
                    }
                }
            }
        } ?: showFeesTooHighMessageOrHide(newState)
    }

    private fun FragmentTxFlowEnterAmountBinding.showFeesTooHighMessageOrHide(
        newState: TransactionState
    ) {
        val feesTooHighMsg = customiser.issueFeesTooHighMessage(state)
        if (newState.pendingTx != null && newState.pendingTx.isLowOnBalance() && feesTooHighMsg != null) {
            amountSheetInput.showError(
                errorMessage = feesTooHighMsg
            )
        } else {
            binding.amountSheetInput.hideLabels()
        }
    }

    private fun FragmentTxFlowEnterAmountBinding.initialiseUpperSlotIfNeeded(newState: TransactionState) {
        if (upperSlot == null) {
            upperSlot = customiser.installEnterAmountUpperSlotView(
                requireContext(),
                frameUpperSlot,
                newState
            ).apply {
                initControl(model, customiser, analyticsHooks)
            }
            (lowerSlot as? ExpandableTxFlowWidget)?.let {
                it.expanded.observeOn(AndroidSchedulers.mainThread()).subscribe { expanded ->
                    upperSlot?.setVisible(!expanded)
                    configureCtaButton()
                }
            }
        }
    }

    private fun configureCtaButton() {
        val layoutParams: ViewGroup.MarginLayoutParams =
            binding.amountSheetCtaButton.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = resources.getDimension(R.dimen.medium_margin).toInt()
        binding.amountSheetCtaButton.layoutParams = layoutParams
    }

    private fun FragmentTxFlowEnterAmountBinding.initialiseLowerSlotIfNeeded(newState: TransactionState) {
        if (lowerSlot == null) {
            lowerSlot = customiser.installEnterAmountLowerSlotView(
                requireContext(),
                frameLowerSlot,
                newState
            ).apply {
                initControl(model, customiser, analyticsHooks)
            }
        }
    }

    // in this method we try to convert the fiat value coming out from
    // the view to a crypto which is withing the min and max limits allowed.
    // We use floor rounding for max and ceiling for min just to make sure that we wont have problem with rounding once
    // the amount reach the engine where the comparison with limits will happen.

    private fun convertFiatToCrypto(
        amount: FiatValue,
        rate: ExchangeRate.CryptoToFiat,
        state: TransactionState
    ): Money {
        val min = state.pendingTx?.minLimit ?: return rate.inverse().convert(amount)
        val max = state.maxSpendable
        val roundedUpAmount = rate.inverse(RoundingMode.CEILING, CryptoValue.DISPLAY_DP)
            .convert(amount)
        val roundedDownAmount = rate.inverse(RoundingMode.FLOOR, CryptoValue.DISPLAY_DP)
            .convert(amount)
        return if (roundedUpAmount >= min && roundedUpAmount <= max)
            roundedUpAmount
        else roundedDownAmount
    }

    private fun onCtaClick() {
        hideKeyboard()
        model.process(TransactionIntent.PrepareTransaction)
    }

    private fun hideKeyboard() {
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun FiatCryptoInputView.configure(
        newState: TransactionState,
        inputCurrency: CurrencyType
    ) {
        if (inputCurrency is CurrencyType.Crypto || newState.amount.takeIf { it is CryptoValue }?.isPositive == true) {
            val selectedFiat = newState.pendingTx?.selectedFiat ?: return
            configuration = FiatCryptoViewConfiguration(
                inputCurrency = CurrencyType.Crypto(newState.sendingAsset),
                exchangeCurrency = CurrencyType.Fiat(selectedFiat),
                predefinedAmount = newState.amount
            )
        } else {
            val selectedFiat = newState.pendingTx?.selectedFiat ?: return
            val fiatRate = newState.fiatRate ?: return
            val isCryptoWithFiatExchange = newState.amount is CryptoValue && fiatRate is ExchangeRate.CryptoToFiat
            configuration = FiatCryptoViewConfiguration(
                inputCurrency = CurrencyType.Fiat(selectedFiat),
                outputCurrency = CurrencyType.Fiat(selectedFiat),
                exchangeCurrency = newState.sendingAccount.currencyType(),
                predefinedAmount = if (isCryptoWithFiatExchange) {
                    fiatRate.convert(newState.amount)
                } else {
                    newState.amount
                }
            )
        }
        showKeyboard()
    }

    private fun showKeyboard() {
        val inputView = binding.amountSheetInput.findViewById<PrefixedOrSuffixedEditText>(
            R.id.enter_amount
        )

        inputView?.run {
            requestFocus()
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    companion object {
        private const val AMOUNT_DEBOUNCE_TIME_MS = 300L

        fun newInstance(): EnterAmountFragment = EnterAmountFragment()
    }
}

private fun BlockchainAccount.currencyType(): CurrencyType =
    when (this) {
        is CryptoAccount -> CurrencyType.Crypto(this.asset)
        is FiatAccount -> CurrencyType.Fiat(this.fiatCurrency)
        else -> throw IllegalStateException("Account not supported")
    }

private fun PendingTx.isLowOnBalance() =
    feeSelection.selectedLevel != FeeLevel.None &&
        availableBalance.isZero && totalBalance.isPositive
