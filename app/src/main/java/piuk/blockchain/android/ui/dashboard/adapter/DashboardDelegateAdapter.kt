package piuk.blockchain.android.ui.dashboard.adapter

import com.blockchain.notifications.analytics.Analytics
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.AssetInfo
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.ui.adapters.AdapterDelegatesManager
import piuk.blockchain.android.ui.adapters.DelegationAdapter
import piuk.blockchain.android.ui.dashboard.announcements.MiniAnnouncementDelegate
import piuk.blockchain.android.ui.dashboard.announcements.StdAnnouncementDelegate
import piuk.blockchain.android.ui.resources.AssetResources

class DashboardDelegateAdapter(
    prefs: CurrencyPrefs,
    onCardClicked: (AssetInfo) -> Unit,
    analytics: Analytics,
    onFundsItemClicked: (FiatAccount) -> Unit,
    coincore: Coincore,
    assetResources: AssetResources
) : DelegationAdapter<Any>(AdapterDelegatesManager(), emptyList()) {

    init {
        // Add all necessary AdapterDelegate objects here
        with(delegatesManager) {
            addAdapterDelegate(StdAnnouncementDelegate(analytics))
            addAdapterDelegate(MiniAnnouncementDelegate(analytics))
            addAdapterDelegate(BalanceCardDelegate(prefs.selectedFiatCurrency, coincore, assetResources))
            addAdapterDelegate(
                FundsCardDelegate(
                    prefs.selectedFiatCurrency,
                    onFundsItemClicked
                )
            )
            addAdapterDelegate(AssetCardDelegate(prefs, assetResources, onCardClicked))
            addAdapterDelegate(EmptyCardDelegate())
        }
    }
}