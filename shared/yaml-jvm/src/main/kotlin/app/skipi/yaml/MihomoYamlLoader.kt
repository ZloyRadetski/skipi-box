// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.yaml

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/**
 * Parses YAML into SnakeYAML Engine's plain object tree using the same default settings as the
 * Android importer. Leading BOM characters are removed before parsing, and parse failures are
 * propagated to the caller so each host can preserve its existing error behavior.
 */
fun loadMihomoYamlDocument(text: String): Any? =
    Load(LoadSettings.builder().build()).loadFromString(text.trimStart('\uFEFF'))
