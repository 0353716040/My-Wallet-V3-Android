package piuk.blockchain.android.ui.transfer.send.flow

import android.graphics.Typeface.BOLD
import android.net.Uri
import android.text.InputFilter
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.koin.scopedInject
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.ui.urllinks.INTEREST_PRIVACY_POLICY
import com.blockchain.ui.urllinks.INTEREST_TERMS_OF_SERVICE
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRates
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.dialog_send_confirm.view.*
import kotlinx.android.synthetic.main.item_send_confirm_details.view.*
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.activity.detail.adapter.INPUT_FIELD_FLAGS
import piuk.blockchain.android.ui.activity.detail.adapter.MAX_NOTE_LENGTH
import piuk.blockchain.android.ui.base.SlidingModalBottomDialog
import piuk.blockchain.android.ui.transfer.send.FlowInputSheet
import piuk.blockchain.android.ui.transfer.send.NoteState
import piuk.blockchain.android.ui.transfer.send.SendErrorState
import piuk.blockchain.android.ui.transfer.send.SendIntent
import piuk.blockchain.android.ui.transfer.send.SendState
import piuk.blockchain.android.ui.transfer.send.SendStep
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcoreui.utils.extensions.gone
import timber.log.Timber

data class PendingTxItem(
    val label: String,
    val value: String
)

