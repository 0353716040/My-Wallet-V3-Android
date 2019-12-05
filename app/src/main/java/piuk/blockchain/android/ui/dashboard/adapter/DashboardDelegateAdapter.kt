package piuk.blockchain.android.ui.dashboard.adapter

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.CryptoCurrency
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.dashboard.announcements.StdAnnouncementDelegate
import piuk.blockchain.android.ui.dashboard.announcements.MiniAnnouncementDelegate

class DashboardDelegateAdapter(
    prefs: CurrencyPrefs,
    onCardClicked: (CryptoCurrency) -> Unit,
    analytics: Analytics
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {

    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(StdAnnouncementDelegate(analytics))
            addAdapterDelegate(MiniAnnouncementDelegate(analytics))
            addAdapterDelegate(BalanceCardDelegate())
            addAdapterDelegate(AssetCardDelegate(prefs, onCardClicked))
            addAdapterDelegate(EmptyCardDelegate())
        }
    }
}