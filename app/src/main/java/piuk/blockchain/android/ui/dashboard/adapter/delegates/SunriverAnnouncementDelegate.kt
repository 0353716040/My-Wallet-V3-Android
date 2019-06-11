package piuk.blockchain.android.ui.dashboard.adapter.delegates

import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.blockchain.kycui.sunriver.SunriverCardType
import kotlinx.android.synthetic.main.item_announcement_sunriver.view.*
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.balance.AnnouncementData
import piuk.blockchain.androidcoreui.utils.extensions.gone
import piuk.blockchain.androidcoreui.utils.extensions.inflate
import piuk.blockchain.androidcoreui.utils.extensions.visible

class SunriverAnnouncementDelegate<in T> : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is SunriverCard

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        SunriverAnnouncementViewHolder(parent.inflate(R.layout.item_announcement_sunriver))

    @Suppress("CascadeIf")
    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder,
        payloads: List<*>
    ) {
        (holder as SunriverAnnouncementViewHolder).bind(items[position] as SunriverCard)
    }

    private class SunriverAnnouncementViewHolder internal constructor(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        internal var button: Button = itemView.button_call_to_action
        internal var title: TextView = itemView.text_view_sunriver_announcement_title
        internal var message: TextView = itemView.text_view_sunriver_announcement_message
        internal var close: ImageView = itemView.imageview_close

        fun bind(data: SunriverCard) {
            itemView.setOnClickListener { data.linkFunction() }
            button.setOnClickListener { data.linkFunction() }
            close.setOnClickListener { data.closeFunction() }
            button.setTextOrHide(data.link)
            title.setText(data.title)
            message.setText(data.description)
        }
    }
}

private fun TextView.setTextOrHide(@StringRes text: Int?) {
    text?.let { this.setText(text); visible() } ?: this.gone()
}

data class SunriverCard(
    @StringRes override val title: Int,
    @StringRes override val description: Int,
    @StringRes override val link: Int? = null,
    @DrawableRes override val image: Int = R.drawable.vector_xlm_colored,
    override val closeFunction: () -> Unit,
    override val linkFunction: () -> Unit,
    override val prefsKey: String,
    override val emoji: String? = null
) : AnnouncementData {

    companion object {

        fun continueClaim(
            closeFunction: () -> Unit,
            linkFunction: () -> Unit
        ) = SunriverCard(
            title = R.string.sunriver_announcement_stellar_claim_title,
            description = R.string.sunriver_announcement_stellar_claim_message,
            link = R.string.sunriver_announcement_stellar_claim_cta,
            closeFunction = closeFunction,
            linkFunction = linkFunction,
            prefsKey = SunriverCardType.FinishSignUp.javaClass.simpleName
        )

        fun onTheWay(
            closeFunction: () -> Unit,
            linkFunction: () -> Unit
        ) = SunriverCard(
            title = R.string.sunriver_announcement_stellar_on_the_way_title,
            description = R.string.sunriver_announcement_stellar_on_the_way_message,
            closeFunction = closeFunction,
            linkFunction = linkFunction,
            prefsKey = SunriverCardType.Complete.javaClass.simpleName
        )
    }
}