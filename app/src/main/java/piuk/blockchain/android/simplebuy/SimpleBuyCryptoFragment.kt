package piuk.blockchain.android.simplebuy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.blockchain.extensions.exhaustive
import com.blockchain.featureflags.GatedFeature
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.UndefinedPaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.models.data.RecurringBuyFrequency
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.utils.isLastDayOfTheMonth
import com.blockchain.utils.to12HourFormat
import com.bumptech.glide.Glide
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.FiatValue
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.cards.CardDetailsActivity
import piuk.blockchain.android.cards.CardDetailsActivity.Companion.ADD_CARD_REQUEST_CODE
import piuk.blockchain.android.cards.icon
import piuk.blockchain.android.databinding.FragmentSimpleBuyBuyCryptoBinding
import piuk.blockchain.android.ui.base.ErrorSlidingBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviFragment
import piuk.blockchain.android.ui.base.setupToolbar
import piuk.blockchain.android.ui.customviews.CurrencyType
import piuk.blockchain.android.ui.customviews.FiatCryptoViewConfiguration
import piuk.blockchain.android.ui.customviews.PrefixedOrSuffixedEditText
import piuk.blockchain.android.ui.dashboard.asDeltaPercent
import piuk.blockchain.android.ui.dashboard.sheets.WireTransferAccountDetailsBottomSheet
import piuk.blockchain.android.ui.kyc.navhost.KycNavHostActivity
import piuk.blockchain.android.ui.linkbank.BankAuthActivity
import piuk.blockchain.android.ui.linkbank.BankAuthSource
import piuk.blockchain.android.ui.resources.AssetResources
import piuk.blockchain.android.util.getResolvedColor
import piuk.blockchain.android.util.getResolvedDrawable
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.setAssetIconColoursWithTint
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import java.time.ZonedDateTime
import java.util.Locale

