package com.blockchain.nabu.util

import com.blockchain.testutils.date
import java.util.Locale
import org.amshove.kluent.`should be equal to`
import org.junit.Test

class CalendarExtensionsKtTest {

    @Test
    fun `calendar should be formatted as 8601 date`() {
        date(Locale.UK, 2000, 8, 12)
            .toISO8601DateString() `should be equal to` "2000-08-12"
    }

    @Test
    fun `calendar should be formatted as 8601 date with single digit date`() {
        date(Locale.UK, 1337, 12, 1)
            .toISO8601DateString() `should be equal to` "1337-12-01"
    }

    @Test
    fun `calendar should be formatted as simple date - all locales`() {
        val list = mutableListOf<Locale>()
        Locale.getAvailableLocales().forEach {
            if (date(it, 1999, 1, 31).toISO8601DateString() != "1999-01-31") {
                list.add(it)
            }
        }
        list `should be equal to` emptyList()
    }
}
