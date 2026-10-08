package com.example.kpkn.data.settings

import com.example.kpkn.data.sync.MediaFiles
import com.example.kpkn.data.sync.OwnedStorageTasks
import com.example.kpkn.data.sync.ResumablePrivateDownload
import com.example.kpkn.data.sync.privateStorageObjectPath
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ResumablePrivateDownloadTest {
    @Test fun sdkLeadingSlashNamesTheSamePrivateObjectAndCannotChangeItsOwner() {
        val hash = "a".repeat(64)
        val path = "accounts/synthetic-owner/1/media/$hash"
        assertEquals(path, privateStorageObjectPath("synthetic-owner", "/$path", hash))
        assertEquals(path, privateStorageObjectPath("synthetic-owner", path, hash))
        assertThrows(IllegalArgumentException::class.java) { privateStorageObjectPath("other-owner", "/$path", hash) }
        assertThrows(IllegalArgumentException::class.java) { privateStorageObjectPath("synthetic-owner", "//$path", hash) }
    }
    @get:Rule val folder = TemporaryFolder()
    private val data = ByteArray(196_611) { (it % 251).toByte() }
    private fun hash(): String = folder.newFile().also { it.writeBytes(data) }.let(MediaFiles::hash)
    @Before fun prepare() { OwnedStorageTasks.resume() }
    @After fun release() { OwnedStorageTasks.resume() }

    private suspend fun server(handler: (HttpExchange) -> Unit, test: suspend (ResumablePrivateDownload) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/private", handler)
        server.start()
        try {
            test(ResumablePrivateDownload { URL("http://127.0.0.1:${server.address.port}/private").openConnection() as HttpURLConnection })
        } finally { server.stop(0) }
    }
    private fun reply(exchange: HttpExchange, offset: Int, range: Boolean = true, truncate: Boolean = false) {
        if (range) exchange.responseHeaders.add("Content-Range", "bytes $offset-${data.lastIndex}/${data.size}")
        exchange.sendResponseHeaders(if (range) 206 else 200, (data.size - offset).toLong())
        try { exchange.responseBody.use { it.write(data, offset, if (truncate) 1024 else data.size - offset) } }
        catch (expected: IOException) { if (!truncate) throw expected }
        finally { exchange.close() }
    }

    @Test fun resumesAPersistedPrefixUsingAuthenticatedEndpointRangeSemantics() = runBlocking {
        val partial = folder.newFile().also { it.writeBytes(data.copyOfRange(0, 65_536)) }
        val checksum = hash()
        server({ exchange ->
            assertEquals("bytes=65536-", exchange.requestHeaders.getFirst("Range"))
            reply(exchange, 65_536)
        }) { it.download(partial, data.size.toLong(), checksum) }
        assertArrayEquals(data, partial.readBytes())
    }
    @Test fun aServerIgnoringRangeRestartsInsteadOfAppendingTwice() = runBlocking {
        val partial = folder.newFile().also { it.writeBytes(data.copyOfRange(0, 65_536)) }
        val checksum = hash()
        server({ exchange -> reply(exchange, 0, range = false) }) { it.download(partial, data.size.toLong(), checksum) }
        assertArrayEquals(data, partial.readBytes())
    }
    @Test fun mismatchedRangeCannotOverwriteTheExistingPrefix() = runBlocking {
        val prefix = data.copyOfRange(0, 65_536)
        val partial = folder.newFile().also { it.writeBytes(prefix) }
        val checksum = hash()
        server({ exchange -> reply(exchange, 1) }) { client ->
            try { client.download(partial, data.size.toLong(), checksum); fail("Wrong range accepted") }
            catch (_: IOException) { assertArrayEquals(prefix, partial.readBytes()) }
        }
    }
    @Test fun networkInterruptionKeepsDurableBytesAndTheRetryResumesThem() = runBlocking {
        val partial = File(folder.root, "interrupted.part")
        val calls = AtomicInteger()
        val checksum = hash()
        server({ exchange ->
            val first = calls.incrementAndGet() == 1
            val offset = exchange.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            reply(exchange, offset, truncate = first)
        }) { client ->
            try { client.download(partial, data.size.toLong(), checksum); fail("Short response accepted") }
            catch (_: IOException) { assertTrue(partial.length() in 1 until data.size.toLong()) }
            client.download(partial, data.size.toLong(), checksum)
        }
        assertEquals(2, calls.get())
        assertArrayEquals(data, partial.readBytes())
    }
    @Test fun aWrongHashIsNeverKeptAsACompletedDownload() = runBlocking {
        val partial = File(folder.root, "wrong-hash.part")
        server({ exchange -> reply(exchange, 0) }) { client ->
            try { client.download(partial, data.size.toLong(), "0".repeat(64)); fail("Wrong hash accepted") }
            catch (_: IOException) { assertFalse(partial.exists()) }
        }
    }
    @Test fun maintenanceWaitsUntilABlockedStreamHasClosed() = runBlocking {
        val entered = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val connection = object : HttpURLConnection(URL("http://127.0.0.1/private")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected.countDown() }
            override fun getResponseCode() = 200
            override fun getContentLengthLong() = data.size.toLong()
            override fun getInputStream(): InputStream = object : InputStream() {
                override fun read(): Int { entered.countDown(); check(disconnected.await(5, TimeUnit.SECONDS)); throw IOException("canceled") }
                override fun close() { closed.countDown() }
            }
        }
        val checksum = hash()
        val transfer = launch(Dispatchers.IO) {
            try { ResumablePrivateDownload { connection }.download(File(folder.root, "blocked.part"), data.size.toLong(), checksum) }
            catch (_: IOException) { }
        }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        OwnedStorageTasks.pauseAndAwait()
        transfer.join()
        assertEquals(0, closed.count)
    }
}
