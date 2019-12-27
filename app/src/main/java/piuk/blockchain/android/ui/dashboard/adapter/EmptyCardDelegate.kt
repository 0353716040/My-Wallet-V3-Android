package piuk.blockchain.android.ui.dashboard.adapter

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.ui.dashboard.EmptyDashboardItem
import piuk.blockchain.androidcoreui.utils.extensions.inflate

class EmptyCardDelegate<in T> : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is EmptyDashboardItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        EmptyCardViewHolder(parent.inflate(R.layout.item_dashboard_empty_card))

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder,
        payloads: List<*>
    ) {}
}

private class EmptyCardViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView)
