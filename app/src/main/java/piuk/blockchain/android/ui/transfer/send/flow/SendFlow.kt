package piuk.blockchain.android.ui.transfer.send.flow

import androidx.annotation.CallSuper
import androidx.annotation.UiThread
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.transfer.send.SendIntent
import piuk.blockchain.android.ui.transfer.send.SendModel
import piuk.blockchain.android.ui.transfer.send.SendState
import piuk.blockchain.android.ui.transfer.send.SendStep
import piuk.blockchain.android.ui.transfer.send.TransactionCompleteSheet
import piuk.blockchain.android.ui.transfer.send.TransactionInProgressSheet
import piuk.blockchain.android.ui.transfer.send.closeSendScope
import piuk.blockchain.android.ui.transfer.send.createSendScope
import piuk.blockchain.android.ui.transfer.send.sendScope
import timber.log.Timber

interface FlowStep

abstract class DialogFlow(
    private val fragmentManager: FragmentManager,
    private val host: FlowHost,
    private val bottomSheetTag: String = SHEET_FRAGMENT_TAG
) : SlidingModalBottomDialog.Host {

    interface FlowHost {
        fun onFlowFinished()
    }

    @CallSuper
    open fun startFlow() { }

    @CallSuper
    open fun finishFlow() {
        host.onFlowFinished()
    }

    @UiThread
    protected fun replaceBottomSheet(bottomSheet: BottomSheetDialogFragment?) {
        val oldSheet = fragmentManager.findFragmentByTag(bottomSheetTag)

        fragmentManager.beginTransaction()
            .apply { oldSheet?.let { sheet -> remove(sheet) } }
            .apply { bottomSheet?.let { sheet -> add(sheet, bottomSheetTag) } }
            .commitNow()
    }

    companion object {
        const val SHEET_FRAGMENT_TAG = "BOTTOM_SHEET"
    }
}

class SendFlow(
    private val account: CryptoAccount,
    private val passwordRequired: Boolean,
    private val disposables: CompositeDisposable,
    fragmentManager: FragmentManager,
    host: FlowHost,
    bottomSheetTag: String = SHEET_FRAGMENT_TAG
) : DialogFlow(
    fragmentManager,
    host,
    bottomSheetTag
) {
    private var currentStep: SendStep = SendStep.ZERO

    override fun startFlow() {
        super.startFlow()
        // Create the send scope
        openScope()
        // Get the model
        model.apply {
            // Trigger intent to set initial state: source account & password required
            disposables += state.subscribeBy(
                onNext = { handleStateChange(it) },
                onError = { Timber.e("Send state is broken: $it") }
            )
            process(SendIntent.Initialise(account, passwordRequired))
        }
    }

    override fun finishFlow() {
        currentStep = SendStep.ZERO
        closeScope()
        super.finishFlow()
    }

    private fun handleStateChange(newState: SendState) {
        if (currentStep != newState.currentStep) {
            currentStep = newState.currentStep
            if (currentStep == SendStep.ZERO) {
                onSendComplete()
            } else {
                showFlowStep(currentStep)
            }
        }
    }

    private fun showFlowStep(step: SendStep) {
        replaceBottomSheet(
            when (step) {
                SendStep.ZERO -> null
                SendStep.ENTER_PASSWORD -> EnterSecondPasswordSheet(this)
                SendStep.ENTER_ADDRESS -> EnterTargetAddressSheet(this)
                SendStep.ENTER_AMOUNT -> EnterAmountSheet(this)
                SendStep.CONFIRM_DETAIL -> ConfirmTransactionSheet(this)
                SendStep.IN_PROGRESS -> TransactionInProgressSheet(this)
                SendStep.SEND_ERROR -> TransactionErrorSheet(this)
                SendStep.SEND_COMPLETE -> TransactionCompleteSheet(this)
            }
        )
    }

    private fun openScope() =
        try {
            createSendScope()
        } catch (e: Throwable) {
            Timber.wtf("$e")
        }

    private fun closeScope() =
        closeSendScope()

    private val model: SendModel
        get() = sendScope().get()

    private fun onSendComplete() =
        finishFlow()

    override fun onSheetClosed() {
        finishFlow()
    }
}
