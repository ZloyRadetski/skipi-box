// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

/** A profile must receive its own bundled snapshot when its selected provider changes. */
fun requiresBundledResourceSourceActivation(
    isProfileScoped: Boolean,
    storedSource: Int?,
    requestedSource: Int,
): Boolean = isProfileScoped && storedSource != requestedSource

fun shouldRestoreBundledResourceFile(
    kind: ResourceFileKind,
    resourceFileSource: Int,
    targetExists: Boolean,
    targetLength: Long,
    targetLastModifiedMillis: Long,
    bundledUpdatedAtMillis: Long,
    restoreAfterPackageUpdate: Boolean,
): Boolean {
    if (!targetExists || targetLength <= 0) return true
    if (!restoreAfterPackageUpdate) return false
    if (kind != ResourceFileKind.XrayCore && resourceFileSource == ResourceFileSourceCustom) {
        return false
    }
    return bundledUpdatedAtMillis > 0 && targetLastModifiedMillis < bundledUpdatedAtMillis
}
