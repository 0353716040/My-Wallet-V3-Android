package piuk.blockchain.androidbuysell.services

import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.Single
import io.reactivex.subjects.ReplaySubject
import piuk.blockchain.androidbuysell.models.ExchangeData
import piuk.blockchain.androidcore.utils.helperfunctions.InvalidatableLazy

class BuyConditions {

    private val optionsInitializer = InvalidatableLazy { ReplaySubject.create<WalletOptions>(1) }
    private val settingsInitializer = InvalidatableLazy { ReplaySubject.create<Settings>(1) }
    private val exchangeInitializer = InvalidatableLazy { ReplaySubject.create<ExchangeData>(1) }

    val walletOptionsSource: ReplaySubject<WalletOptions> by optionsInitializer
    val walletSettingsSource: ReplaySubject<Settings> by settingsInitializer
    val exchangeDataSource: ReplaySubject<ExchangeData> by exchangeInitializer

    fun wipe() {
        optionsInitializer.invalidate()
        settingsInitializer.invalidate()
        exchangeInitializer.invalidate()
    }

    fun buySellCountries(): Single<List<String>> =
        walletOptionsSource.firstOrError().map {
            it.partners.coinify.countries
        }
}
