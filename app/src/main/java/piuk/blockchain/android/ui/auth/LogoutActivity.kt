package piuk.blockchain.android.ui.auth

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.blockchain.swap.nabu.datamanagers.NabuDataManager
import org.koin.android.ext.android.inject
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.erc20.Erc20Account
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.android.util.OSUtil
import piuk.blockchain.androidbuysell.datamanagers.BuyDataManager
import piuk.blockchain.androidbuysell.datamanagers.CoinifyDataManager
import piuk.blockchain.androidbuysell.services.BuyConditions
import piuk.blockchain.androidcore.data.access.AccessState
import com.blockchain.swap.shapeshift.ShapeShiftDataManager
import piuk.blockchain.android.data.coinswebsocket.service.CoinsWebSocketService
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState
import piuk.blockchain.androidcore.utils.PersistentPrefs

class LogoutActivity : AppCompatActivity() {

    private val buyDataManager: BuyDataManager by inject()
    private val ethDataManager: EthDataManager by inject()
    private val paxAccount: Erc20Account by inject()
    private val bchDataManager: BchDataManager by inject()
    private val buyConditions: BuyConditions by inject()
    private val walletOptionsState: WalletOptionsState by inject()
    private val shapeShiftDataManager: ShapeShiftDataManager by inject()
    private val coinifyDataManager: CoinifyDataManager by inject()
    private val nabuDataManager: NabuDataManager by inject()
    private val osUtil: OSUtil by inject()
    private val loginState: AccessState by inject()
    private val prefs: PersistentPrefs by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == AccessState.LOGOUT_ACTION) {
            val intent = Intent(this, CoinsWebSocketService::class.java)

            // When user logs out, assume onboarding has been completed
            prefs.setValue(PersistentPrefs.KEY_ONBOARDING_COMPLETE, true)

            if (osUtil.isServiceRunning(CoinsWebSocketService::class.java)) {
                stopService(intent)
            }

            // TODO: 21/02/2018 I'm not sure this is a great way to reset things, but it'll
            // do for now until we've had a rethink. Should individual datamanagers get
            // Rx events and handle their own state during logout?
            clearData()
        }
    }

    private fun clearData() {
        buyDataManager.wipe()
        ethDataManager.clearEthAccountDetails()
        paxAccount.clear()
        bchDataManager.clearBchAccountDetails()
        shapeShiftDataManager.clearShapeShiftData()
        coinifyDataManager.clearAccessToken()
        nabuDataManager.clearAccessToken()

        buyConditions.wipe()
        walletOptionsState.wipe()

        loginState.isLoggedIn = false
        finishAffinity()
    }
}
