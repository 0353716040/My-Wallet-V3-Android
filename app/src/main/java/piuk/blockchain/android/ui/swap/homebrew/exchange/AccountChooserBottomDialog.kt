package piuk.blockchain.android.ui.swap.homebrew.exchange

import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.accounts.AsyncAllAccountList
import com.blockchain.koin.scopedInject
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import info.blockchain.balance.AccountReference
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.util.setCoinIcon
import timber.log.Timber

private const val ArgumentTitle = "Title"
private const val ResultId = "ResultId"

class AccountChooserBottomDialog : BottomSheetDialogFragment() {

    interface Callback {
        fun onAccountSelected(requestCode: Int, accountReference: AccountReference)
    }

    private var callback: Callback =
        NullCallBack

    private val compositeDisposable = CompositeDisposable()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? Callback
            ?: NullCallBack
    }

    override fun onDetach() {
        callback = NullCallBack
        super.onDetach()
    }

    private val allAccountList: AsyncAllAccountList by scopedInject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.account_chooser_bottom_dialog, container, false)

    override fun onResume() {
        super.onResume()
        view?.let { view ->
            view.findViewById<TextView>(R.id.dialog_title).text = getTitle()
            compositeDisposable += allAccountList.allAccounts()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onError = {
                    Timber.e(it)
                    dismiss()
                }) { references ->
                    view.findViewById<RecyclerView>(R.id.recyclerView)
                        .also {
                            it.adapter = AccountReferenceAdapter(
                                references,
                                callback andDismiss this,
                                getResultId()
                            )
                            it.layoutManager = LinearLayoutManager(context)
                        }
                }
        }
    }

    override fun onPause() {
        compositeDisposable.dispose()
        super.onPause()
    }

    private fun getTitle() =
        arguments?.getString(ArgumentTitle) ?: ""

    private fun getResultId() =
        arguments?.getInt(ResultId) ?: -1

    companion object {

        @JvmStatic
        fun create(title: String, resultId: Int): BottomSheetDialogFragment {
            return AccountChooserBottomDialog()
                .apply {
                    arguments = Bundle().apply {
                        putString(ArgumentTitle, title)
                        putInt(ResultId, resultId)
                    }
                }
        }
    }
}

private object NullCallBack : AccountChooserBottomDialog.Callback {

    override fun onAccountSelected(requestCode: Int, accountReference: AccountReference) {
        Timber.d("Callback not set")
    }
}

private infix fun AccountChooserBottomDialog.Callback.andDismiss(
    dialog: AccountChooserBottomDialog
): AccountChooserBottomDialog.Callback =
    object : AccountChooserBottomDialog.Callback {
        override fun onAccountSelected(requestCode: Int, accountReference: AccountReference) {
            this@andDismiss.onAccountSelected(requestCode, accountReference)
            dialog.dismiss()
        }
    }

private class AccountReferenceAdapter(
    private val accountReferences: List<AccountReference>,
    private val callback: AccountChooserBottomDialog.Callback,
    private val resultId: Int
) :
    RecyclerView.Adapter<AccountReferenceAdapter.AccountReferenceViewHolder>() {

    class AccountReferenceViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AccountReferenceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.account_chooser_card, parent, false)
        return AccountReferenceViewHolder(
            view
        )
    }

    override fun onBindViewHolder(holder: AccountReferenceViewHolder, position: Int) {
        val accountReference = accountReferences[position]
        val textView = holder.view.findViewById(R.id.account_name) as TextView
        val imageView = holder.view.findViewById(R.id.account_symbol) as ImageView
        textView.text = accountReference.label
        imageView.setCoinIcon(accountReference.cryptoCurrency)
        holder.view.setOnClickListener {
            callback.onAccountSelected(resultId, accountReference)
        }
    }

    override fun getItemCount() = accountReferences.size
}
