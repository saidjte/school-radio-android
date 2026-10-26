package com.saidjte.schoolradio.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.concurrent.thread

/**
 * v0.43.0 — اختبارات إمبيريقية للمحمّل الفعلي (StreamResolver.downloadToFile)
 * ضد خادم HTTP خام حقيقي (ServerSocket — بلا أي اعتماد): التفرع المتوازي
 * 206، الهبوط للتدفق الواحد عند خادم يتجاهل Range أو يُسقط شريحة وسط
 * التدفق، والرسائل الصادقة عند الرفض والفراغ.
 *
 * هذه الاختبارات تمارس كود الإنتاج نفسه — لا محاكاة — فتثبت أن التنزيل
 * المجزأ يسلّم الملف كاملاً بايتاً بايتاً وأن كل مفاجأة خادم تنتهي إما
 * بملف كامل أو برسالة صادقة، لا بشيء بينهما.
 */
class StreamResolverDownloadTest {

    /** ملف تجريبي 6MB (فوق عتبة التقسيم 1MB) ببايتات حتمية قابلة للمقارنة */
    private val payload = ByteArray(6_000_000).also { buf ->
        var seed = 0x5EED_1234L
        for (i in buf.indices) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            buf[i] = (seed shr 33).toByte()
        }
    }

    /** رد HTTP خام: رمز + طول معلن + ترويسات إضافية + جسد مكتوب فعلياً */
    private class HttpReply(
        val code: Int,
        val declaredLength: Long,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray? = null
    )

    /**
     * خادم HTTP خام على منفذ حرة — خيط لكل اتصال (التفرع يفتح 6 اتصالات
     * متوازية) — Connection: close بعد كل رد، والجسد الأقصر من الطول المعلن
     * يظهر عند العميل كـEOF مبكر (محاكاة شريحة مقطوعة واقعياً).
     */
    private class RawHttpServer {
        private val serverSocket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        @Volatile private var handler: ((String?) -> HttpReply)? = null

        fun start(handler: (rangeHeader: String?) -> HttpReply) {
            this.handler = handler
            thread(name = "test-http-accept", isDaemon = true) {
                while (!serverSocket.isClosed) {
                    val socket = try { serverSocket.accept() } catch (_: Exception) { return@thread }
                    thread(name = "test-http-conn", isDaemon = true) { serve(socket) }
                }
            }
        }

        private fun serve(socket: Socket) {
            try {
                socket.use { s ->
                    s.soTimeout = 15_000
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    val range = generateSequence { reader.readLine() }
                        .takeWhile { it != null && it.isNotEmpty() }
                        .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                        ?.substringAfter("bytes=")?.trim()
                    val reply = handler?.invoke(range) ?: return
                    val out = s.getOutputStream()
                    out.write("HTTP/1.1 ${reply.code} Test\r\n".toByteArray())
                    out.write("Content-Length: ${reply.declaredLength}\r\n".toByteArray())
                    reply.headers.forEach { (k, v) -> out.write("$k: $v\r\n".toByteArray()) }
                    out.write("Connection: close\r\n\r\n".toByteArray())
                    reply.body?.let { out.write(it) }
                    out.flush()
                } // الإغلاق هنا — الجسد الناقص يصل للعميل كـEOF مبكر
            } catch (_: Exception) { }
        }

        val url: String get() = "http://127.0.0.1:${serverSocket.localPort}/audio.m4a"

        fun stop() = serverSocket.close()
    }

    private fun tempDir(): File = Files.createTempDirectory("dl_test").toFile()

    /** رد 206 قانوني لشريحة Range طالبة */
    private fun rangeReply(range: String): HttpReply {
        val (s, e) = range.split("-").map { it.toInt() }
        return HttpReply(
            code = 206,
            declaredLength = (e - s + 1).toLong(),
            headers = mapOf("Content-Range" to "bytes $s-$e/${payload.size}"),
            body = payload.copyOfRange(s, e + 1)
        )
    }

    private val fullReply get() = HttpReply(200, payload.size.toLong(), body = payload)

    // ================== المسار المتوازي ==================

    @Test fun `parallel segmented download delivers the exact bytes`() {
        val srv = RawHttpServer()
        srv.start { range -> if (range == null) fullReply else rangeReply(range) }
        val dir = tempDir()
        try {
            val out = File(dir, "song.m4a")
            val sizes = mutableListOf<Pair<Long, Long>>()
            val result = runBlocking {
                StreamResolver.downloadToFile(srv.url, out) { d, t -> sizes += d to t }
            }
            assertEquals(payload.size.toLong(), result)
            assertEquals(payload.size.toLong(), out.length())
            assertArrayEquals(payload, out.readBytes())
            // التقدم الحي وصل حتى النهاية بالمجموع الكامل
            assertEquals(payload.size.toLong(), sizes.last().first)
            assertEquals(payload.size.toLong(), sizes.last().second)
            // لا ملفات وسيطة متروكة
            assertTrue(dir.listFiles()!!.all { !it.name.contains(".part") })
        } finally {
            srv.stop(); dir.deleteRecursively()
        }
    }

    // ================== الهبوط للتدفق الواحد ==================

    @Test fun `server ignoring range header falls back to single stream`() {
        // يتجاهل Range كلياً — يعيد 200 والملف كاملاً لكل طلب
        val srv = RawHttpServer()
        srv.start { _ -> fullReply }
        val dir = tempDir()
        try {
            val out = File(dir, "song.m4a")
            val result = runBlocking { StreamResolver.downloadToFile(srv.url, out) }
            assertEquals(payload.size.toLong(), result)
            assertArrayEquals(payload, out.readBytes())
        } finally {
            srv.stop(); dir.deleteRecursively()
        }
    }

    @Test fun `dropped segment mid-stream falls back and still completes the file`() {
        // دعم Range حقيقي، لكن الشريحة الثانية تُقتل بعد نصف بايتاتها —
        // كشف القراءة القصيرة يبطل التفرع كله ويهبط للتدفق الواحد فيُسلّم ملفاً كاملاً
        val killedStart = (payload.size / 6) * 2 // بداية الشريحة رقم 2
        val srv = RawHttpServer()
        srv.start { range ->
            when {
                range == null -> fullReply
                range.startsWith("$killedStart-") -> {
                    val (s, e) = range.split("-").map { it.toInt() }
                    val half = (e - s + 1) / 2
                    HttpReply(
                        code = 206,
                        declaredLength = (e - s + 1).toLong(), // الطول معلن كاملاً لكن...
                        headers = mapOf("Content-Range" to "bytes $s-$e/${payload.size}"),
                        body = payload.copyOfRange(s, s + half) // ...النصف فقط يُكتب ثم الإغلاق
                    )
                }
                else -> rangeReply(range)
            }
        }
        val dir = tempDir()
        try {
            val out = File(dir, "song.m4a")
            val result = runBlocking { StreamResolver.downloadToFile(srv.url, out) }
            assertEquals(payload.size.toLong(), result)
            assertArrayEquals(payload, out.readBytes())
        } finally {
            srv.stop(); dir.deleteRecursively()
        }
    }

    // ================== الرفض الصادق ==================

    @Test fun `server 403 surfaces the honest expiry message and leaves no file`() {
        val srv = RawHttpServer()
        srv.start { _ -> HttpReply(403, 0) }
        val dir = tempDir()
        try {
            val out = File(dir, "song.m4a")
            val e = assertThrows(RemoteImportException::class.java) {
                runBlocking { StreamResolver.downloadToFile(srv.url, out) }
            }
            assertTrue(e.message!!.contains("403"))
            assertTrue(e.message!!.contains("انتهت صلاحية الرابط"))
            assertFalse(out.exists())
            assertTrue(dir.listFiles()!!.all { !it.name.contains(".part") })
        } finally {
            srv.stop(); dir.deleteRecursively()
        }
    }

    @Test fun `empty body download surfaces the honest empty message`() {
        val srv = RawHttpServer()
        srv.start { _ -> HttpReply(200, 0) }
        val dir = tempDir()
        try {
            val out = File(dir, "song.m4a")
            val e = assertThrows(RemoteImportException::class.java) {
                runBlocking { StreamResolver.downloadToFile(srv.url, out) }
            }
            assertTrue(e.message!!.contains("فارغاً"))
        } finally {
            srv.stop(); dir.deleteRecursively()
        }
    }
}