class SimpleBuyCryptoFragment :
    MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState, FragmentSimpleBuyBuyCryptoBinding>(),
    RecurringBuySelectionBottomSheet.Host,
    SimpleBuyScreen,
    PaymentMethodChangeListener,
    ChangeCurrencyHost {

    override val model: SimpleBuyModel by scopedInject()
    private val exchangeRateDataManager: ExchangeRateDataManager by scopedInject()
    private val assetResources: AssetResources by inject()
    private val assetCatalogue: AssetCatalogue by inject()
    private val features: InternalFeatureFlagApi by inject()

    private var lastState: SimpleBuyState? = null
    private val compositeDisposable = CompositeDisposable()

    private val asset: AssetInfo by unsafeLazy {
        arguments?.getString(ARG_CRYPTO_ASSET)?.let {
            assetCatalogue.fromNetworkTicker(it)
        } ?: throw IllegalArgumentException("No cryptoCurrency specified")
    }

    private val preselectedMethodId: String? by unsafeLazy {
        arguments?.getString(ARG_PAYMENT_METHOD_ID)
    }

    private val isRecurringBuyEnabled: Boolean by lazy {
        features.isFeatureEnabled(GatedFeature.RECURRING_BUYS)
    }

    override fun navigator(): SimpleBuyNavigator =
        (activity as? SimpleBuyNavigator)
            ?: throw IllegalStateException("Parent must implement SimpleBuyNavigator")

    private val currencyPrefs: CurrencyPrefs by inject()

    override fun onBackPressed(): Boolean = true

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSimpleBuyBuyCryptoBinding =
        FragmentSimpleBuyBuyCryptoBinding.inflate(inflater, container, false)

    override fun onResume() {
        super.onResume()
        model.process(SimpleBuyIntent.FetchBuyLimits(currencyPrefs.selectedFiatCurrency, asset))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        activity.setupToolbar(getString(R.string.tx_title_buy, asset.ticker))

        model.process(SimpleBuyIntent.FlowCurrentScreen(FlowScreen.ENTER_AMOUNT))
        model.process(
            SimpleBuyIntent.FetchSuggestedPaymentMethod(
                currencyPrefs.selectedFiatCurrency,
                preselectedMethodId
            )
        )
        model.process(SimpleBuyIntent.FetchSupportedFiatCurrencies)
        analytics.logEvent(SimpleBuyAnalytics.BUY_FORM_SHOWN)

        compositeDisposable += binding.inputAmount.amount.subscribe {
            when (it) {
                is FiatValue -> model.process(SimpleBuyIntent.AmountUpdated(it))
                else -> throw IllegalStateException("CryptoValue is not supported as input yet")
            }
        }

        binding.btnContinue.setOnClickListener { startBuy() }

        binding.paymentMethodDetailsRoot.setOnClickListener {
            showPaymentMethodsBottomSheet(
                if (lastState?.paymentOptions?.availablePaymentMethods?.any { it.canUsedForPaying() } == true) {
                    PaymentMethodsChooserState.AVAILABLE_TO_PAY
                } else {
                    PaymentMethodsChooserState.AVAILABLE_TO_ADD
                }
            )
        }

        compositeDisposable += binding.inputAmount.onImeAction.subscribe {
            if (it == PrefixedOrSuffixedEditText.ImeOptions.NEXT)
                startBuy()
        }

        binding.recurringBuyCta.setOnClickListener {
            if (lastState?.isSelectedPaymentMethodRecurringBuyEligible() == true) {
                showBottomSheet(
                    lastState?.let {
                        RecurringBuySelectionBottomSheet.newInstance(it.recurringBuyFrequency)
                    }
                )
            } else {
                showDialogRecurringBuyUnavailable()
            }
        }
    }

    override fun showAvailableToAddPaymentMethods() =
        showPaymentMethodsBottomSheet(PaymentMethodsChooserState.AVAILABLE_TO_ADD)

    private fun showPaymentMethodsBottomSheet(state: PaymentMethodsChooserState) {
        lastState?.paymentOptions?.let {
            showBottomSheet(PaymentMethodChooserBottomSheet.newInstance(
                when (state) {
                    PaymentMethodsChooserState.AVAILABLE_TO_PAY -> it.availablePaymentMethods.filter { method ->
                        method.canUsedForPaying()
                    }
                    PaymentMethodsChooserState.AVAILABLE_TO_ADD -> it.availablePaymentMethods.filter { method ->
                        method.canBeAdded()
                    }
                }))
        }
    }

    private fun isRecurringFrequencyAvailableForPaymentMethod(): Boolean {
        val intervalSelected = lastState?.recurringBuyFrequency
        val canBeUsedForRecurringBuy = lastState?.isSelectedPaymentMethodRecurringBuyEligible() ?: false
        return !(!canBeUsedForRecurringBuy && intervalSelected != RecurringBuyFrequency.ONE_TIME)
    }

    private fun startBuy() {
        lastState?.let {
            if (canContinue(it)) {
                model.process(SimpleBuyIntent.BuyButtonClicked)
                model.process(SimpleBuyIntent.CancelOrderIfAnyAndCreatePendingOne)
                analytics.logEvent(
                    buyConfirmClicked(
                        lastState?.order?.amount?.valueMinor.toString(),
                        lastState?.fiatCurrency.orEmpty(),
                        lastState?.selectedPaymentMethod?.paymentMethodType?.toAnalyticsString().orEmpty()
                    )
                )
                check(lastState?.order?.amount != null)
                check(lastState?.maxFiatAmount != null)
                check(lastState?.selectedCryptoAsset != null)

                analytics.logEvent(
                    BuyAmountEntered(
                        lastState?.order?.amount ?: return,
                        lastState?.maxFiatAmount ?: return,
                        lastState?.selectedCryptoAsset?.ticker ?: return
                    )
                )
            }
        }
    }

    private fun showDialogRecurringBuyUnavailable() {
        showAlert(
            AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
                .setTitle(R.string.recurring_buy_unavailable_title)
                .setMessage(R.string.recurring_buy_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(R.string.recurring_buy_cta_alert) { dialog, _ ->
                    val interval = RecurringBuyFrequency.ONE_TIME
                    model.process(SimpleBuyIntent.RecurringBuyIntervalUpdated(interval))
                    binding.recurringBuyCta.text = interval.toHumanReadableRecurringBuy(requireContext())
                    dialog.dismiss()
                }
                .create()
        )
    }

    override fun onFiatCurrencyChanged(fiatCurrency: String) {
        if (fiatCurrency == lastState?.fiatCurrency) return
        model.process(SimpleBuyIntent.FiatCurrencyUpdated(fiatCurrency))
        model.process(
            SimpleBuyIntent.FetchBuyLimits(
                fiatCurrency,
                lastState?.selectedCryptoAsset ?: return
            )
        )
        model.process(SimpleBuyIntent.FetchSuggestedPaymentMethod(currencyPrefs.selectedFiatCurrency))
        analytics.logEvent(CurrencyChangedFromBuyForm(fiatCurrency))
    }

    override fun render(newState: SimpleBuyState) {
        lastState = newState

        if (newState.errorState != null) {
            handleErrorState(newState.errorState)
            return
        }

        binding.recurringBuyCta.text = newState.recurringBuyFrequency.toHumanReadableRecurringBuy(requireContext())

        newState.selectedCryptoAsset?.let {
            binding.inputAmount.configuration = FiatCryptoViewConfiguration(
                inputCurrency = CurrencyType.Fiat(newState.fiatCurrency),
                outputCurrency = CurrencyType.Fiat(newState.fiatCurrency),
                exchangeCurrency = CurrencyType.Crypto(it),
                canSwap = false,
                predefinedAmount = newState.order.amount ?: FiatValue.zero(newState.fiatCurrency)
            )
            binding.buyIcon.setAssetIconColoursWithTint(it)
        }
        newState.selectedCryptoAsset?.let {
            assetResources.loadAssetIcon(binding.cryptoIcon, it)
            binding.cryptoText.text = it.name
        }

        newState.exchangePriceWithDelta?.let {
            binding.cryptoExchangeRate.text = it.price.toStringWithSymbol()
            binding.priceDelta.asDeltaPercent(it.delta)
        }

        newState.maxFiatAmount.takeIf { it.currencyCode == currencyPrefs.selectedFiatCurrency }?.let {
            binding.inputAmount.maxLimit = it
        }

        if (newState.paymentOptions.availablePaymentMethods.isEmpty()) {
            hidePaymentMethod()
        } else {
            newState.selectedPaymentMethodDetails?.let {
                renderDefinedPaymentMethod(it)
            }
        }

        binding.btnContinue.isEnabled = canContinue(newState)
        newState.error?.let {
            handleError(it, newState)
        } ?: kotlin.run {
            clearError()
        }

        if (newState.confirmationActionRequested &&
            newState.kycVerificationState != null &&
            newState.orderState == OrderState.PENDING_CONFIRMATION
        ) {
            handlePostOrderCreationAction(newState)
        }

        newState.newPaymentMethodToBeAdded?.let {
            handleNewPaymentMethodAdding(newState)
        }

        newState.linkBankTransfer?.let {
            model.process(SimpleBuyIntent.ResetLinkBankTransfer)
            startActivityForResult(
                BankAuthActivity.newInstance(
                    it, BankAuthSource.SIMPLE_BUY, requireContext()
                ), BankAuthActivity.LINK_BANK_REQUEST_CODE
            )
        }
    }

    private fun handleNewPaymentMethodAdding(state: SimpleBuyState) {
        require(state.newPaymentMethodToBeAdded is UndefinedPaymentMethod)
        addPaymentMethod(state.newPaymentMethodToBeAdded.paymentMethodType)
        model.process(SimpleBuyIntent.AddNewPaymentMethodHandled)
        model.process(SimpleBuyIntent.SelectedPaymentMethodUpdate(state.newPaymentMethodToBeAdded))
    }

    private fun handlePostOrderCreationAction(newState: SimpleBuyState) {
        when {
            newState.selectedPaymentMethod?.isActive() == true -> {
                navigator().goToCheckOutScreen()
            }
            newState.selectedPaymentMethod?.isEligible == true -> {
                addPaymentMethod(newState.selectedPaymentMethod.paymentMethodType)
            }
            else -> {
                require(newState.kycVerificationState != null)
                require(newState.kycVerificationState != KycState.VERIFIED_AND_ELIGIBLE)
                when (newState.kycVerificationState) {
                    // Kyc state unknown because error, or gold docs unsubmitted
                    KycState.PENDING -> {
                        startKyc()
                    }
                    // Awaiting results state
                    KycState.IN_REVIEW,
                    KycState.UNDECIDED -> {
                        navigator().goToKycVerificationScreen()
                    }
                    // Got results, kyc verification screen will show error
                    KycState.VERIFIED_BUT_NOT_ELIGIBLE,
                    KycState.FAILED -> {
                        navigator().goToKycVerificationScreen()
                    }
                    KycState.VERIFIED_AND_ELIGIBLE -> throw IllegalStateException(
                        "Payment method should be active or eligible"
                    )
                }.exhaustive
            }
        }
    }

    /**
     * Once User selects the option to Link a bank then his/her Kyc status is checked.
     * If is VERIFIED_AND_ELIGIBLE then we try to link a bank and if the fetched partner is supported
     * then the LinkBankActivity is launched.
     * In case that user is not VERIFIED_AND_ELIGIBLE then we just select the payment method and when
     * user presses Continue the KYC flow is launched
     */

    private fun startKyc() {
        model.process(SimpleBuyIntent.NavigationHandled)
        model.process(SimpleBuyIntent.KycStarted)
        analytics.logEvent(SimpleBuyAnalytics.START_GOLD_FLOW)
        KycNavHostActivity.startForResult(this, CampaignType.SimpleBuy, SimpleBuyActivity.KYC_STARTED)
    }

    private fun canContinue(state: SimpleBuyState) =
        state.isAmountValid && state.selectedPaymentMethod != null && !state.isLoading

    private fun renderDefinedPaymentMethod(selectedPaymentMethod: PaymentMethod) {
        binding.frequencySpinner.visibleIf { isRecurringBuyEnabled }
        if (isRecurringBuyEnabled) renderRecurringBuy(selectedPaymentMethod)

        when (selectedPaymentMethod) {
            is PaymentMethod.Card -> renderCardPayment(selectedPaymentMethod)
            is PaymentMethod.Funds -> renderFundsPayment(selectedPaymentMethod)
            is PaymentMethod.Bank -> renderBankPayment(selectedPaymentMethod)
            is PaymentMethod.UndefinedCard -> renderUndefinedCardPayment(selectedPaymentMethod)
            is PaymentMethod.UndefinedBankTransfer -> renderUndefinedBankTransfer(selectedPaymentMethod)
            else -> {
            }
        }
        with(binding) {
            paymentMethod.visible()
            paymentMethodSeparator.visible()
            paymentMethodDetailsRoot.visible()
            paymentMethodTitle.visible()
            paymentMethodLimit.visible()
        }
    }

    private fun renderRecurringBuy(paymentMethod: PaymentMethod) {
        with(binding) {
            if (lastState?.isSelectedPaymentMethodRecurringBuyEligible() == true) {
                recurringBuyCta.apply {
                    background = requireContext().getResolvedDrawable(R.drawable.bkgd_button_white_selector)
                    setTextColor(requireContext().getResolvedColor(R.color.button_white_text_states))
                }
            } else {
                if (!isRecurringFrequencyAvailableForPaymentMethod()) {
                    showDialogRecurringBuyUnavailable()
                }
                recurringBuyCta.apply {
                    background = requireContext().getResolvedDrawable(R.drawable.bkgd_grey_000_rounded)
                    setTextColor(requireContext().getResolvedColor(R.color.grey_800))
                }
            }
        }
    }

    private fun renderFundsPayment(paymentMethod: PaymentMethod.Funds) {
        with(binding) {
            paymentMethodBankInfo.gone()
            paymentMethodIcon.setImageResource(
                paymentMethod.icon()
            )
            paymentMethodTitle.text = getString(paymentMethod.label())

            paymentMethodLimit.text = paymentMethod.limits.max.toStringWithSymbol()
        }
    }

    private fun renderBankPayment(paymentMethod: PaymentMethod.Bank) {
        with(binding) {
            paymentMethodIcon.setImageResource(R.drawable.ic_bank_transfer)
            if (paymentMethod.iconUrl.isNotEmpty()) {
                Glide.with(requireContext()).load(paymentMethod.iconUrl).into(paymentMethodIcon)
            }

            paymentMethodTitle.text = paymentMethod.bankName
            paymentMethodBankInfo.text =
                requireContext().getString(
                    R.string.payment_method_type_account_info, paymentMethod.uiAccountType,
                    paymentMethod.accountEnding
                )
            paymentMethodBankInfo.visible()
            paymentMethodLimit.text =
                getString(R.string.payment_method_limit, paymentMethod.limits.max.toStringWithSymbol())
        }
    }

    private fun renderUndefinedCardPayment(selectedPaymentMethod: PaymentMethod.UndefinedCard) {
        with(binding) {
            paymentMethodBankInfo.gone()
            paymentMethodIcon.setImageResource(R.drawable.ic_payment_card)
            paymentMethodTitle.text = getString(R.string.credit_or_debit_card)
            paymentMethodLimit.text =
                getString(R.string.payment_method_limit, selectedPaymentMethod.limits.max.toStringWithSymbol())
        }
    }

    private fun renderUndefinedBankTransfer(selectedPaymentMethod: PaymentMethod.UndefinedBankTransfer) {
        with(binding) {
            paymentMethodBankInfo.gone()
            paymentMethodIcon.setImageResource(R.drawable.ic_bank_transfer)
            paymentMethodTitle.text = getString(R.string.link_a_bank)
            paymentMethodLimit.text =
                getString(R.string.payment_method_limit, selectedPaymentMethod.limits.max.toStringWithSymbol())
        }
    }

    private fun renderCardPayment(selectedPaymentMethod: PaymentMethod.Card) {
        with(binding) {
            paymentMethodBankInfo.gone()
            paymentMethodIcon.setImageResource(selectedPaymentMethod.cardType.icon())
            paymentMethodTitle.text = selectedPaymentMethod.detailedLabel()
            paymentMethodLimit.text =
                getString(R.string.payment_method_limit, selectedPaymentMethod.limits.max.toStringWithSymbol())
        }
    }

    private fun clearError() {
        binding.inputAmount.hideLabels()
    }

    private fun hidePaymentMethod() {
        with(binding) {
            paymentMethod.gone()
            paymentMethodSeparator.gone()
            paymentMethodDetailsRoot.gone()
        }
    }

    private fun handleErrorState(errorState: ErrorState) {
        if (errorState == ErrorState.LinkedBankNotSupported) {
            model.process(SimpleBuyIntent.ClearError)
            model.process(SimpleBuyIntent.ClearAnySelectedPaymentMethods)
            navigator().launchBankAuthWithError(errorState)
        } else {
            showBottomSheet(ErrorSlidingBottomDialog.newInstance(activity))
        }
    }

    private fun handleError(error: InputError, state: SimpleBuyState) {
        when (error) {
            InputError.ABOVE_MAX -> {
                binding.inputAmount.showError(
                    if (binding.inputAmount.configuration.inputCurrency.isFiat())
                        resources.getString(R.string.maximum_buy, state.maxFiatAmount.toStringWithSymbol())
                    else
                        resources.getString(
                            R.string.maximum_buy,
                            state.maxCryptoAmount(exchangeRateDataManager)?.toStringWithSymbol()
                        )
                )
            }
            InputError.BELOW_MIN -> {
                binding.inputAmount.showError(
                    if (binding.inputAmount.configuration.inputCurrency.isFiat())
                        resources.getString(R.string.minimum_buy, state.minFiatAmount.toStringWithSymbol())
                    else
                        resources.getString(
                            R.string.minimum_buy,
                            state.minCryptoAmount(exchangeRateDataManager)?.toStringWithSymbol()
                        )
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        model.process(SimpleBuyIntent.NavigationHandled)
    }

    override fun onIntervalSelected(interval: RecurringBuyFrequency) {
        model.process(SimpleBuyIntent.RecurringBuyIntervalUpdated(interval))
        binding.recurringBuyCta.text = interval.toHumanReadableRecurringBuy(requireContext())
    }

    override fun onSheetClosed() {
        model.process(SimpleBuyIntent.ClearError)
    }

    override fun onPaymentMethodChanged(paymentMethod: PaymentMethod) {
        model.process(SimpleBuyIntent.PaymentMethodChangeRequested(paymentMethod))
        if (paymentMethod.canUsedForPaying())
            analytics.logEvent(
                BuyPaymentMethodSelected(
                    paymentMethod.toNabuAnalyticsString()
                )
            )
    }

    private fun addPaymentMethod(type: PaymentMethodType) {
        when (type) {
            PaymentMethodType.PAYMENT_CARD -> {
                val intent = Intent(activity, CardDetailsActivity::class.java)
                startActivityForResult(intent, ADD_CARD_REQUEST_CODE)
            }
            PaymentMethodType.FUNDS -> {
                showBottomSheet(
                    WireTransferAccountDetailsBottomSheet.newInstance(
                        lastState?.fiatCurrency ?: return
                    )
                )
            }
            PaymentMethodType.BANK_TRANSFER -> {
                model.process(SimpleBuyIntent.LinkBankTransferRequested)
            }
            else -> {
            }
        }
        analytics.logEvent(PaymentMethodSelected(type.toAnalyticsString()))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ADD_CARD_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val preselectedId = (data?.extras?.getSerializable(CardDetailsActivity.CARD_KEY) as? PaymentMethod.Card)?.id
            updatePaymentMethods(preselectedId)
        }
        if (requestCode == BankAuthActivity.LINK_BANK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val preselectedId = data?.extras?.getString(BankAuthActivity.LINKED_BANK_ID_KEY)
            updatePaymentMethods(preselectedId)
        }
        if (requestCode == SimpleBuyActivity.KYC_STARTED) {
            if (resultCode == SimpleBuyActivity.RESULT_KYC_SIMPLE_BUY_COMPLETE) {
                model.process(SimpleBuyIntent.KycCompleted)
                navigator().goToKycVerificationScreen()
            } else if (resultCode == KycNavHostActivity.RESULT_KYC_FOR_SDD_COMPLETE) {
                model.process(
                    SimpleBuyIntent.UpdatePaymentMethodsAndAddTheFirstEligible(
                        currencyPrefs.selectedFiatCurrency
                    )
                )
            }
        }
    }

    private fun updatePaymentMethods(preselectedId: String?) {
        model.process(
            SimpleBuyIntent.FetchSuggestedPaymentMethod(
                currencyPrefs.selectedFiatCurrency,
                preselectedId
            )
        )
    }

    companion object {
        private const val ARG_CRYPTO_ASSET = "CRYPTO"
        private const val ARG_PAYMENT_METHOD_ID = "PAYMENT_METHOD_ID"

        fun newInstance(asset: AssetInfo, preselectedMethodId: String? = null): SimpleBuyCryptoFragment {
            return SimpleBuyCryptoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CRYPTO_ASSET, asset.ticker)
                    preselectedMethodId?.let { putString(ARG_PAYMENT_METHOD_ID, preselectedMethodId) }
                }
            }
        }
    }

    private enum class PaymentMethodsChooserState {
        AVAILABLE_TO_PAY, AVAILABLE_TO_ADD
    }
}

