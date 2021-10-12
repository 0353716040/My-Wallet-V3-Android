package piuk.blockchain.android.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.blockchain.notifications.analytics.Analytics
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentTransferBinding
import piuk.blockchain.android.ui.transfer.analytics.TransferAnalyticsEvent
import piuk.blockchain.android.ui.transfer.receive.ReceiveFragment
import piuk.blockchain.android.ui.transfer.receive.TransferReceiveFragment
import piuk.blockchain.android.ui.transfer.send.TransferSendFragment
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy

class TransferFragment : Fragment() {

    private val startingView: TransferViewType by unsafeLazy {
        arguments?.getSerializable(PARAM_START_VIEW) as? TransferViewType ?: TransferViewType.TYPE_SEND
    }

    private val useDynamicAssets: Boolean by unsafeLazy {
        arguments?.getBoolean(USE_DYNAMIC_ASSETS) ?: false
    }

    private val analytics: Analytics by inject()

    private var _binding: FragmentTransferBinding? = null

    private val binding: FragmentTransferBinding
    get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurePager(useDynamicAssets)
        analytics.logEvent(TransferAnalyticsEvent.TransferViewed)
    }

    private fun configurePager(dynamicAssetsEnabled: Boolean) {
        binding.apply {
            transferTabs.setupWithViewPager(transferPager)
            transferPager.adapter = TransferPagerAdapter(
                listOf(getString(R.string.send), getString(R.string.common_receive)),
                dynamicAssetsEnabled,
                childFragmentManager
            )

            transferPager.setCurrentItem(
                when (startingView) {
                    TransferViewType.TYPE_SEND -> TransferViewType.TYPE_SEND.ordinal
                    TransferViewType.TYPE_RECEIVE -> TransferViewType.TYPE_RECEIVE.ordinal
                }, true
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PARAM_START_VIEW = "show_view"
        private const val USE_DYNAMIC_ASSETS = "user_dynamic_assets"

        fun newInstance(
            useDynamicAssets: Boolean,
            transferViewType: TransferViewType = TransferViewType.TYPE_SEND
        ): TransferFragment =
            TransferFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(USE_DYNAMIC_ASSETS, useDynamicAssets)
                    putSerializable(PARAM_START_VIEW, transferViewType)
                }
            }
    }

    enum class TransferViewType {
        TYPE_SEND,
        TYPE_RECEIVE
    }
}

class TransferPagerAdapter(
    private val titlesList: List<String>,
    private val dynamicAssetsEnabled: Boolean,
    fragmentManager: FragmentManager
) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int = titlesList.size
    override fun getPageTitle(position: Int): CharSequence =
        titlesList[position]

    override fun getItem(position: Int): Fragment =
        when (position) {
            0 -> TransferSendFragment.newInstance()
            else -> {
                if (dynamicAssetsEnabled) {
                    ReceiveFragment.newInstance()
                } else {
                    TransferReceiveFragment.newInstance()
                }
            }
        }
}