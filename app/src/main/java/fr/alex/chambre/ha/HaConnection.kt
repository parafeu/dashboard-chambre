package fr.alex.chambre.ha

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

/** `http://host:8123` → `ws://host:8123/api/websocket` (https → wss). */
fun websocketUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    val ws = when {
        trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
        trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
        else -> trimmed
    }
    return "$ws/api/websocket"
}

/**
 * One WebSocket session with Home Assistant. Incoming messages are parsed and delivered on
 * [incoming], which is closed when the socket closes or fails. Message ids start at 1.
 */
class HaConnection(client: OkHttpClient, baseUrl: String) {
    private val channel = Channel<Incoming>(Channel.UNLIMITED)
    val incoming: ReceiveChannel<Incoming> = channel
    private val ids = AtomicInteger(0)

    private val socket: WebSocket = client.newWebSocket(
        Request.Builder().url(websocketUrl(baseUrl)).build(),
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                channel.trySend(HaProtocol.parse(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                channel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                channel.close()
            }
        },
    )

    fun nextId(): Int = ids.incrementAndGet()

    fun send(text: String): Boolean = socket.send(text)

    fun close() {
        socket.cancel()
        channel.close()
    }
}
