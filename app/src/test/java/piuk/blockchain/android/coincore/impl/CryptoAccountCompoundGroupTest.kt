package piuk.blockchain.android.coincore.impl

import com.blockchain.android.testutils.rxInit
import com.blockchain.testutils.bitcoin
import com.nhaarman.mockito_kotlin.mock
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import org.amshove.kluent.itReturns
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.CryptoSingleAccount
import kotlin.test.assertEquals

class CryptoAccountCompoundGroupTest {

    @get:Rule
    val rx = rxInit {
        mainTrampoline()
        ioTrampoline()
        computationTrampoline()
    }

    @Test
    fun `group with single account returns single account balance`() {
        // Arrange
        val account: CryptoSingleAccount = mock {
            on { balance } itReturns Single.just(100.bitcoin())
        }

        val subject = CryptoAccountCompoundGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account)
        )

        // Act
        subject.balance.test()
            .assertComplete()
            .assertValue(100.bitcoin())
    }

    @Test
    fun `group with two accounts returns the sum of the account balance`() {
        // Arrange
        val account1: CryptoSingleAccount = mock {
            on { balance } itReturns Single.just(100.bitcoin())
        }

        val account2: CryptoSingleAccount = mock {
            on { balance } itReturns Single.just(150.bitcoin())
        }

        val subject = CryptoAccountCompoundGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account1, account2)
        )

        // Act
        subject.balance.test()
            .assertComplete()
            .assertValue(250.bitcoin())
    }

    @Test
    fun `group with single account returns single account actions`() {
        // Arrange
        val accountActions = setOf(AssetAction.Send, AssetAction.Receive)

        val account: CryptoSingleAccount = mock {
            on { actions } itReturns accountActions
        }

        val subject = CryptoAccountCompoundGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account)
        )

        // Act
        val r = subject.actions

        // Assert
        assertEquals(r, accountActions)
    }

    @Test
    fun `group with three accounts returns the intersection of possible actions`() {
        // Arrange
        val accountActions1 = setOf(
            AssetAction.Send,
            AssetAction.Receive
        )

        val accountActions2 = setOf(
            AssetAction.Send,
            AssetAction.Swap
        )

        val accountActions3 = setOf(
            AssetAction.Send,
            AssetAction.Receive
        )

        val expectedResult = setOf(AssetAction.Send)

        val account1: CryptoSingleAccount = mock {
            on { actions } itReturns accountActions1
        }

        val account2: CryptoSingleAccount = mock {
            on { actions } itReturns accountActions2
        }

        val account3: CryptoSingleAccount = mock {
            on { actions } itReturns accountActions3
        }

        val subject = CryptoAccountCompoundGroup(
            asset = CryptoCurrency.BTC,
            label = "group label",
            accounts = listOf(account1, account2, account3)
        )

        // Act
        val r = subject.actions

        // Assert
        assertEquals(r, expectedResult)
    }
}