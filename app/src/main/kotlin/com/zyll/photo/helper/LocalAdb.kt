package com.zyll.photo.helper

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ADB client for this phone's own adbd. The key pair is made only for this helper and kept in the
 * app's no-backup folder; it never leaves the phone. The pairing can be removed any time in
 * Developer options → Wireless debugging → Paired devices, and uninstalling the helper deletes the key.
 */
class LocalAdb private constructor(context: Context) : AbsAdbConnectionManager() {
    private val dir = File(context.noBackupFilesDir, "adb").apply { mkdirs() }
    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(10, TimeUnit.SECONDS)
        val keyFile = File(dir, "client.pk8")
        val certFile = File(dir, "client.der")
        if (keyFile.exists() && certFile.exists()) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            cert = CertificateFactory.getInstance("X.509").generateCertificate(certFile.inputStream())
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val name = X500Name("CN=Zyll Photo Helper")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 10L * 365 * 86_400_000L), name, kp.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
            key = kp.private
            cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "Zyll Photo Helper"

    data class Result(val output: String, val exitCode: Int)

    /**
     * Runs one command and returns its output and exit code. Only pass commands from [PickerAllowlist].
     *
     * An end marker and `$?` are appended so we get the exit code and know the output is complete.
     * libadb throws "Stream closed." at end of stream instead of returning -1, so that counts as EOF.
     * If the marker is missing we retry (all commands here are idempotent).
     */
    fun exec(command: String): Result {
        repeat(3) {
            val raw = readAll("shell:$command; echo \"$END_MARK\$?\"")
            val idx = raw.lastIndexOf(END_MARK)
            if (idx >= 0) {
                val code = raw.substring(idx + END_MARK.length).trim().toIntOrNull() ?: -1
                return Result(raw.substring(0, idx).trim(), code)
            }
        }
        throw IncompleteOutputException()
    }

    /** Fire and forget, for commands that drop the connection (turning wireless debugging off). */
    fun execNoWait(command: String) {
        runCatching { openStream("shell:$command").close() }
    }

    private fun readAll(destination: String): String {
        val out = ByteArrayOutputStream()
        openStream(destination).use { stream ->
            stream.openInputStream().use { input ->
                val buf = ByteArray(4096)
                while (true) {
                    val n = try {
                        input.read(buf)
                    } catch (e: IOException) {
                        if (e.message == "Stream closed.") -1 else throw e
                    }
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    class IncompleteOutputException : IOException("incomplete command output")

    companion object {
        private const val END_MARK = "__ZYLL_EXIT__="

        @Volatile private var instance: LocalAdb? = null

        fun get(context: Context): LocalAdb =
            instance ?: synchronized(this) { instance ?: LocalAdb(context.applicationContext).also { instance = it } }

        /** Finds the port the "Pair device with pairing code" dialog is listening on, via mDNS. */
        fun discoverPairingPort(context: Context, timeoutMs: Long): Pair<String, Int>? {
            val found = AtomicReference<Pair<String, Int>?>(null)
            val latch = CountDownLatch(1)
            val mdns = AdbMdns(context.applicationContext, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { host: InetAddress?, port: Int ->
                if (host != null && port > 0) {
                    found.set(host.hostAddress!! to port)
                    latch.countDown()
                }
            }
            mdns.start()
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } finally {
                mdns.stop()
            }
            return found.get()
        }
    }
}
