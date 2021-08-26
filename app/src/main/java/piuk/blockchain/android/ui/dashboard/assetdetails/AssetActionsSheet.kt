package piuk.blockchain.android.ui.dashboard.assetdetails

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.service.TierService
import info.blockchain.balance.AssetInfo
import com.blockchain.notifications.analytics.LaunchOrigin
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.databinding.DialogAssetActionsSheetBinding
import piuk.blockchain.android.databinding.ItemAssetActionBinding
import piuk.blockchain.android.ui.base.mvi.MviBottomSheet
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.customviews.account.CellDecorator
import piuk.blockchain.android.ui.customviews.account.DefaultCellDecorator
import piuk.blockchain.android.ui.customviews.account.PendingBalanceAccountDecorator
import piuk.blockchain.android.ui.customviews.account.StatusDecorator
import piuk.blockchain.android.ui.customviews.account.addViewToBottomWithConstraints
import piuk.blockchain.android.ui.customviews.account.removePossibleBottomView
import piuk.blockchain.android.ui.transactionflow.analytics.InterestAnalytics
import piuk.blockchain.android.ui.transfer.analytics.TransferAnalyticsEvent
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.setAssetIconColoursWithTint
import timber.log.Timber

class AssetActionsSheet :
    MviBottomSheet<AssetDetailsModel, AssetDetailsIntent, AssetDetailsState, DialogAssetActionsSheetBinding>() {
    private val disposables = CompositeDisposable()

    private val kycTierService: TierService by scopedInject()

    override val model: AssetDetailsModel by scopedInject()

    private val itemAdapter: AssetActionAdapter by lazy {
        AssetActionAdapter(
            disposable = disposables,
            statusDecorator = ::statusDecorator
        )
    }

    override fun render(newState: AssetDetailsState) {
        if (this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {

            newState.selectedAccount?.let {
                showAssetBalances(newState)
                itemAdapter.itemList = mapActions(it, newState.actions)
            }

            if (newState.errorState != AssetDetailsError.NONE) {
                showError(newState.errorState)
            }
        }
    }

    override fun initControls(binding: DialogAssetActionsSheetBinding) {
        binding.assetActionsList.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
            adapter = itemAdapter
        }

        binding.assetActionsBack.setOnClickListener {
            model.process(ClearActionStates)
            model.process(ReturnToPreviousStep)
            dispose()
        }
    }

    override fun dismiss() {
        super.dismiss()
        model.process(ClearSheetDataIntent)
        dispose()
    }

    private fun statusDecorator(account: BlockchainAccount): CellDecorator =
        if (account is CryptoAccount) {
            AssetActionsDecorator(account)
        } else {
            DefaultCellDecorator()
        }

    private fun showError(error: AssetDetailsError) =
        when (error) {
            AssetDetailsError.TX_IN_FLIGHT -> ToastCustom.makeText(
                requireContext(),
                getString(R.string.dashboard_asset_actions_tx_in_progress), Toast.LENGTH_SHORT,
                ToastCustom.TYPE_ERROR
            )
            else -> {
                // do nothing
            }
        }

    private fun showAssetBalances(state: AssetDetailsState) {
        with(binding.assetActionsAccountDetails) {
            updateAccount(
                state.selectedAccount.selectFirstAccount(),
                {},
                PendingBalanceAccountDecorator(state.selectedAccount.selectFirstAccount())
            )
        }
    }

    // we want to display Interest deposit only for Interest accounts and not for the accounts that
    // have the InterestDeposit as an available action (can be used as source account for interest deposit)
    private fun mapActions(
        account: BlockchainAccount,
        actions: AvailableActions
    ): List<AssetActionItem> {
        return when (val firstAccount = account.selectFirstAccount()) {
            is InterestAccount -> actions.toMutableList().apply {
                add(0, AssetAction.InterestDeposit)
            }.map { mapAction(it, firstAccount.asset, firstAccount) }
            else -> actions.toMutableList().apply {
                remove(AssetAction.InterestDeposit)
            }.map { mapAction(it, firstAccount.asset, firstAccount) }
        }
    }

    private fun mapAction(
        action: AssetAction,
        asset: AssetInfo,
        account: BlockchainAccount
    ): AssetActionItem =
        when (action) {
            // using the secondary ctor ensures the action is always enabled if it is present
            AssetAction.ViewActivity ->
                AssetActionItem(
                    getString(R.string.activities_title),
                    R.drawable.ic_tx_activity_clock,
                    getString(R.string.fiat_funds_detail_activity_details), asset,
                    action
                ) {
                    logActionEvent(AssetDetailsAnalytics.ACTIVITY_CLICKED, asset)
                    processAction(AssetAction.ViewActivity)
                }
            AssetAction.Send ->
                AssetActionItem(
                    account, getString(R.string.common_send), R.drawable.ic_tx_sent,
                    getString(
                        R.string.dashboard_asset_actions_send_dsc,
                        asset.ticker
                    ), asset, action
                ) {
                    logActionEvent(AssetDetailsAnalytics.SEND_CLICKED, asset)
                    processAction(AssetAction.Send)
                    analytics.logEvent(
                        TransferAnalyticsEvent.TransferClicked(
                            LaunchOrigin.CURRENCY_PAGE,
                            type = TransferAnalyticsEvent.AnalyticsTransferType.SEND
                        )
                    )
                }
            AssetAction.Receive ->
                AssetActionItem(
                    getString(R.string.common_receive), R.drawable.ic_tx_receive,
                    getString(
                        R.string.dashboard_asset_actions_receive_dsc,
                        asset.ticker
                    ), asset, action
                ) {
                    logActionEvent(AssetDetailsAnalytics.RECEIVE_CLICKED, asset)
                    processAction(AssetAction.Receive)
                    analytics.logEvent(
                        TransferAnalyticsEvent.TransferClicked(
                            LaunchOrigin.CURRENCY_PAGE,
                            type = TransferAnalyticsEvent.AnalyticsTransferType.SEND
                        )
                    )
                }
            AssetAction.Swap -> AssetActionItem(
                account, getString(R.string.common_swap),
                R.drawable.ic_tx_swap,
                getString(R.string.dashboard_asset_actions_swap_dsc, asset.ticker),
                asset, action
            ) {
                logActionEvent(AssetDetailsAnalytics.SWAP_CLICKED, asset)
                processAction(AssetAction.Swap)
            }
            AssetAction.ViewStatement -> AssetActionItem(
                getString(R.string.dashboard_asset_actions_summary_title),
                R.drawable.ic_tx_interest,
                getString(R.string.dashboard_asset_actions_summary_dsc, asset.ticker),
                asset, action
            ) {
                goToSummary()
            }
            AssetAction.InterestDeposit -> AssetActionItem(
                getString(R.string.common_transfer),
                R.drawable.ic_tx_deposit_arrow,
                getString(R.string.dashboard_asset_actions_deposit_dsc, asset.ticker),
                asset, action
            ) {
                processAction(AssetAction.InterestDeposit)
                analytics.logEvent(
                    InterestAnalytics.InterestDepositClicked(
                        currency = asset.ticker,
                        origin = LaunchOrigin.CURRENCY_PAGE
                    )
                )
            }
            AssetAction.InterestWithdraw -> AssetActionItem(
                getString(R.string.common_withdraw),
                R.drawable.ic_tx_withdraw,
                getString(R.string.dashboard_asset_actions_withdraw_dsc, asset.ticker),
                asset, action
            ) {
                processAction(AssetAction.InterestWithdraw)
                analytics.logEvent(
                    InterestAnalytics.InterestWithdrawalClicked(
                        currency = asset.ticker,
                        origin = LaunchOrigin.CURRENCY_PAGE
                    )
                )
            }
            AssetAction.Sell -> AssetActionItem(
                getString(R.string.common_sell),
                R.drawable.ic_tx_sell,
                getString(R.string.convert_your_crypto_to_cash),
                asset, action
            ) {
                logActionEvent(AssetDetailsAnalytics.SELL_CLICKED, asset)
                processAction(AssetAction.Sell)
            }
            AssetAction.Buy -> AssetActionItem(
                getString(R.string.common_buy),
                R.drawable.ic_tx_buy,
                getString(R.string.dashboard_asset_actions_buy_dsc, asset.ticker),
                asset, action
            ) {
                processAction(AssetAction.Buy)
                dismiss()
            }
            AssetAction.Withdraw -> throw IllegalStateException("Cannot Withdraw a non-fiat currency")
            AssetAction.FiatDeposit -> throw IllegalStateException("Cannot Deposit a non-fiat currency to Fiat")
        }

    private fun logActionEvent(event: AssetDetailsAnalytics, asset: AssetInfo) {
        analytics.logEvent(assetActionEvent(event, asset.ticker))
    }

    private fun goToSummary() {
        checkForKycStatus {
            processAction(AssetAction.ViewStatement)
        }
    }

    private fun checkForKycStatus(action: () -> Unit) {
        disposables += kycTierService.tiers().subscribeBy(
            onSuccess = { tiers ->
                if (tiers.isApprovedFor(KycTierLevel.GOLD)) {
                    action()
                } else {
                    model.process(ShowInterestDashboard)
                    dismiss()
                }
            },
            onError = {
                Timber.e("Error getting tiers in asset actions sheet $it")
            }
        )
    }

    private fun processAction(action: AssetAction) {
        model.process(HandleActionIntent(action))
        dispose()
    }

    companion object {
        fun newInstance(): AssetActionsSheet = AssetActionsSheet()
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): DialogAssetActionsSheetBinding =
        DialogAssetActionsSheetBinding.inflate(inflater, container, false)
}

