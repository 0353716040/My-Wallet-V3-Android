package piuk.blockchain.android.ui.transactionflow.flow

import android.view.LayoutInflater
import android.view.ViewGroup
import io.reactivex.rxjava3.core.Single
import org.koin.android.ext.android.inject
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.SingleAccount
import piuk.blockchain.android.databinding.DialogSheetAccountSelectorBinding
import piuk.blockchain.android.ui.transactionflow.engine.TransactionIntent
import piuk.blockchain.android.ui.transactionflow.engine.TransactionState
import piuk.blockchain.android.ui.transactionflow.flow.customisations.TargetSelectionCustomisations
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class SelectTargetAccountSheet : TransactionFlowSheet<DialogSheetAccountSelectorBinding>() {

    private val customiser: TargetSelectionCustomisations by inject()

    override fun render(newState: TransactionState) {
        with(binding) {
            accountList.initialise(
                source = Single.just(newState.availableTargets.map { it as SingleAccount }),
                status = customiser.selectTargetStatusDecorator(newState),
                assetAction = newState.action
            )
            accountListTitle.text = customiser.selectTargetAccountTitle(newState)
            accountListSubtitle.text = customiser.selectTargetAccountDescription(newState)
            accountListSubtitle.visible()
            accountListBack.visibleIf { newState.canGoBack }
        }
    }

    override fun initControls(binding: DialogSheetAccountSelectorBinding) {
        binding.apply {
            accountList.onAccountSelected = ::doOnAccountSelected
            accountList.onListLoaded = ::doOnListLoaded
            accountList.onLoadError = ::doOnLoadError
            accountListBack.setOnClickListener {
                model.process(TransactionIntent.ReturnToPreviousStep)
            }
        }
    }

    private fun doOnListLoaded(isEmpty: Boolean) {
        binding.progress.gone()
    }

    private fun doOnAccountSelected(account: BlockchainAccount) {
        require(account is SingleAccount)
        model.process(TransactionIntent.TargetAccountSelected(account))
        analyticsHooks.onTargetAccountSelected(account, state)
    }

    private fun doOnLoadError(it: Throwable) {
        binding.accountListEmpty.visible()
        binding.progress.gone()
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): DialogSheetAccountSelectorBinding =
        DialogSheetAccountSelectorBinding.inflate(inflater, container, false)
}