package piuk.blockchain.android.ui.send

import com.blockchain.testutils.lumens
import com.blockchain.testutils.stroops
import com.blockchain.testutils.usd
import com.blockchain.transactions.SendDetails
import info.blockchain.balance.AccountReference
import info.blockchain.balance.CryptoCurrency
import org.amshove.kluent.`should equal`
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.ui.transfer.send.activity.SendConfirmationDetails
import java.util.Locale

class SendConfirmationDetailsMapperTest {

    @Before
    fun setupLocal() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `can map to PaymentConfirmationDetails`() {
        SendConfirmationDetails(
            SendDetails(
                from = AccountReference.Xlm("My account", ""),
                toAddress = "Some Address",
                value = 100.1.lumens(),
                fee = 1.stroops()
            ),
            fees = 99.stroops(),
            fiatAmount = 1234.45.usd(),
            fiatFees = 0.20.usd()
        )
            .toPaymentConfirmationDetails()
            .apply {
                this.crypto `should equal` CryptoCurrency.XLM
                this.cryptoAmount `should equal` "100.1"
                this.cryptoFee `should equal` "0.0000099"
                this.cryptoTotal `should equal` "100.1000099"

                this.fiatUnit `should equal` "USD"
                this.fiatSymbol `should equal` "$"
                this.fiatAmount `should equal` "1,234.45"
                this.fiatFee `should equal` "0.20"
                this.fiatTotal `should equal` "1,234.65"

                this.fromLabel `should equal` "My account"
                this.toLabel `should equal` "Some Address"
            }
    }
}
