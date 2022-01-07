package com.blockchain.network.websocket

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.reactivex.rxjava3.disposables.Disposable
import org.amshove.kluent.`should be equal to`
import org.junit.Test

class AfterOpenWebSocketTest {

    @Test
    fun `method runs after successful connect`() {
        var counter = 0
        val mockConnection = MockConnection()
        val webSocket = (mock<WebSocket<String, String>>() + mockConnection)
            .afterOpen {
                counter++
                mock()
            }
        counter `should be equal to` 0
        webSocket.open()
        mockConnection.simulateSuccess()
        counter `should be equal to` 1
    }

    @Test
    fun `method doesn't run after unsuccessful connections`() {
        var counter = 0
        val mockConnection = MockConnection()
        val webSocket = (mock<WebSocket<String, String>>() + mockConnection)
            .afterOpen {
                counter++
                mock()
            }
        counter `should be equal to` 0
        webSocket.open()
        mockConnection.simulateFailure()
        mockConnection.simulateDisconnect()
        counter `should be equal to` 0
    }

    @Test
    fun `close closes other`() {
        val mockConnection = MockConnection()
        val webSocket = (mock<WebSocket<String, String>>() + mockConnection)
            .afterOpen {
                mock()
            }
        webSocket.close()
        verify(mockConnection.mock).close()
    }

    @Test
    fun `after close the method is not called on successful connection`() {
        var counter = 0
        val mockConnection = MockConnection()
        val webSocket = (mock<WebSocket<String, String>>() + mockConnection)
            .afterOpen {
                counter++
                mock()
            }
        counter `should be equal to` 0
        webSocket.open()
        webSocket.close()
        mockConnection.simulateSuccess()
        counter `should be equal to` 0
    }

    @Test
    fun `after close and open, the method is called on successful connection`() {
        var counter = 0
        val mockConnection = MockConnection()
        val webSocket = (mock<WebSocket<String, String>>() + mockConnection)
            .afterOpen {
                counter++
                mock()
            }
        counter `should be equal to` 0
        webSocket.open()
        webSocket.close()
        webSocket.open()
        mockConnection.simulateSuccess()
        counter `should be equal to` 1
    }

    @Test
    fun `after close the result of the after open is disposed`() {
        val disposable = mock<Disposable>()
        val mockConnection = MockConnection()
        val webSocket = (mock<WebSocket<String, String>>() + mockConnection)
            .afterOpen {
                disposable
            }
        webSocket.open()
        mockConnection.simulateSuccess()
        verify(disposable, never()).dispose()
        webSocket.close()
        verify(disposable).dispose()
    }
}
