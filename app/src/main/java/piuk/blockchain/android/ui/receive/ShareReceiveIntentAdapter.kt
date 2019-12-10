package piuk.blockchain.android.ui.receive

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import kotlinx.android.synthetic.main.receive_share_row.view.*

import piuk.blockchain.android.R

internal class ShareReceiveIntentAdapter(private val paymentCodeData: List<SendPaymentCodeData>) :
    RecyclerView.Adapter<ShareReceiveIntentAdapter.ViewHolder>() {

    internal var itemClickedListener: () -> Unit = {}
    private var context: Context? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val inflater = LayoutInflater.from(context)
        val row = inflater.inflate(R.layout.receive_share_row, parent, false)

        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = paymentCodeData[position]

        with(holder) {
            itemView.share_app_title.text = data.title
            itemView.share_app_image.setImageDrawable(data.logo)

            rootView.setOnClickListener {
                itemClickedListener()
                context?.startActivity(data.intent)
            }
        }
    }

    override fun getItemCount() = paymentCodeData.size

    internal class ViewHolder(var rootView: View) : RecyclerView.ViewHolder(rootView)
}
