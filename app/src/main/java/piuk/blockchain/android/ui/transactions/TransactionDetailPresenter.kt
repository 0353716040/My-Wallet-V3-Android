package piuk.blockchain.android.ui.transactions

import android.support.annotation.VisibleForTesting
import com.blockchain.data.TransactionHash
import com.blockchain.sunriver.XlmDataManager
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.format
import info.blockchain.balance.formatWithUnit
import info.blockchain.wallet.multiaddress.MultiAddressFactory
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.R
import piuk.blockchain.android.data.datamanagers.TransactionListDataManager
import piuk.blockchain.android.ui.balance.BalanceFragment.Companion.KEY_TRANSACTION_HASH
import piuk.blockchain.android.ui.balance.BalanceFragment.Companion.KEY_TRANSACTION_LIST_POSITION
import piuk.blockchain.android.ui.balance.adapter.formatting
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.transactions.models.Displayable
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import timber.log.Timber
import java.math.BigInteger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.collections.HashMap

class TransactionDetailPresenter constructor(
    private val transactionHelper: TransactionHelper,
    prefs: PersistentPrefs,
    private val payloadDataManager: PayloadDataManager,
    private val stringUtils: StringUtils,
    private val transactionListDataManager: TransactionListDataManager,
    private val exchangeRateDataManager: ExchangeRateDataManager,
    private val ethDataManager: EthDataManager,
    private val bchDataManager: BchDataManager,
    private val environmentSettings: EnvironmentConfig,
    private val xlmDataManager: XlmDataManager
) : BasePresenter<TransactionDetailView>() {

    private val fiatType = prefs.selectedFiatCurrency

    @VisibleForTesting
    lateinit var displayable: Displayable

    // Currently no available notes for bch
    // Only BTC and ETHER currently supported
    var transactionNote: String?
        get() = when (displayable.cryptoCurrency) {
            CryptoCurrency.BTC -> payloadDataManager.getTransactionNotes(displayable.hash)
            CryptoCurrency.PAX -> ethDataManager.getErc20TokenData(CryptoCurrency.PAX).txNotes[displayable.hash]
            CryptoCurrency.ETHER -> ethDataManager.getTransactionNotes(displayable.hash)
            else -> ""
        }
        private set(txHash) {
            val notes: String? = when (displayable.cryptoCurrency) {
                CryptoCurrency.BTC -> payloadDataManager.getTransactionNotes(txHash!!)
                CryptoCurrency.PAX -> ethDataManager.getErc20TokenData(CryptoCurrency.PAX).txNotes[displayable.hash]
                CryptoCurrency.ETHER -> ethDataManager.getTransactionNotes(displayable.hash)
                else -> {
                    view?.hideDescriptionField()
                    ""
                }
            }
            view?.setDescription(notes)
        }

    val transactionHash: TransactionHash
        get() = TransactionHash(displayable.cryptoCurrency, displayable.hash)

    override fun onViewReady() {
        val pageIntent = view?.getPageIntent()
        if (pageIntent != null && pageIntent.hasExtra(KEY_TRANSACTION_LIST_POSITION)) {
            val transactionPosition = pageIntent.getIntExtra(KEY_TRANSACTION_LIST_POSITION, -1)
            if (transactionPosition != -1) {
                displayable = transactionListDataManager.getTransactionList()[transactionPosition]
                updateUiFromTransaction(displayable)
            } else {
                view?.pageFinish()
            }
        } else if (pageIntent != null && pageIntent.hasExtra(KEY_TRANSACTION_HASH)) {
            compositeDisposable +=
                transactionListDataManager.getTxFromHash(pageIntent.getStringExtra(KEY_TRANSACTION_HASH))
                    .doOnSuccess { displayable = it }
                    .subscribe(
                        { this.updateUiFromTransaction(it) },
                        { view?.pageFinish() })
        } else {
            Timber.e("Transaction hash not found")
            view?.pageFinish()
        }
    }

    fun updateTransactionNote(description: String) {
        val completable: Completable = when (displayable.cryptoCurrency) {
            CryptoCurrency.BTC -> payloadDataManager.updateTransactionNotes(
                displayable.hash,
                description
            )
            CryptoCurrency.PAX -> ethDataManager.updateErc20TransactionNotes(displayable.hash,
                description)
            CryptoCurrency.ETHER -> ethDataManager.updateTransactionNotes(
                displayable.hash,
                description
            )
            else -> throw IllegalArgumentException("Only BTC, ETHER and PAX currently supported")
        }

        compositeDisposable +=
            completable.subscribe(
                {
                    view?.showToast(R.string.remote_save_ok, ToastCustom.TYPE_OK)
                    view?.setDescription(description)
                },
                { view?.showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR) }
            )
    }

    private fun updateUiFromTransaction(displayable: Displayable) {
        with(displayable) {
            view?.setTransactionType(direction, displayable.isFeeTransaction)
            view?.updateFeeFieldVisibility(direction != TransactionSummary.Direction.RECEIVED &&
                    !displayable.isFeeTransaction)
            setTransactionColor(this)
            setTransactionValue(cryptoCurrency, total)
            setConfirmationStatus(cryptoCurrency, hash, confirmations.toLong())
            transactionNote = hash
            setDate(timeStamp)
            setTransactionFee(cryptoCurrency, fee)

            when (cryptoCurrency) {
                CryptoCurrency.BTC -> handleBtcToAndFrom(this)
                CryptoCurrency.ETHER -> handleEthToAndFrom(this)
                CryptoCurrency.BCH -> handleBchToAndFrom(this)
                CryptoCurrency.XLM -> handleXlmToAndFrom(this)
                CryptoCurrency.PAX -> handlePaxToAndFrom(this)
                else -> throw IllegalArgumentException("$cryptoCurrency is not currently supported")
            }

            compositeDisposable +=
                getTransactionValueString(fiatType, this)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { value -> view?.setTransactionValueFiat(value) },
                        { view?.showToast(R.string.unexpected_error, ToastCustom.TYPE_ERROR) })

            view?.onDataLoaded()
            view?.setIsDoubleSpend(doubleSpend)
        }
    }

    private fun handleXlmToAndFrom(displayable: Displayable) {
        compositeDisposable +=
            xlmDataManager.defaultAccount()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { account ->
                        var fromAddress = displayable.inputsMap.keys.first()
                        var toAddress = displayable.outputsMap.keys.first()
                        if (fromAddress == account.accountId) {
                            fromAddress = account.label
                        }
                        if (toAddress == account.accountId) {
                            toAddress = account.label
                        }

                        view?.let {
                            it.setFromAddress(listOf(TransactionDetailModel(fromAddress, "", "")))
                            it.setToAddresses(listOf(TransactionDetailModel(toAddress, "", "")))
                        }
                    })
    }

    private fun handleEthToAndFrom(displayable: Displayable) {
        var fromAddress = displayable.inputsMap.keys.first()
        var toAddress = displayable.outputsMap.keys.first()

        val ethAddress = ethDataManager.getEthResponseModel()!!.getAddressResponse()!!.account
        if (fromAddress == ethAddress) {
            fromAddress = stringUtils.getString(R.string.eth_default_account_label)
        }
        if (toAddress == ethAddress) {
            toAddress = stringUtils.getString(R.string.eth_default_account_label)
        }
        view?.let {
            it.setFromAddress(listOf(TransactionDetailModel(fromAddress, "", "")))
            it.setToAddresses(listOf(TransactionDetailModel(toAddress, "", "")))
        }
    }

    private fun handlePaxToAndFrom(displayable: Displayable) {
        var fromAddress = displayable.inputsMap.keys.first()
        var toAddress = displayable.outputsMap.keys.first()

        val ethAddress = ethDataManager.getEthResponseModel()!!.getAddressResponse()!!.account
        if (fromAddress == ethAddress) {
            fromAddress = stringUtils.getString(R.string.pax_default_account_label)
        }
        if (toAddress == ethAddress) {
            toAddress = stringUtils.getString(R.string.pax_default_account_label)
        }
        view?.let {
            it.setFromAddress(listOf(TransactionDetailModel(fromAddress, "", "")))
            it.setToAddresses(listOf(TransactionDetailModel(toAddress, "", "")))
        }
    }

    private fun handleBtcToAndFrom(displayable: Displayable) {
        val (inputs, outputs) = transactionHelper.filterNonChangeAddresses(displayable)
        setToAndFrom(displayable, inputs, outputs)
    }

    private fun handleBchToAndFrom(displayable: Displayable) {
        val (inputs, outputs) = transactionHelper.filterNonChangeAddressesBch(displayable)
        setToAndFrom(displayable, inputs, outputs)
    }

    private fun setToAndFrom(
        displayable: Displayable,
        inputs: HashMap<String, BigInteger?>,
        outputs: HashMap<String, BigInteger?>
    ) {
        // From Addresses
        val fromList = getFromList(displayable.cryptoCurrency, inputs)
        view?.setFromAddress(fromList)
        // To Addresses
        val recipients = getToList(displayable.cryptoCurrency, outputs)
        view?.setToAddresses(recipients)
    }

    private fun getFromList(
        currency: CryptoCurrency,
        inputMap: HashMap<String, BigInteger?>
    ): List<TransactionDetailModel> {
        val inputs = handleTransactionMap(inputMap, currency)
        // No inputs = coinbase transaction
        if (inputs.isEmpty()) {
            val coinbase = TransactionDetailModel(
                stringUtils.getString(R.string.transaction_detail_coinbase),
                "",
                currency.symbol
            )

            inputs.add(coinbase)
        }

        return inputs.toList()
    }

    private fun getToList(
        currency: CryptoCurrency,
        outputMap: HashMap<String, BigInteger?>
    ): List<TransactionDetailModel> = handleTransactionMap(outputMap, currency)

    private fun handleTransactionMap(
        inputMap: HashMap<String, BigInteger?>,
        currency: CryptoCurrency
    ): MutableList<TransactionDetailModel> {
        val inputs = mutableListOf<TransactionDetailModel>()
        for ((key, value) in inputMap) {
            var label: String?
            if (currency == CryptoCurrency.BTC) {
                label = payloadDataManager.addressToLabel(key)
            } else {
                label = bchDataManager.getLabelFromBchAddress(key)
                if (label == null)
                    label = FormatsUtil.toShortCashAddress(
                        environmentSettings.bitcoinCashNetworkParameters,
                        key
                    )
            }

            val transactionDetailModel = buildTransactionDetailModel(label, currency, value, currency.symbol)
            inputs.add(transactionDetailModel)
        }
        return inputs
    }

    private fun buildTransactionDetailModel(
        label: String?,
        currency: CryptoCurrency,
        value: BigInteger?,
        unit: String
    ): TransactionDetailModel = TransactionDetailModel(
        label,
        if (currency == CryptoCurrency.BTC) {
            CryptoValue.bitcoinFromSatoshis(value ?: BigInteger.ZERO).format()
        } else {
            CryptoValue.bitcoinCashFromSatoshis(value ?: BigInteger.ZERO).format()
        },
        unit
    ).apply {
        if (address == MultiAddressFactory.ADDRESS_DECODE_ERROR) {
            address = stringUtils.getString(R.string.tx_decode_error)
            setAddressDecodeError(true)
        }
    }

    private fun setTransactionFee(currency: CryptoCurrency, fee: Observable<BigInteger>) {
        fee.map {
            when (currency) {
                CryptoCurrency.BTC -> CryptoValue.bitcoinFromSatoshis(it)
                CryptoCurrency.ETHER -> CryptoValue.etherFromWei(it)
                CryptoCurrency.BCH -> CryptoValue.bitcoinCashFromSatoshis(it)
                CryptoCurrency.XLM -> CryptoValue.lumensFromStroop(it)
                CryptoCurrency.PAX -> CryptoValue.etherFromWei(it)
                else -> CryptoValue.usdPaxFromMinor(it)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                view?.setFee("")
            }
            .subscribe { view?.setFee(it.formatWithUnit()) }
            .addTo(compositeDisposable)
    }

    private fun setTransactionValue(currency: CryptoCurrency, total: BigInteger) {
        when (currency) {
            CryptoCurrency.ETHER -> CryptoValue.etherFromWei(total)
            CryptoCurrency.BTC -> CryptoValue.bitcoinFromSatoshis(total)
            CryptoCurrency.BCH -> CryptoValue.bitcoinCashFromSatoshis(total)
            CryptoCurrency.XLM -> CryptoValue.lumensFromStroop(total)
            CryptoCurrency.PAX -> CryptoValue.usdPaxFromMinor(total)
            else -> throw IllegalArgumentException("$currency is not currently supported")
        }.run { view?.setTransactionValue(this.formatWithUnit()) }
    }

    @VisibleForTesting
    internal fun setConfirmationStatus(cryptoCurrency: CryptoCurrency, txHash: String, confirmations: Long) {
        if (confirmations >= cryptoCurrency.requiredConfirmations) {
            view?.setStatus(cryptoCurrency, stringUtils.getString(R.string.transaction_detail_confirmed), txHash)
        } else {
            var pending = stringUtils.getString(R.string.transaction_detail_pending)
            pending =
                String.format(Locale.getDefault(), pending, confirmations, cryptoCurrency.requiredConfirmations)
            view?.setStatus(cryptoCurrency, pending, txHash)
        }
    }

    private fun setDate(time: Long) {
        val epochTime = time * 1000

        val date = Date(epochTime)
        val dateFormat = SimpleDateFormat.getDateInstance(DateFormat.LONG)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateText = dateFormat.format(date)
        val timeText = timeFormat.format(date)

        view?.setDate("$dateText @ $timeText")
    }

    @VisibleForTesting
    internal fun setTransactionColor(transaction: Displayable) {
        view?.setTransactionColour(transaction.formatting().directionColour)
    }

    @VisibleForTesting
    internal fun getTransactionValueString(currency: String, transaction: Displayable): Single<String> =
        exchangeRateDataManager.getHistoricPrice(
            CryptoValue(transaction.cryptoCurrency, transaction.total),
            currency,
            transaction.timeStamp
        ).map { getTransactionString(transaction, it) }

    private fun getTransactionString(transaction: Displayable, value: FiatValue): String {
        val stringId = when (transaction.direction) {
            TransactionSummary.Direction.TRANSFERRED -> R.string.transaction_detail_value_at_time_transferred
            TransactionSummary.Direction.SENT -> R.string.transaction_detail_value_at_time_sent
            TransactionSummary.Direction.RECEIVED -> R.string.transaction_detail_value_at_time_received
        }
        return stringUtils.getString(stringId) + value.toStringWithSymbol()
    }
}