fun RecurringBuyFrequency.toHumanReadableRecurringBuy(context: Context): String {
    return when (this) {
        RecurringBuyFrequency.ONE_TIME -> context.getString(R.string.recurring_buy_one_time_short)
        RecurringBuyFrequency.DAILY -> context.getString(R.string.recurring_buy_daily)
        RecurringBuyFrequency.WEEKLY -> context.getString(R.string.recurring_buy_weekly)
        RecurringBuyFrequency.BI_WEEKLY -> context.getString(R.string.recurring_buy_bi_weekly)
        RecurringBuyFrequency.MONTHLY -> context.getString(R.string.recurring_buy_monthly)
        else -> context.getString(R.string.common_unknown)
    }
}

fun RecurringBuyFrequency.toHumanReadableRecurringDate(context: Context): String {
    val dateTime = ZonedDateTime.now()
    return when (this) {
        RecurringBuyFrequency.DAILY -> {
            context.getString(
                R.string.recurring_buy_frequency_subtitle_each_day,
                dateTime.to12HourFormat()
            )
        }
        RecurringBuyFrequency.BI_WEEKLY, RecurringBuyFrequency.WEEKLY -> {
            context.getString(
                R.string.recurring_buy_frequency_subtitle,
                dateTime.dayOfWeek.toString().toLowerCase(Locale.getDefault()).capitalize(Locale.getDefault())
            )
        }
        RecurringBuyFrequency.MONTHLY -> {
            if (dateTime.isLastDayOfTheMonth()) {
                context.getString(R.string.recurring_buy_frequency_subtitle_monthly_last_day)
            } else {
                context.getString(
                    R.string.recurring_buy_frequency_subtitle_monthly,
                    dateTime.dayOfMonth.toString()
                )
            }
        }
        RecurringBuyFrequency.ONE_TIME,
        RecurringBuyFrequency.UNKNOWN -> ""
    }
}

interface PaymentMethodChangeListener {
    fun onPaymentMethodChanged(paymentMethod: PaymentMethod)
    fun showAvailableToAddPaymentMethods()
}

interface ChangeCurrencyHost : SimpleBuyScreen {
    fun onFiatCurrencyChanged(fiatCurrency: String)
}

fun PaymentMethod.Funds.icon() =
    when (fiatCurrency) {
        "GBP" -> R.drawable.ic_funds_gbp
        "EUR" -> R.drawable.ic_funds_euro
        "USD" -> R.drawable.ic_funds_usd
        else -> throw IllegalStateException("Unsupported currency")
    }

fun PaymentMethod.Funds.label() =
    when (fiatCurrency) {
        "GBP" -> R.string.pounds
        "EUR" -> R.string.euros
        "USD" -> R.string.us_dollars
        else -> throw IllegalStateException("Unsupported currency")
    }
