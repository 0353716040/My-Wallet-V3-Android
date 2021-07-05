package piuk.blockchain.android.ui.transactionflow.fullscreen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.blockchain.koin.scopedInject
import info.blockchain.balance.AssetInfo
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAddress
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.databinding.FragmentTxFlowEnterAddressBinding
import piuk.blockchain.android.scan.QrScanResultProcessor
import piuk.blockchain.android.ui.customviews.EditTextUpdateThrottle
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.scan.QrExpected
import piuk.blockchain.android.ui.scan.QrScanActivity
import piuk.blockchain.android.ui.scan.QrScanActivity.Companion.getRawScanData
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TargetAddressSheetState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TargetSelectionCustomisations
import piuk.blockchain.android.ui.transactionflow.plugin.TxFlowWidget
import piuk.blockchain.android.util.getTextString
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.invisible
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf
import timber.log.Timber

class EnterTargetAddressFragment : TransactionFlowFragment<FragmentTxFlowEnterAddressBinding>() {

    private val customiser: TargetSelectionCustomisations by inject()
    private val qrProcessor: QrScanResultProcessor by scopedInject()
    private var sourceSlot: TxFlowWidget? = null

    private val disposables = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            addressEntry.addTextChangedListener(addressTextWatcher)
            btnScan.setOnClickListener { onLaunchAddressScan() }
            ctaButton.setOnClickListener { onCtaClick() }
            selectAnAccount.setOnClickListener { showMoreAccounts() }
            walletSelect.apply {
                onLoadError = {
                    hideTransferList()
                }
                onListLoaded = {
                    if (it) hideTransferList()
                }
            }
            //            addressSheetBack.setOnClickListener {
            //                model.process(TransactionIntent.ReturnToPreviousStep)
            //            }
        }
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTxFlowEnterAddressBinding =
        FragmentTxFlowEnterAddressBinding.inflate(inflater, container, false)

    private fun onAddressEditUpdated(s: Editable?) {
        val address = s.toString()

        if (address.isEmpty()) {
            model.process(TransactionIntent.EnteredAddressReset)
        } else {
            if (customiser.enterTargetAddressSheetState(state) is
                    TargetAddressSheetState.SelectAccountWhenOverMaxLimitSurpassed
            ) {
                binding.selectAnAccount.visible()
            } else {
                binding.walletSelect.clearSelectedAccount()
            }
            addressEntered(address, state.sendingAsset)
        }
    }

    override fun render(newState: TransactionState) {
        Timber.d("!TRANSACTION!> Rendering! EnterTargetAddressSheet")
        activity.setToolbarTitle(customiser.selectTargetAddressTitle(state))

        with(binding) {
            // this is commented out because if we cache from the newInstance we already have the sending account
            // but we need a better strategy to pass the state around
            // if (state.sendingAccount != newState.sendingAccount) {
            if (sourceSlot == null) {
                sourceSlot = customiser.installAddressSheetSource(requireContext(), fromDetails, newState)
            }
            sourceSlot?.update(newState)

            setupTransferList(customiser.enterTargetAddressSheetState(newState))
            setupLabels(newState)

            upsellGroup.visibleIf { customiser.shouldShowCustodialUpsell(newState) }

            if (customiser.enterTargetAddressSheetState(newState) is
                    TargetAddressSheetState.SelectAccountWhenOverMaxLimitSurpassed
            ) {
                selectAnAccount.visible()
            }

            if (customiser.selectTargetShowManualEnterAddress(newState)) {
                showManualAddressEntry(newState)
            } else {
                hideManualAddressEntry(newState)
            }

            customiser.issueFlashMessage(newState, null)?.let {
                addressEntry.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.red_000)
                )
                errorMsg.apply {
                    text = it
                    visible()
                }
            } ?: hideErrorState()

            // addressSheetBack.visibleIf { newState.canGoBack }

            ctaButton.isEnabled = newState.nextEnabled
            cacheState(newState)
        }
    }

    private fun setupLabels(state: TransactionState) {
        with(binding) {
            titleFrom.text = customiser.selectTargetSourceLabel(state)
            titleTo.text = customiser.selectTargetDestinationLabel(state)
            subtitle.visibleIf { customiser.selectTargetShouldShowSubtitle(state) }
            subtitle.text = customiser.selectTargetSubtitle(state)
            selectAccountCta.text = customiser.selectTargetAddressWalletsCta(state)
        }
    }

    private fun hideErrorState() {
        binding.errorMsg.invisible()
        binding.addressEntry.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.grey_000)
        )
    }

    private fun showManualAddressEntry(newState: TransactionState) {
        val address = if (newState.selectedTarget is CryptoAddress) {
            newState.selectedTarget.label
        } else {
            ""
        }

        with(binding) {
            if (address.isNotEmpty() && address != addressEntry.getTextString()) {
                addressEntry.setText(address, TextView.BufferType.EDITABLE)
            }
            addressEntry.hint = customiser.selectTargetAddressInputHint(newState)
            // set visibility of component here so bottom sheet grows to the correct height
            inputSwitcher.visible()
            inputSwitcher.displayedChild = NONCUSTODIAL_INPUT
        }
    }

    private fun hideManualAddressEntry(newState: TransactionState) {
        val msg = customiser.selectTargetNoAddressMessageText(newState)

        with(binding) {
            if (msg != null) {
                inputSwitcher.visible()
                noManualEnterMsg.text = msg

                internalSendClose.setOnClickListener {
                    inputSwitcher.gone()
                }

                titlePick.gone()
                pickSeparator.gone()
                inputSwitcher.displayedChild = CUSTODIAL_INPUT
            } else {
                inputSwitcher.gone()
                pickSeparator.gone()
                titlePick.gone()
            }
        }
    }

    private fun showMoreAccounts() {
        model.process(TransactionIntent.ShowMoreAccounts)
    }

    private fun setupTransferList(targetAddressSheetState: TargetAddressSheetState) {
        with(binding.walletSelect) {
            initialise(
                Single.just(targetAddressSheetState.accounts.filterIsInstance<BlockchainAccount>()),
                shouldShowSelectionStatus = true
            )
            // set visibility of component here so bottom sheet grows to the correct height
            visible()
            when (targetAddressSheetState) {
                is TargetAddressSheetState.SelectAccountWhenWithinMaxLimit -> {
                    onAccountSelected = {
                        accountSelected(it)
                    }
                }
                is TargetAddressSheetState.TargetAccountSelected -> {
                    updatedSelectedAccount(
                        targetAddressSheetState.accounts.filterIsInstance<BlockchainAccount>().first()
                    )
                    onAccountSelected = {
                        model.process(TransactionIntent.ShowTargetSelection)
                    }
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun hideTransferList() {
        binding.titlePick.gone()
        binding.walletSelect.gone()
    }

    private fun accountSelected(account: BlockchainAccount) {
        require(account is SingleAccount)
        analyticsHooks.onAccountSelected(account, state)

        binding.walletSelect.updatedSelectedAccount(account)
        // TODO update the selected target (account type) instead so the render method knows what to show  & hide
        setAddressValue("")
        model.process(TransactionIntent.TargetSelectionUpdated(account))
    }

    private fun onLaunchAddressScan() {
        analyticsHooks.onScanQrClicked(state)
        QrScanActivity.start(this, QrExpected.ASSET_ADDRESS_QR(state.sendingAsset))
    }

    private fun addressEntered(address: String, asset: AssetInfo) {
        analyticsHooks.onManualAddressEntered(state)
        model.process(TransactionIntent.ValidateInputTargetAddress(address, asset))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            QrScanActivity.SCAN_URI_RESULT -> handleScanResult(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }

    private fun setAddressValue(value: String) {
        with(binding.addressEntry) {
            removeTextChangedListener(addressTextWatcher)
            setText(value, TextView.BufferType.EDITABLE)
            setSelection(value.length)
            addTextChangedListener(addressTextWatcher)
        }
    }

    private fun handleScanResult(resultCode: Int, data: Intent?) {
        Timber.d("Got QR scan result!")
        if (resultCode == Activity.RESULT_OK) {
            data.getRawScanData()?.let { rawScan ->
                disposables += qrProcessor.processScan(rawScan, false)
                    .flatMapMaybe { qrProcessor.selectAssetTargetFromScan(state.sendingAsset, it) }
                    .subscribeBy(
                        onSuccess = {
                            // TODO update the selected target (address type) instead so the render method knows what to show  & hide
                            setAddressValue(it.address)
                            binding.walletSelect.clearSelectedAccount()
                            model.process(TransactionIntent.TargetSelectionUpdated(it))
                        },
                        onComplete = {
                            ToastCustom.makeText(
                                requireContext(),
                                getString(R.string.scan_mismatch_transaction_target, state.sendingAsset.ticker),
                                ToastCustom.LENGTH_SHORT,
                                ToastCustom.TYPE_GENERAL
                            )
                        },
                        onError = {
                            ToastCustom.makeText(
                                requireContext(),
                                getString(R.string.scan_failed),
                                ToastCustom.LENGTH_SHORT,
                                ToastCustom.TYPE_GENERAL
                            )
                        }
                    )
            }
        }
    }

    private fun onCtaClick() {
        analyticsHooks.onEnterAddressCtaClick(state)
        model.process(TransactionIntent.TargetSelected)
    }

    private val addressTextWatcher = EditTextUpdateThrottle(
        updateFn = ::onAddressEditUpdated,
        updateDelayMillis = ADDRESS_UPDATE_INTERVAL
    )

    companion object {
        private const val NONCUSTODIAL_INPUT = 0
        private const val CUSTODIAL_INPUT = 1
        private const val ADDRESS_UPDATE_INTERVAL = 500L

        fun newInstance(): EnterTargetAddressFragment = EnterTargetAddressFragment()
    }
}
