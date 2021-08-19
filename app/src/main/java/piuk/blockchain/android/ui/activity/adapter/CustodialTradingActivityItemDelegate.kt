package piuk.blockchain.android.ui.activity.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.custodialwalletimpl.OrderType
import com.blockchain.preferences.CurrencyPrefs
import info.blockchain.balance.AssetInfo
import com.blockchain.utils.toFormattedDate
import io.reactivex.rxjava3.disposables.CompositeDisposable
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.CustodialTradingActivitySummaryItem
import com.blockchain.data.activity.historicRate.HistoricRateFetcher
import piuk.blockchain.android.databinding.DialogActivitiesTxItemBinding
import piuk.blockchain.android.ui.activity.CryptoActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.getResolvedColor
import piuk.blockchain.android.util.setAssetIconColoursWithTint
import piuk.blockchain.android.util.setTransactionHasFailed
import java.util.Date

class CustodialTradingActivityItemDelegate<in T>(
    private val currencyPrefs: CurrencyPrefs,
    private val historicRateFetcher: HistoricRateFetcher,
    private val onItemClicked: (AssetInfo, String, CryptoActivityType) -> Unit // crypto, txID, type
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is CustodialTradingActivitySummaryItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        CustodialTradingActivityItemViewHolder(
            DialogActivitiesTxItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(
        items: List<T>,
        position: Int,
        holder: RecyclerView.ViewHolder
    ) = (holder as CustodialTradingActivityItemViewHolder).bind(
        items[position] as CustodialTradingActivitySummaryItem,
        currencyPrefs.selectedFiatCurrency,
        historicRateFetcher,
        onItemClicked
    )
}

private class CustodialTradingActivityItemViewHolder(
    private val binding: DialogActivitiesTxItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    private val disposables: CompositeDisposable = CompositeDisposable()

    fun bind(
        tx: CustodialTradingActivitySummaryItem,
        selectedFiatCurrency: String,
        historicRateFetcher: HistoricRateFetcher,
        onAccountClicked: (AssetInfo, String, CryptoActivityType) -> Unit
    ) {
        disposables.clear()
        with(binding) {
            icon.setIcon(tx.status, tx.type)
            when {
                tx.status.isPending().not() -> {
                    icon.setAssetIconColoursWithTint(tx.asset)
                }
                tx.status.hasFailed() -> icon.setTransactionHasFailed()

                else -> {
                    icon.background = null
                    icon.setColorFilter(android.graphics.Color.TRANSPARENT)
                }
            }

            txType.setTxLabel(tx.asset, tx.type)

            statusDate.setTxStatus(tx)
            setTextColours(tx.status)

            assetBalanceFiat.bindAndConvertFiatBalance(tx, disposables, selectedFiatCurrency, historicRateFetcher)

            assetBalanceCrypto.text = tx.value.toStringWithSymbol()

            txRoot.setOnClickListener {
                onAccountClicked(tx.asset, tx.txId, CryptoActivityType.CUSTODIAL_TRADING)
            }
        }
    }

    private fun setTextColours(txStatus: OrderState) {
        with(binding) {
            if (txStatus.isFinished()) {
                txType.setTextColor(context.getResolvedColor(R.color.black))
                statusDate.setTextColor(context.getResolvedColor(R.color.grey_600))
                assetBalanceFiat.setTextColor(context.getResolvedColor(R.color.grey_600))
                assetBalanceCrypto.setTextColor(context.getResolvedColor(R.color.black))
            } else {
                txType.setTextColor(context.getResolvedColor(R.color.grey_400))
                statusDate.setTextColor(context.getResolvedColor(R.color.grey_400))
                assetBalanceFiat.setTextColor(context.getResolvedColor(R.color.grey_400))
                assetBalanceCrypto.setTextColor(context.getResolvedColor(R.color.grey_400))
            }
        }
    }
}

private fun ImageView.setIcon(status: OrderState, type: OrderType) =
    setImageResource(
        when (status) {
            OrderState.FINISHED -> if (type == OrderType.BUY) R.drawable.ic_tx_buy else R.drawable.ic_tx_sell
            OrderState.AWAITING_FUNDS,
            OrderState.PENDING_CONFIRMATION,
            OrderState.PENDING_EXECUTION -> R.drawable.ic_tx_confirming
            OrderState.UNINITIALISED, // should not see these next ones ATM
            OrderState.INITIALISED,
            OrderState.UNKNOWN,
            OrderState.CANCELED,
            OrderState.FAILED -> if (type == OrderType.BUY) R.drawable.ic_tx_buy else R.drawable.ic_tx_sell
        }
    )

private fun TextView.setTxLabel(asset: AssetInfo, type: OrderType) {
    text = context.resources.getString(
        if (type == OrderType.BUY) R.string.tx_title_buy else R.string.tx_title_sell, asset.ticker
    )
}

private fun TextView.setTxStatus(tx: CustodialTradingActivitySummaryItem) {
    text = when (tx.status) {
        OrderState.FINISHED -> Date(tx.timeStampMs).toFormattedDate()
        OrderState.UNINITIALISED -> context.getString(R.string.activity_state_uninitialised)
        OrderState.INITIALISED -> context.getString(R.string.activity_state_initialised)
        OrderState.AWAITING_FUNDS -> context.getString(R.string.activity_state_awaiting_funds)
        OrderState.PENDING_EXECUTION -> context.getString(R.string.activity_state_pending)
        OrderState.PENDING_CONFIRMATION -> context.getString(R.string.activity_state_pending)
        OrderState.UNKNOWN -> context.getString(R.string.activity_state_unknown)
        OrderState.CANCELED -> context.getString(R.string.activity_state_canceled)
        OrderState.FAILED -> context.getString(R.string.activity_state_failed)
    }
}
