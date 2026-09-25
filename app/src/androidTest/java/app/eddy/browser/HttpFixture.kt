package app.eddy.browser

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

/** Loopback-only pages; tests never depend on a public website. */
class HttpFixture(tls: Boolean = false) : AutoCloseable {
    private val socket = if (tls) {
        val store = java.security.KeyStore.getInstance("PKCS12")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("audit-test-only.p12").use {
            store.load(it, "password".toCharArray())
        }
        val keys = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm())
        keys.init(store, "password".toCharArray())
        val context = javax.net.ssl.SSLContext.getInstance("TLS")
        context.init(keys.keyManagers, null, null)
        context.serverSocketFactory.createServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    } else ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool()
    val origin = "${if (tls) "https" else "http"}://127.0.0.1:${socket.localPort}"
    init {
        workers.execute {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                workers.execute {
                    runCatching { client.use {
                        val reader = it.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty().split(' ')
                        val path = request.getOrNull(1).orEmpty()
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                        }
                        if (path.startsWith("/slow") || path == "/bad-range") {
                            val out = it.getOutputStream()
                            if (path == "/slow-auth" && headers["cookie"] != "auth=yes") {
                                out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                return@use
                            }
                            val total = if (path == "/bad-range") 3 else 2 * 1024 * 1024
                            val requested = headers["range"]?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                            val start = if (path == "/bad-range") 0 else requested
                            val partial = requested > 0
                            val range = if (partial) "Content-Range: bytes $start-${total - 1}/$total\r\n" else ""
                            out.write(("HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\n" +
                                "Content-Type: application/octet-stream\r\nContent-Length: ${total - start}\r\n" +
                                "Accept-Ranges: bytes\r\nETag: \"audit\"\r\n${range}Connection: close\r\n\r\n").toByteArray())
                            var sent = start
                            while (sent < total) {
                                val count = minOf(65536, total - sent)
                                out.write(ByteArray(count) { 65 }); out.flush(); sent += count
                                if (path != "/bad-range") Thread.sleep(100)
                            }
                            return@use
                        }
                        val body = when (path.substringBefore('?')) {
                            "/a" -> "<title>A</title><a id='next' href='/b'>Next</a><input id='text'><input id='upload' type='file'><button id='popup' onclick=\"window.open('/b')\">Popup</button>"
                            "/b" -> "<title>B</title><a href='/a'>First</a>"
                            "/post" -> "<title>${request.first()}</title>"
                            "/form" -> "<title>Form</title><form method='post' action='/post'><input name='q' value='hello'><button>Send</button></form>"
                            "/download" -> "audit-download"
                            else -> "<title>Fixture</title>ok"
                        }.toByteArray()
                        val response = when (path) {
                            "/redirect" -> "HTTP/1.1 302 Found\r\nLocation: /b\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            "/download" -> "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Disposition: attachment; filename=audit.txt\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            else -> "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        }
                        runCatching {
                            it.getOutputStream().write(response.toByteArray())
                            if (path != "/redirect") it.getOutputStream().write(body)
                        }
                    } }
                }
            }
        }
    }
    override fun close() { socket.close(); workers.shutdownNow() }
}
