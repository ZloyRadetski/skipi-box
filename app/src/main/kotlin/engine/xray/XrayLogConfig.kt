// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

internal fun XrayConfigRequest.buildXrayLogConfig() = buildXrayLogConfig(
    XrayLogConfigRequest(
        coreLogLevel = appState.coreLogLevel,
        enableAccessLog = appState.enableAccessLog,
        accessLogPath = coreLogPaths.accessLogPath,
        errorLogPath = coreLogPaths.errorLogPath,
    ),
)
