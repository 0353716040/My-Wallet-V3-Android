package piuk.blockchain.android.ui.transactionflow

import android.app.Activity
import androidx.fragment.app.FragmentManager
import com.blockchain.featureflags.GatedFeature
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.NullCryptoAccount
import com.blockchain.coincore.TransactionTarget
import piuk.blockchain.android.ui.transactionflow.fullscreen.TransactionFlowActivity

class TransactionLauncher(private val flags: InternalFeatureFlagApi) {

    fun startFlow(
        activity: Activity,
        fragmentManager: FragmentManager,
        flowHost: DialogFlow.FlowHost,
        action: AssetAction,
        sourceAccount: BlockchainAccount = NullCryptoAccount(),
        target: TransactionTarget = NullCryptoAccount()
    ) {
        if (flags.isFeatureEnabled(GatedFeature.FULL_SCREEN_TXS)) {
            activity.startActivity(TransactionFlowActivity.newInstance(activity, sourceAccount, target, action))
        } else {
            TransactionFlow(sourceAccount, target, action).also {
                it.startFlow(fragmentManager, flowHost)
            }
        }
    }
}