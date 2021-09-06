package piuk.blockchain.android.ui.transfer.receive.plugin

import android.content.Context
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import piuk.blockchain.android.urllinks.URL_XLM_MEMO
import piuk.blockchain.android.R
import com.blockchain.coincore.CryptoAddress
import piuk.blockchain.android.databinding.ViewReceiveMemoBinding
import piuk.blockchain.android.util.StringUtils

class ReceiveMemoView @JvmOverloads constructor(
    ctx: Context,
    attr: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(ctx, attr, defStyle) {

    private val binding: ViewReceiveMemoBinding =
        ViewReceiveMemoBinding.inflate(LayoutInflater.from(context), this, true)

    fun updateAddress(address: CryptoAddress) {
        require(address.memo != null)

        with(binding) {
            val assetName = address.asset.ticker
            memoText.text = address.memo
            memoLabel.text = resources.getString(R.string.receive_memo_title, assetName)
            memoWarn.text = resources.getString(R.string.receive_memo_warning, assetName)

            val linkMap = mapOf<String, Uri>("learn_more_link" to Uri.parse(URL_XLM_MEMO))
            memoLink.apply {
                movementMethod = LinkMovementMethod.getInstance()
                text = StringUtils.getStringWithMappedAnnotations(context, R.string.common_linked_learn_more, linkMap)
            }
        }
    }
}
