package app.skipi.ui.config

import engine.network.isIpOrCidrAddress

/** A rule type and value recognised from what the user typed into the value field. */
data class TrafficConfigRuleIntent(val type: String, val value: String)

/**
 * Recognises the rule type from a prefix or an unmistakable shape in the typed value, so `keyword:google` becomes
 * DOMAIN-KEYWORD with the value `google`. Only unambiguous input is recognised: a bare word or number never changes
 * the type the user picked. Returns null when nothing is recognised.
 */
fun detectTrafficConfigRuleIntent(input: String): TrafficConfigRuleIntent? {
    val text = input.trim()
    if (text.isEmpty()) return null

    val separator = text.indexOf(':')
    if (separator > 0) {
        val rest = text.substring(separator + 1).trim()
        when (text.substring(0, separator).lowercase()) {
            "full", "domain-full" -> return rest.intent("DOMAIN")
            "domain", "suffix", "domain-suffix" -> return rest.intent("DOMAIN-SUFFIX")
            "keyword", "domain-keyword" -> return rest.intent("DOMAIN-KEYWORD")
            "wildcard", "domain-wildcard" -> return rest.intent("DOMAIN-WILDCARD")
            "geoip" -> return rest.intent("GEOIP", rest.uppercase())
            "cidr", "ip", "ip-cidr" -> return rest.intent("IP-CIDR")
            "port", "dst-port" -> return rest.intent("DST-PORT")
            "network" -> return rest.intent("NETWORK", rest.lowercase())
            "rule-set", "ruleset" -> return rest.intent("RULE-SET")
            // The domain-set value keeps its Xray form ("geosite:x" / "ext:file:tag").
            "geosite", "ext" -> return text.intent("DOMAIN-SET")
        }
    }

    return when {
        text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) ->
            TrafficConfigRuleIntent("RULE-SET", text)
        isIpOrCidrAddress(text) -> TrafficConfigRuleIntent("IP-CIDR", text)
        '*' in text && text.none(Char::isWhitespace) -> TrafficConfigRuleIntent("DOMAIN-WILDCARD", text)
        else -> null
    }
}

private fun String.intent(type: String, value: String = this): TrafficConfigRuleIntent? =
    if (isEmpty()) null else TrafficConfigRuleIntent(type, value)