class ConfirmTransactionSheet(
    host: SlidingModalBottomDialog.Host
) : FlowInputSheet(host) {
    override val layoutResource: Int = R.layout.dialog_send_confirm

    private val stringUtils: StringUtils by inject()
    private val exchangeRates: ExchangeRates by scopedInject()
    private val prefs: CurrencyPrefs by scopedInject()

    private val detailsAdapter = DetailsAdapter()
    private var state = SendState()

    override fun render(newState: SendState) {
        Timber.d("!SEND!> Rendering! ConfirmTransactionSheet")
        require(newState.currentStep == SendStep.CONFIRM_DETAIL)

        val totalAmount = (newState.sendAmount + newState.feeAmount).toStringWithSymbol()
        detailsAdapter.populate(
            listOf(
                PendingTxItem(getString(R.string.common_send),
                    newState.sendAmount.toStringWithSymbol()),
                PendingTxItem(getString(R.string.common_from), newState.sendingAccount.label),
                PendingTxItem(getString(R.string.common_to), newState.sendTarget.label),
                addFeeItem(newState),
                PendingTxItem(getString(R.string.common_total), totalAmount)
            )
        )

        showAddNoteIfSupported(newState)
        showNoteState(newState)

        // TODO if new state tx options have confirmation
        dialogView.confirm_details_bottom_view_switcher.displayedChild = 0

        setupTosAndPPLinks()
        setupHoldingValues(newState.sendAmount)
        setupCheckboxEvents()

        dialogView.confirm_cta_button.text = getString(R.string.send_confirmation_cta_button,
            totalAmount)

        state = newState
    }

    private fun showNoteState(newState: SendState) {
        if (newState.note.isNotEmpty()) {
            dialogView.confirm_details_note_input.setText(newState.note,
                TextView.BufferType.EDITABLE)
        } else {
            when (newState.noteState) {
                NoteState.UPDATE_SUCCESS -> {
                    Toast.makeText(requireContext(),
                        getString(R.string.send_confirmation_add_note_success), Toast.LENGTH_SHORT)
                        .show()
                }
                NoteState.UPDATE_ERROR -> {
                    // can this happen?
                }
                NoteState.NOT_SET -> {
                    // do nothing
                }
            }
        }
    }

    private fun showAddNoteIfSupported(state: SendState) {
        state.transactionNoteSupported?.let {
            if (it) {
                dialogView.confirm_details_note_input.apply {
                    inputType = INPUT_FIELD_FLAGS
                    filters = arrayOf(InputFilter.LengthFilter(MAX_NOTE_LENGTH))

                    setOnEditorActionListener { v, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE && v.text.isNotEmpty()) {
                            model.process(SendIntent.NoteAdded(v.text.toString()))
                            clearFocus()
                        }

                        false
                    }
                }
            } else {
                dialogView.confirm_details_note_holder.gone()
            }
        } ?: model.process(SendIntent.RequestTransactionNoteSupport)
    }

    private fun addFeeItem(state: SendState): PendingTxItem {
        val feeTitle = getString(R.string.common_spaced_strings,
            getString(R.string.send_confirmation_fee),
            getString(R.string.send_confirmation_regular_estimation))
        return if (state.errorState == SendErrorState.FEE_REQUEST_FAILED) {
            PendingTxItem(feeTitle, getString(R.string.send_confirmation_fee_error))
        } else {
            PendingTxItem(feeTitle, state.feeAmount.toStringWithSymbol())
        }
    }

    private fun setupTosAndPPLinks() {
        val linksMap = mapOf<String, Uri>(
            "interest_tos" to Uri.parse(INTEREST_TERMS_OF_SERVICE),
            "interest_pp" to Uri.parse(INTEREST_PRIVACY_POLICY)
        )
        dialogView.confirm_details_tos_pp_checkbox.text =
            stringUtils.getStringWithMappedLinks(
                R.string.send_confirmation_interest_tos_pp,
                linksMap,
                requireActivity()
            )
        dialogView.confirm_details_tos_pp_checkbox.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupHoldingValues(sendAmount: CryptoValue) {
        val part1 = getString(R.string.send_confirmation_interest_holding_period_1)
        val part2 =
            sendAmount.toFiat(exchangeRates, prefs.selectedFiatCurrency).toStringWithSymbol()
        val part3 = getString(R.string.send_confirmation_interest_holding_period_2,
            sendAmount.toStringWithSymbol())
        val sb = SpannableStringBuilder()
        sb.append(part1)
        sb.append(part2)
        sb.setSpan(StyleSpan(BOLD), part1.length, part1.length + part2.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(part3)
        dialogView.confirm_details_holdings_checkbox.setText(sb, TextView.BufferType.SPANNABLE)
    }

    private fun setupCheckboxEvents() {
        dialogView.confirm_cta_button.isEnabled = false

        dialogView.confirm_details_tos_pp_checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (dialogView.confirm_details_holdings_checkbox.isChecked) {
                    dialogView.confirm_cta_button.isEnabled = true
                }
            } else {
                dialogView.confirm_cta_button.isEnabled = false
            }
        }

        dialogView.confirm_details_holdings_checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (dialogView.confirm_details_tos_pp_checkbox.isChecked) {
                    dialogView.confirm_cta_button.isEnabled = true
                }
            } else {
                dialogView.confirm_cta_button.isEnabled = false
            }
        }
    }

    override fun initControls(view: View) {
        view.confirm_cta_button.setOnClickListener { onCtaClick() }

        with(view.confirm_details_list) {
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )

            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
            )
            adapter = detailsAdapter
        }

        view.confirm_sheet_back.setOnClickListener {
            model.process(SendIntent.ReturnToPreviousStep)
        }

        model.process(SendIntent.RequestFee)
    }

    private fun onCtaClick() {
        model.process(SendIntent.ExecuteTransaction)
    }

    companion object {
        private const val DESCRIPTION_INPUT = 0
        private const val CONFIRMATION_INPUT = 1
    }
}

class DetailsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val itemsList = mutableListOf<PendingTxItem>()

    internal fun populate(items: List<PendingTxItem>) {
        itemsList.clear()
        itemsList.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DetailsItemVH(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_send_confirm_details,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int =
        itemsList.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = itemsList[position]
        when (holder) {
            is DetailsItemVH -> holder.bind(item.label, item.value)
            else -> {
            }
        }
    }
}

private class DetailsItemVH(val parent: View) :
    RecyclerView.ViewHolder(parent),
    LayoutContainer {

    override val containerView: View?
        get() = itemView

    fun bind(label: String, value: String) {
        itemView.confirmation_item_label.text = label
        itemView.confirmation_item_value.text = value
    }
}
