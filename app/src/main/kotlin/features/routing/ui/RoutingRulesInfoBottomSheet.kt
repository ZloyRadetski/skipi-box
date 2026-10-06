// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.ui

import androidx.compose.runtime.Composable
import app.skipi.ui.routing.SkipiRoutingRulesInfoBottomSheet

/** Android compatibility facade; the rules and help presentation are shared. */
@Composable
fun RoutingRulesInfoBottomSheet(show: Boolean, onDismissRequest: () -> Unit) =
    SkipiRoutingRulesInfoBottomSheet(show = show, onDismissRequest = onDismissRequest)