// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * How one local inbound (SOCKS or HTTP) is protected. Each inbound has its own mode.
 *
 * - [Auto]: the login a raw Xray JSON carries for that inbound, else the generated one.
 * - [Manual]: the login the user typed, whatever the JSON says.
 * - [FromJson]: only the login a raw Xray JSON carries; without one the inbound is open.
 * - [Off]: no authentication.
 */
enum class LocalProxyAuthMode { Auto, Manual, FromJson, Off }

data class LocalProxyCredentials(val username: String, val password: String) {
    /** A blank username is how the listener configs have always meant "no authentication". */
    val isUsable: Boolean get() = username.isNotBlank()

    companion object {
        val None = LocalProxyCredentials("", "")
    }
}

/** The logins a raw Xray config carries on its own socks and http inbounds. */
data class LocalProxyJsonAuth(
    val socks: LocalProxyCredentials? = null,
    val http: LocalProxyCredentials? = null,
)

object LocalProxyAuth {
    /** The login an inbound ends up with; [LocalProxyCredentials.None] means no authentication. */
    fun resolve(
        mode: LocalProxyAuthMode,
        stored: LocalProxyCredentials,
        fromJson: LocalProxyCredentials?,
    ): LocalProxyCredentials {
        val storedLogin = stored.takeIf { it.isUsable }?.copy(username = stored.username.trim())
        val jsonLogin = fromJson?.takeIf { it.isUsable }
        return when (mode) {
            LocalProxyAuthMode.Off -> null
            LocalProxyAuthMode.Manual -> storedLogin
            LocalProxyAuthMode.FromJson -> jsonLogin
            LocalProxyAuthMode.Auto -> jsonLogin ?: storedLogin
        } ?: LocalProxyCredentials.None
    }

    fun readJsonAuth(configJson: String): LocalProxyJsonAuth {
        val config = runCatching { parseCustomXrayConfigJsonObject(configJson) }.getOrNull()
            ?: return LocalProxyJsonAuth()
        return readJsonAuth(config)
    }

    /** The first inbound of each protocol that carries a usable login wins, like Xray's own first match. */
    fun readJsonAuth(config: JsonObject): LocalProxyJsonAuth {
        val inbounds = (config["inbounds"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return LocalProxyJsonAuth(
            socks = inbounds.firstNotNullOfOrNull { inbound -> inbound.socksLogin() },
            http = inbounds.firstNotNullOfOrNull { inbound -> inbound.httpLogin() },
        )
    }

    private fun JsonObject.socksLogin(): LocalProxyCredentials? {
        if (protocol() != "socks") return null
        val settings = this["settings"] as? JsonObject ?: return null
        if (settings.string("auth") != "password") return null
        return settings.firstAccount("users")
    }

    private fun JsonObject.httpLogin(): LocalProxyCredentials? {
        if (protocol() != "http") return null
        val settings = this["settings"] as? JsonObject ?: return null
        return settings.firstAccount("accounts")
    }

    private fun JsonObject.protocol(): String? = string("protocol")?.lowercase()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.firstAccount(key: String): LocalProxyCredentials? =
        (this[key] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstNotNullOfOrNull { account ->
                val user = account.string("user")?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
                LocalProxyCredentials(user, account.string("pass").orEmpty())
            }
}


/** Reads a stored mode name; an unknown or blank one (an older or newer backup) becomes [default]. */
fun parseLocalProxyAuthMode(name: String?, default: LocalProxyAuthMode = LocalProxyAuthMode.Auto): LocalProxyAuthMode =
    LocalProxyAuthMode.entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: default
