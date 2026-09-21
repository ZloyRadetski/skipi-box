// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class XrayLogPlanningTest {
    @Test
    fun mapsCoreLevelsAndLeavesAccessLoggingDisabledByDefault() {
        val config = buildXrayLogConfig(
            XrayLogConfigRequest(
                coreLogLevel = 0,
                enableAccessLog = false,
                accessLogPath = "access.log",
                errorLogPath = "error.log",
            ),
        )

        assertEquals("debug", config.getValue("loglevel").jsonPrimitive.content)
        assertEquals("true", config.getValue("dnsLog").jsonPrimitive.content)
        assertEquals(XrayLogDisabled, config.getValue("access").jsonPrimitive.content)
        assertEquals("error.log", config.getValue("error").jsonPrimitive.content)
        assertEquals(XrayLogDisabled, xrayLogLevel(4))
        assertEquals("warning", xrayLogLevel(99))
    }

    @Test
    fun enablesOnlyNonBlankAccessLogPaths() {
        val config = buildXrayLogConfig(
            XrayLogConfigRequest(
                coreLogLevel = 2,
                enableAccessLog = true,
                accessLogPath = "",
                errorLogPath = "error.log",
            ),
        )

        assertEquals(XrayLogDisabled, config.getValue("access").jsonPrimitive.content)
        assertEquals("false", config.getValue("dnsLog").jsonPrimitive.content)
    }
}
