package piuk.blockchain.android.ui.interest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.Singles
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.databinding.FragmentInterestDashboardBinding
import piuk.blockchain.android.ui.resources.AssetResources
import piuk.blockchain.android.ui.transactionflow.DialogFlow
import piuk.blockchain.android.ui.transactionflow.TransactionLauncher
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import timber.log.Timber

class InterestDashboardFragment : Fragment() {

    interface InterestDashboardHost {
        fun startKyc()
        fun showInterestSummarySheet(account: SingleAccount, asset: AssetInfo)
        fun startAccountSelection(filter: Single<List<BlockchainAccount>>, toAccount: SingleAccount)
    }

    val host: InterestDashboardHost by lazy {
        activity as? InterestDashboardHost ?: throw IllegalStateException(
            "Host fragment is not a InterestDashboardFragment.InterestDashboardHost"
        )
    }

    private var _binding: FragmentInterestDashboardBinding? = null
    private val binding: FragmentInterestDashboardBinding
        get() = _binding!!

    private val disposables = CompositeDisposable()
    private val custodialWalletManager: CustodialWalletManager by scopedInject()
    private val kycTierService: TierService by scopedInject()
    private val coincore: Coincore by scopedInject()
    private val assetResources: AssetResources by inject()
    private val txLauncher: TransactionLauncher by inject()

    private val listAdapter: InterestDashboardAdapter by lazy {
        InterestDashboardAdapter(
            assetResources = assetResources,
            disposables = disposables,
            custodialWalletManager = custodialWalletManager,
            verificationClicked = ::startKyc,
            itemClicked = ::interestItemClicked
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterestDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.interestDashboardList.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            adapter = listAdapter
        }

        loadInterestDetails()
    }

    private fun loadInterestDetails() {
        disposables +=
            Singles.zip(
                kycTierService.tiers(),
                custodialWalletManager.getInterestEnabledAssets()
            ).observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe {
                    binding.interestError.gone()
                    binding.interestDashboardProgress.visible()
                }
                .subscribeBy(
                    onSuccess = { (tiers, enabledAssets) ->
                        renderInterestDetails(tiers, enabledAssets)
                    },
                    onError = {
                        renderErrorState()
                        Timber.e("Error loading interest summary details $it")
                    }
                )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposables.clear()
        _binding = null
    }

    private fun renderInterestDetails(
        tiers: KycTiers,
        enabledAssets: List<AssetInfo>
    ) {
        val items = mutableListOf<InterestDashboardItem>()

        val isKycGold = tiers.isApprovedFor(KycTierLevel.GOLD)
        if (!isKycGold) {
            items.add(InterestIdentityVerificationItem)
        }

        enabledAssets.map {
            items.add(InterestAssetInfoItem(isKycGold, it))
        }

        listAdapter.items = items
        listAdapter.notifyDataSetChanged()

        with(binding) {
            interestDashboardProgress.gone()
            interestDashboardList.visible()
        }
    }

    private fun renderErrorState() {
        with(binding) {
            interestDashboardList.gone()
            interestDashboardProgress.gone()

            interestError.setDetails(
                title = R.string.interest_error_title,
                description = R.string.interest_error_desc,
                contactSupportEnabled = true
            ) {
                loadInterestDetails()
            }
            interestError.visible()
        }
    }

    fun refreshBalances() {
        // force redraw, so balances update
        listAdapter.notifyDataSetChanged()
    }

    private fun interestItemClicked(cryptoCurrency: AssetInfo, hasBalance: Boolean) {
        disposables += coincore[cryptoCurrency].accountGroup(AssetFilter.Interest).subscribe {
            val interestAccount = it.accounts.first()
            if (hasBalance) {
                host.showInterestSummarySheet(interestAccount, cryptoCurrency)
            } else {
                txLauncher.startFlow(
                    activity = requireActivity(),
                    target = it.accounts.first(),
                    action = AssetAction.InterestDeposit,
                    fragmentManager = parentFragmentManager,
                    flowHost = activity as DialogFlow.FlowHost
                )
            }
        }
    }

    private fun startKyc() {
        host.startKyc()
    }

    companion object {
        fun newInstance(): InterestDashboardFragment = InterestDashboardFragment()
    }
}