package piuk.blockchain.android.ui.kyc.search

import io.reactivex.rxjava3.core.Observable
import piuk.blockchain.android.ui.kyc.countryselection.util.CountryDisplayModel

fun Observable<List<CountryDisplayModel>>.filterCountries(
    query: Observable<CharSequence>
): Observable<List<CountryDisplayModel>> =
    ListQueryObservable(
        Observable.just<CharSequence>("").concatWith(query),
        this
    ).matchingItems { q, list ->
        list.asSequence()
            .map { country -> country.searchCode.indexOf(q.toString(), ignoreCase = true) to country }
            .filter { it.first != -1 }
            .sortedBy { it.first }
            .map { it.second }
            .toList()
    }
