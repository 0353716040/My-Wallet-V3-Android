package piuk.blockchain.android.ui.buysell.launcher

import piuk.blockchain.androidcoreui.ui.base.View

interface BuySellLauncherView: View {

    fun onStartCoinifySignUp()

    fun onStartCoinifyOverview()

    fun finishPage()
}