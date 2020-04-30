package piuk.blockchain.android.ui.activity.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.ui.urllinks.makeBlockExplorerUrl
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.multiaddress.TransactionSummary
import kotlinx.android.synthetic.main.dialog_activity_details_sheet.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.simplebuy.SimpleBuyActivity
import piuk.blockchain.android.ui.activity.adapter.ActivityDetailsDelegateAdapter
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.base.mvi.MviBottomSheet
import piuk.blockchain.androidcoreui.utils.extensions.visible

class ActivityDetailsBottomSheet :
    MviBottomSheet<ActivityDetailsModel, ActivityDetailsIntents, ActivityDetailState>() {

    interface Host : SlidingModalBottomDialog.Host {
        fun onShowBankDetailsSelected()
        fun onShowBankCancelOrder()
    }

    override val host: Host by lazy {
        super.host as? Host
            ?: throw IllegalStateException("Host fragment is not a ActivityDetailsBottomSheet.Host")
    }

    override val layoutResource: Int
        get() = R.layout.dialog_activity_details_sheet

    override val model: ActivityDetailsModel by inject()

    private val listAdapter: ActivityDetailsDelegateAdapter by lazy {
        ActivityDetailsDelegateAdapter(
            onActionItemClicked = { onActionItemClicked() },
            onDescriptionItemClicked = { onDescriptionItemClicked() },
            onCancelActionItemClicked = { onCancelActionItemClicked() }
        )
    }

    private val Bundle?.txId
        get() = this?.getString(ARG_TRANSACTION_HASH) ?: throw IllegalArgumentException(
            "Transaction id should not be null")

    private val Bundle?.cryptoCurrency
        get() = this?.getSerializable(ARG_CRYPTO_CURRENCY) as? CryptoCurrency
            ?: throw IllegalArgumentException("Cryptocurrency should not be null")

    override fun initControls(view: View) {
        view.details_list.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            adapter = listAdapter
        }
    }

    override fun render(newState: ActivityDetailState) {
        dialogView.apply {
            title.text = if (newState.isFeeTransaction) {
                getString(R.string.activity_details_title_fee)
            } else {
                mapToAction(newState.direction)
            }
            amount.text = newState.amount?.toStringWithSymbol()

            if (newState.direction == TransactionSummary.Direction.BUY) {
                if (newState.isPending || newState.isPendingExecution) {
                    custodial_tx_button.text =
                        getString(R.string.activity_details_view_bank_transfer_details)
                    custodial_tx_button.setOnClickListener {
                        host.onShowBankDetailsSelected()
                        dismiss()
                    }
                } else {
                    custodial_tx_button.text =
                        getString(R.string.activity_details_buy_again)
                    custodial_tx_button.setOnClickListener {
                        startActivity(SimpleBuyActivity.newInstance(requireContext()))
                        dismiss()
                    }
                }

                custodial_tx_button.visible()
            }
        }

        renderCompletedOrPending(newState.isPending, newState.isPendingExecution,
            newState.confirmations, newState.totalConfirmations, newState.direction,
            newState.isFeeTransaction)

        if (listAdapter.items != newState.listOfItems) {
            listAdapter.items = newState.listOfItems.toList()
            listAdapter.notifyDataSetChanged()
        }
    }

    private fun renderCompletedOrPending(
        pending: Boolean,
        pendingExecution: Boolean,
        confirmations: Int,
        totalConfirmations: Int,
        direction: TransactionSummary.Direction?,
        isFeeTransaction: Boolean
    ) {
        dialogView.apply {
            if (pending || pendingExecution) {
                if (confirmations != totalConfirmations) {
                    confirmation_label.text =
                        getString(R.string.activity_details_label_confirmations, confirmations,
                            totalConfirmations)
                    confirmation_progress.setProgress(
                        (confirmations / totalConfirmations.toFloat()) * 100)
                    confirmation_label.visible()
                    confirmation_progress.visible()
                }

                status.text = getString(when {
                    direction == TransactionSummary.Direction.SENT ||
                        direction == TransactionSummary.Direction.TRANSFERRED ->
                        R.string.activity_details_label_confirming
                    isFeeTransaction || direction == TransactionSummary.Direction.SWAP ->
                        R.string.activity_details_label_pending
                    direction == TransactionSummary.Direction.BUY ->
                        if (pending && !pendingExecution) {
                            R.string.activity_details_label_waiting_on_funds
                        } else {
                            R.string.activity_details_label_pending_execution
                        }
                    else -> R.string.activity_details_empty
                })
                status.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_status_unconfirmed)
                status.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.grey_800))
            } else {
                status.text = getString(R.string.activity_details_label_complete)
                status.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bkgd_status_received)
                status.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.green_600))
            }
        }
    }

    private fun onDescriptionItemClicked() {
        // TODO
    }

    private fun onCancelActionItemClicked() {
        host.onShowBankCancelOrder()
        dismiss()
    }

    private fun onActionItemClicked() {
        val explorerUri = makeBlockExplorerUrl(arguments.cryptoCurrency, arguments.txId)

        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(explorerUri)
            startActivity(this)
        }
    }

    private fun loadActivityDetails(
        cryptoCurrency: CryptoCurrency,
        txHash: String,
        isCustodial: Boolean
    ) {
        model.process(LoadActivityDetailsIntent(cryptoCurrency, txHash, isCustodial))
    }

    private fun mapToAction(direction: TransactionSummary.Direction?): String =
        when (direction) {
            TransactionSummary.Direction.TRANSFERRED -> getString(
                R.string.activity_details_title_transferred)
            TransactionSummary.Direction.RECEIVED -> getString(
                R.string.activity_details_title_received)
            TransactionSummary.Direction.SENT -> getString(R.string.activity_details_title_sent)
            TransactionSummary.Direction.BUY -> getString(R.string.activity_details_title_buy)
            TransactionSummary.Direction.SELL -> getString(R.string.activity_details_title_sell)
            TransactionSummary.Direction.SWAP -> getString(R.string.activity_details_title_swap)
            else -> ""
        }

    companion object {
        private const val ARG_CRYPTO_CURRENCY = "crypto_currency"
        private const val ARG_TRANSACTION_HASH = "tx_hash"
        private const val ARG_CUSTODIAL_TRANSACTION = "custodial_tx"

        fun newInstance(
            cryptoCurrency: CryptoCurrency,
            txHash: String,
            isCustodial: Boolean
        ): ActivityDetailsBottomSheet {
            return ActivityDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CRYPTO_CURRENCY, cryptoCurrency)
                    putString(ARG_TRANSACTION_HASH, txHash)
                    putBoolean(ARG_CUSTODIAL_TRANSACTION, isCustodial)
                }

                loadActivityDetails(cryptoCurrency, txHash, isCustodial)
            }
        }
    }
}