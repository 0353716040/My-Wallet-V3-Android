package piuk.blockchain.android.ui.buysell.coinify.signup.select_country

import piuk.blockchain.android.util.extensions.addToCompositeDisposable
import piuk.blockchain.androidbuysell.datamanagers.BuyDataManager
import piuk.blockchain.androidcore.utils.extensions.applySchedulers
import piuk.blockchain.androidcoreui.ui.base.BasePresenter
import java.util.*
import javax.inject.Inject

class CoinifySelectCountryPresenter @Inject constructor(
    private val buyDataManager: BuyDataManager
) : BasePresenter<CoinifySelectCountryView>() {

    private var countryCodeMap = mutableMapOf<String, String>()

    override fun onViewReady() {

        setCountryCodeMap()

        buyDataManager.countryCode
                .applySchedulers()
                .addToCompositeDisposable(this)
                .subscribe {
                    autoSelectCountry(it)
                }
    }

    fun setCountryCodeMap() {

        val unsortedMap = mutableMapOf<String, String>()

        for (code in Locale.getISOCountries()) {
            val loc = Locale("en", code)
            val displayName = loc.getDisplayCountry()

            unsortedMap.put(displayName, code)
        }

        countryCodeMap = unsortedMap.toSortedMap()

        view.onSetCountryPickerData(countryCodeMap.keys.toList())
    }

    private fun autoSelectCountry(countryCode: String) {
        val countryName = countryCodeMap
                .filterValues { it.equals(countryCode) }.keys.firstOrNull() ?: ""

        if (countryName.isNotEmpty()) {
            view.onAutoSelectCountry(countryCodeMap.keys.indexOf(countryName))
        }
    }

    fun collectDataAndContinue(countryPosition: Int) {
        val countryName = countryCodeMap.keys.filterIndexed { index, _ -> index == countryPosition }.last()
        val countryCode = countryCodeMap.get(countryName)

        countryCode?.let {

            buyDataManager.isInCoinifyCountry(countryCode)
                    .applySchedulers()
                    .addToCompositeDisposable(this)
                    .subscribe { isAllowed ->
                        if (isAllowed) {
                            view.onStartVerifyEmail(countryCode)
                        } else {
                            view.onStartInvalidCountry()
                        }
                    }
        }
    }

}