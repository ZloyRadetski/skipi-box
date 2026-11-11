// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.PasswordAuthentication
import java.net.URI
import java.net.URL
import utils.encodeBase64

// What the subscription and resource downloads of Android and Desktop both do over java.net, so that the
// two transports differ only in how they open a connection.

/** This URL with a Unicode host name converted to its ASCII (punycode) form; any other URL is returned unchanged. */
fun String.toIdnUrl(): String {
    val host = URL(this).host
    val asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
    return if (host == asciiHost) this else replace(host, asciiHost)
}

/** The Basic Authorization header for the credentials written into this URL, or null when it has none. */
fun URI.toBasicAuthHeaderOrNull(): String? {
    val info = rawUserInfo ?: return null
    val parts = info.split(":", limit = 2)
    val user = parts.getOrElse(0) { "" }
    val password = parts.getOrElse(1) { "" }
    return "Basic " + "$user:$password".encodeBase64()
}

/** Sends the credentials written into [rawUrl] (`https://user:pass@host/`) as a Basic Authorization header. */
fun HttpURLConnection.setEmbeddedBasicAuth(rawUrl: String) {
    val header = runCatching { URI(rawUrl).toBasicAuthHeaderOrNull() }.getOrNull()
        // A URL that java.net.URI rejects (a space, say) can still be read by java.net.URL.
        ?: runCatching { URL(rawUrl).userInfo?.let { info -> URI("http://$info@host/").toBasicAuthHeaderOrNull() } }.getOrNull()
        ?: return
    setRequestProperty("Authorization", header)
}

/**
 * Reads this stream as UTF-8 text and gives up with the error [exceeded] builds as soon as it holds more than
 * [maxBytes], so a hostile or broken endpoint cannot exhaust memory.
 */
fun InputStream.readUtf8Limited(maxBytes: Int, exceeded: () -> Throwable): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (out.size().toLong() + read > maxBytes) throw exceeded()
        out.write(buffer, 0, read)
    }
    return out.toString(Charsets.UTF_8.name())
}

/**
 * Android's SOCKS implementation does not consistently expose the proxy host to [Authenticator], but the
 * dynamically allocated local port is unique.
 */
fun isSocksProxyAuthenticationRequest(requestingPort: Int, proxyPort: Int): Boolean {
    return requestingPort == proxyPort && proxyPort in 1..65_535
}

private val SocksAuthenticatorLock = Any()

/**
 * Runs [block] with the credentials of a local SOCKS proxy available to java.net. A SOCKS proxy asks for them
 * lazily, at connect time, so configuring the Proxy object alone is not enough. Without a [username] nothing
 * is installed; afterwards the authenticator that was there before is restored.
 */
fun <T> withSocksProxyAuthenticator(port: Int, username: String, password: String, block: () -> T): T {
    if (username.isBlank()) return block()
    synchronized(SocksAuthenticatorLock) {
        val previous = defaultAuthenticatorOrNull()
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (!isSocksProxyAuthenticationRequest(requestingPort, port)) return null
                return PasswordAuthentication(username, password.toCharArray())
            }
        })
        return try {
            block()
        } finally {
            Authenticator.setDefault(previous)
        }
    }
}

/** The authenticator that is installed now, so it can be put back; null where the platform cannot be asked (Android). */
internal expect fun defaultAuthenticatorOrNull(): Authenticator?

/** A SOCKS proxy that java.net connections are sent through, with the credentials it asks for, if any. */
data class SocksProxyEndpoint(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    fun toJavaProxy(): Proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress(host.removePrefix("[").removeSuffix("]"), port),
    )
}

/** Runs [block] with this proxy's credentials available to java.net; a null proxy needs none. */
fun <T> SocksProxyEndpoint?.withAuthenticator(block: () -> T): T =
    if (this == null) block() else withSocksProxyAuthenticator(port, username, password, block)
