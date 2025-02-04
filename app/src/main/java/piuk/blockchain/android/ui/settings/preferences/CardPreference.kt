package piuk.blockchain.android.ui.settings.preferences

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.CardStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import piuk.blockchain.android.R
import piuk.blockchain.android.cards.icon
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.loadInterMedium
import piuk.blockchain.android.util.visible
import piuk.blockchain.android.util.visibleIf

class CardPreference(
    private val card: PaymentMethod? = null,
    context: Context
) : Preference(context, null, R.attr.preferenceStyle, 0) {
    private val typeface: Typeface = context.loadInterMedium()

    init {
        widgetLayoutResource = R.layout.preference_cards_layout

        this.title = title // Forces setting fonts when Title is set via XML

        title = (card as? PaymentMethod.Card)?.uiLabel() ?: context.getString(R.string.add_card_title)
        icon = ContextCompat.getDrawable(
            context,
            (card as? PaymentMethod.Card)?.cardType?.icon()
                ?: R.drawable.ic_payment_card
        )
    }

    override fun setTitle(titleResId: Int) {
        title = context.getString(titleResId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title?.applyFont(typeface))
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val titleView = holder.findViewById(android.R.id.title) as? TextView
        val endDigits = holder.itemView.findViewById(R.id.end_digits) as AppCompatTextView
        val expDate = holder.itemView.findViewById(R.id.exp_date) as AppCompatTextView
        val expired = holder.itemView.findViewById(R.id.expired) as AppCompatTextView
        val addCard = holder.itemView.findViewById(R.id.add_card) as AppCompatImageView
        (card as? PaymentMethod.Card)?.let {
            endDigits.text = card.dottedEndDigits()
            expDate.text =
                context.getString(R.string.card_expiry_date, card.expireDate.formatted())
            expired.visibleIf { it.status == CardStatus.EXPIRED }
            endDigits.visibleIf { it.status != CardStatus.EXPIRED }
            expDate.visibleIf { it.status != CardStatus.EXPIRED }
            addCard.gone()
        } ?: kotlin.run {
            endDigits.gone()
            expDate.gone()
            expired.gone()
            expDate.gone()
            addCard.visible()
        }
        titleView?.ellipsize = TextUtils.TruncateAt.END
        titleView?.setSingleLine(true)

        holder.isDividerAllowedAbove = true
    }
}

private fun Date.formatted() =
    SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(this)
