package piuk.blockchain.android.ui.activity.adapter

import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter

class ActivityDetailsDelegateAdapter(
    onActionItemClicked: () -> Unit,
    onDescriptionItemUpdated: (String) -> Unit,
    onCancelActionItemClicked: () -> Unit
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {

    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(ActivityDetailInfoItemDelegate())
            addAdapterDelegate(ActivityDetailDescriptionItemDelegate(onDescriptionItemUpdated))
            addAdapterDelegate(ActivityDetailActionItemDelegate(onActionItemClicked))
            addAdapterDelegate(ActivityDetailCancelActionItemDelegate(onCancelActionItemClicked))
        }
    }
}