private class AssetActionAdapter(
    val disposable: CompositeDisposable,
    val statusDecorator: StatusDecorator
) : RecyclerView.Adapter<AssetActionAdapter.ActionItemViewHolder>() {
    var itemList: List<AssetActionItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val compositeDisposable = CompositeDisposable()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionItemViewHolder =
        ActionItemViewHolder(
            compositeDisposable, ItemAssetActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun getItemCount(): Int = itemList.size

    override fun onBindViewHolder(holder: ActionItemViewHolder, position: Int) =
        holder.bind(itemList[position], statusDecorator)

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        compositeDisposable.clear()
    }

    private class ActionItemViewHolder(
        private val compositeDisposable: CompositeDisposable,
        private val binding: ItemAssetActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: AssetActionItem,
            statusDecorator: StatusDecorator
        ) {
            addDecorator(item, statusDecorator)

            binding.apply {
                itemActionIcon.setImageResource(item.icon)
                itemActionIcon.setAssetIconColoursWithTint(item.asset)
                itemActionTitle.text = item.title
                itemActionLabel.text = item.description
            }
        }

        private fun addDecorator(
            item: AssetActionItem,
            statusDecorator: StatusDecorator
        ) {
            item.account?.let { account ->
                compositeDisposable += statusDecorator(account).isEnabled()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                        onSuccess = { enabled ->
                            with(binding.itemActionHolder) {
                                if (enabled) {
                                    alpha = 1f
                                    setOnClickListener {
                                        item.actionCta()
                                    }
                                } else {
                                    alpha = .6f
                                    setOnClickListener {}
                                }
                            }
                        },
                        onError = {
                            Timber.e("Error getting decorator info $it")
                        }
                    )

                binding.itemActionHolder.removePossibleBottomView()
                compositeDisposable += statusDecorator(account).view(context)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        binding.itemActionHolder.addViewToBottomWithConstraints(
                            it,
                            bottomOfView = binding.itemActionLabel,
                            startOfView = binding.itemActionIcon,
                            endOfView = null
                        )
                    }
            } ?: binding.itemActionHolder.setOnClickListener {
                item.actionCta()
            }
        }
    }
}

private data class AssetActionItem(
    val account: BlockchainAccount?,
    val title: String,
    val icon: Int,
    val description: String,
    val asset: AssetInfo,
    val action: AssetAction,
    val actionCta: () -> Unit
) {
    constructor(
        title: String,
        icon: Int,
        description: String,
        asset: AssetInfo,
        action: AssetAction,
        actionCta: () -> Unit
    ) : this(
        null,
        title,
        icon,
        description,
        asset,
        action,
        actionCta
    )
}