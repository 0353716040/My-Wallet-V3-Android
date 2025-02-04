package piuk.blockchain.android.ui.transactionflow.flow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.coincore.TxConfirmation
import com.blockchain.coincore.TxConfirmationValue
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ItemCheckoutComplexInfoBinding
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.transactionflow.flow.ConfirmationPropertyKey
import piuk.blockchain.android.ui.transactionflow.flow.TxConfirmReadOnlyMapperCheckout

class ComplexConfirmationCheckoutDelegate(private val mapper: TxConfirmReadOnlyMapperCheckout) :
    AdapterDelegate<TxConfirmationValue> {
    override fun isForViewType(items: List<TxConfirmationValue>, position: Int): Boolean {
        return items[position].confirmation == TxConfirmation.COMPLEX_READ_ONLY
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        ComplexConfirmationCheckoutItemItemViewHolder(
            ItemCheckoutComplexInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            mapper
        )

    override fun onBindViewHolder(
        items: List<TxConfirmationValue>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as ComplexConfirmationCheckoutItemItemViewHolder).bind(
        items[position]
    )
}

private class ComplexConfirmationCheckoutItemItemViewHolder(
    val binding: ItemCheckoutComplexInfoBinding,
    private val mapper: TxConfirmReadOnlyMapperCheckout
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: TxConfirmationValue) {
        with(binding) {
            mapper.map(item).run {
                complexItemLabel.text = this[ConfirmationPropertyKey.LABEL] as String
                complexItemTitle.text = this[ConfirmationPropertyKey.TITLE] as String
                complexItemSubtitle.text = this[ConfirmationPropertyKey.SUBTITLE] as String
                this[ConfirmationPropertyKey.IS_IMPORTANT]?.let { isImportant ->
                    if (isImportant as Boolean) {
                        complexItemLabel.setTextAppearance(R.style.Text_Semibold_16)
                        complexItemTitle.setTextAppearance(R.style.Text_Semibold_16)
                    } else {
                        complexItemLabel.setTextAppearance(R.style.Text_Standard_14)
                        complexItemTitle.setTextAppearance(R.style.Text_Standard_14)
                    }
                }
            }
        }
    }
}
