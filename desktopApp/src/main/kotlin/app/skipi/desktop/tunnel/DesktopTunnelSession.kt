// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.skipi.app.proxy.ProxyServerStopOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.TunnelConnectRequest

/**
 * Observable state and serialized operations of the Desktop SKIPI Core tunnel.
 *
 * Every connect, reconnect, stop-for-deletion, backup stop and shutdown goes through
 * [operationInProgress]; [intentVersion] lets a manual action supersede a queued reconnect.
 */
internal class DesktopTunnelSession(
    private val coreController: DesktopCoreController,
    private val systemProxyManager: DesktopSystemProxyManager,
    private val readUseSystemProxy: () -> Boolean,
) {
    private lateinit var tunnel: DesktopTunnelController

    var coreState by mutableStateOf(coreController.state())
    var coreMessage by mutableStateOf("")
    var desiredRunning by mutableStateOf(coreState.isRunning)
    var operationInProgress by mutableStateOf(false)
    var systemProxyRecoveryInProgress by mutableStateOf(systemProxyManager.isSupportedHost())
    var intentVersion by mutableStateOf(0L)
    var closeRequested by mutableStateOf(false)

    var pendingReconnectReason by mutableStateOf<String?>(null)
        private set

    // The reason is user-facing text; the revision is the actual event
    // identity, so two successive edits with identical text still restart.
    var pendingReconnectRevision by mutableStateOf(0L)
        private set

    fun attach(controller: DesktopTunnelController) {
        tunnel = controller
    }

    fun requestReconnect(reason: String) {
        pendingReconnectReason = reason
        pendingReconnectRevision += 1
    }

    /** Drops a queued reconnect: the tunnel was stopped deliberately. */
    private fun cancelPendingReconnect() {
        pendingReconnectReason = null
        pendingReconnectRevision += 1
    }

    private fun stoppedDeliberately() {
        coreState = coreController.state()
        desiredRunning = false
        intentVersion += 1
        cancelPendingReconnect()
    }

    private fun stoppedMessage(purpose: String): String = if (readUseSystemProxy()) {
        "Туннель SKIPI Core и системный прокси остановлены для удаления $purpose."
    } else {
        "Локальный туннель SKIPI Core остановлен для удаления $purpose."
    }

    private suspend fun waitUntilIdle() {
        while (operationInProgress || systemProxyRecoveryInProgress) delay(50)
    }

    /** Releases a system proxy left behind by a previous run. */
    suspend fun recoverSystemProxy() {
        if (!systemProxyManager.isSupportedHost()) {
            systemProxyRecoveryInProgress = false
            return
        }
        systemProxyRecoveryInProgress = true
        withContext(Dispatchers.IO) { systemProxyManager.recover() }
            .onSuccess { recovery ->
                if (recovery.action != DesktopSystemProxyLeaseAction.NothingToRelease) {
                    coreMessage = recovery.message
                }
            }
            .onFailure { error ->
                coreMessage = "Не удалось восстановить системный прокси: ${error.message.orEmpty()}"
            }
        systemProxyRecoveryInProgress = false
    }

    /** Mirrors Core's own state, which can change without a user action (e.g. a crash). */
    suspend fun observeCore() {
        while (true) {
            delay(750)
            val previous = coreState
            val current = coreController.state()
            if (current != previous) {
                coreState = current
                if (previous.isRunning && !current.isRunning) {
                    val snapshot = withContext(Dispatchers.IO) { tunnel.snapshot() }
                    desiredRunning = false
                    val detail = snapshot.failure?.message.orEmpty()
                        .ifBlank { current.lastError }
                    coreMessage = "SKIPI Core остановлен. $detail"
                }
            }
        }
    }

    /** Stops the tunnel so the active subscription server can be deleted. */
    suspend fun stopForActiveServerDeletion() {
        check(!operationInProgress) {
            "Дождитесь завершения операции SKIPI Core перед удалением активного сервера."
        }
        while (systemProxyRecoveryInProgress) {
            delay(50)
        }
        check(!operationInProgress) {
            "Дождитесь завершения операции SKIPI Core перед удалением активного сервера."
        }
        operationInProgress = true
        try {
            val stopResult = withContext(Dispatchers.IO) { tunnel.disconnect() }
            stopResult.getOrThrow()
            stoppedDeliberately()
            coreMessage = stoppedMessage("активного сервера")
        } catch (cancelled: CancellationException) {
            coreState = coreController.state()
            desiredRunning = coreState.isRunning
            throw cancelled
        } catch (error: Throwable) {
            coreState = coreController.state()
            desiredRunning = coreState.isRunning
            coreMessage = "Не удалось остановить SKIPI Core: ${error.message.orEmpty()}"
            throw error
        } finally {
            operationInProgress = false
            reconnectIfIntentDiverged()
        }
    }

    /** Runs the queued reconnect, unless a newer manual action or shutdown superseded it. */
    suspend fun runPendingReconnect(selectedProfileId: String, systemProxyEnabled: () -> Boolean) {
        val reason = pendingReconnectReason ?: return
        if (closeRequested) return
        // A manual action carries a generation.  It can supersede a queued
        // automatic reconnect even after this coroutine has started waiting.
        val operationIntentVersion = intentVersion
        val reconnectRevision = pendingReconnectRevision
        waitUntilIdle()
        if (
            closeRequested ||
            operationIntentVersion != intentVersion ||
            reconnectRevision != pendingReconnectRevision
        ) {
            return
        }
        operationInProgress = true
        try {
            val shouldRun = desiredRunning
            coreMessage = if (shouldRun) "$reason Переподключение SKIPI Core…" else "$reason Отключение SKIPI Core…"
            val stopFailure = withContext(Dispatchers.IO) { tunnel.disconnect() }.exceptionOrNull()
            if (stopFailure != null) {
                coreState = coreController.state()
                if (operationIntentVersion == intentVersion) {
                    desiredRunning = coreState.isRunning
                }
                coreMessage = "Не удалось остановить SKIPI Core: ${stopFailure.message.orEmpty()}"
                return
            }
            coreState = coreController.state()
            if (
                !shouldRun ||
                closeRequested ||
                operationIntentVersion != intentVersion ||
                reconnectRevision != pendingReconnectRevision
            ) {
                if (operationIntentVersion == intentVersion) {
                    desiredRunning = false
                    coreMessage = "$reason Локальный туннель SKIPI Core остановлен."
                }
                return
            }
            withContext(Dispatchers.IO) {
                tunnel.connect(TunnelConnectRequest(selectedProfileId))
            }.onSuccess {
                coreState = coreController.state()
                val systemProxySuffix = if (systemProxyEnabled()) " и системный прокси" else ""
                coreMessage = "$reason SKIPI Core переподключён$systemProxySuffix."
            }.onFailure { error ->
                coreState = coreController.state()
                if (operationIntentVersion == intentVersion) {
                    desiredRunning = coreState.isRunning
                }
                coreMessage = "Не удалось переподключить SKIPI Core: ${error.message.orEmpty()}"
            }
        } finally {
            operationInProgress = false
            if (
                !closeRequested &&
                operationIntentVersion != intentVersion &&
                desiredRunning != coreController.state().isRunning
            ) {
                requestReconnect("Выполняю последнее действие с туннелем.")
            } else if (
                pendingReconnectRevision == reconnectRevision &&
                pendingReconnectReason == reason
            ) {
                pendingReconnectReason = null
            }
        }
    }

    /** Stops the tunnel before exit; Core is kept running if the system proxy cannot be restored. */
    suspend fun shutDown(exitApplication: () -> Unit) {
        // Do not let a queued settings/profile reconnect race shutdown.
        pendingReconnectReason = null
        waitUntilIdle()
        operationInProgress = true
        try {
            coreMessage = "Безопасное завершение SKIPI…"
            withContext(Dispatchers.IO) { tunnel.disconnect() }
                .onSuccess {
                    coreState = coreController.state()
                    exitApplication()
                }
                .onFailure { error ->
                    coreState = coreController.state()
                    coreMessage =
                        "Не удалось вернуть системный прокси; SKIPI Core оставлен запущенным. " +
                            "Повторите закрытие: ${error.message.orEmpty()}"
                    closeRequested = false
                }
        } finally {
            operationInProgress = false
        }
    }

    /** Stops the tunnel ahead of a backup restore. */
    suspend fun stopForBackup(): Result<Unit> {
        if (!coreController.state().isRunning) return Result.success(Unit)
        desiredRunning = false
        intentVersion += 1
        val result = withContext(Dispatchers.IO) { tunnel.disconnect() }
        coreState = coreController.state()
        return result.map { }
    }

    /**
     * Runs a server-deletion [operation] that must stop the tunnel first. [onStopFailed] is told when the
     * stop fails after this call owns the tunnel operation.
     */
    suspend fun withServerDeletionRuntime(
        onStopFailed: (Throwable) -> Unit,
        operation: suspend (
            stopService: suspend () -> ProxyServerStopOutcome,
            applyStoppedState: () -> Unit,
            onStopFailed: suspend (Throwable) -> Unit,
        ) -> Unit,
    ) {
        var ownsTunnelOperation = false
        try {
            operation(
                {
                    while (systemProxyRecoveryInProgress) delay(50)
                    if (closeRequested || operationInProgress) {
                        ProxyServerStopOutcome.Failed(
                            IllegalStateException("Дождитесь завершения операции SKIPI Core."),
                        )
                    } else {
                        operationInProgress = true
                        ownsTunnelOperation = true
                        val stopResult = try {
                            withContext(Dispatchers.IO) { tunnel.disconnect() }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Result.failure(error)
                        }
                        stopResult.fold(
                            onSuccess = {
                                coreState = coreController.state()
                                ProxyServerStopOutcome.Stopped
                            },
                            onFailure = ProxyServerStopOutcome::Failed,
                        )
                    }
                },
                {
                    stoppedDeliberately()
                    coreMessage = stoppedMessage("серверов")
                },
                { error ->
                    coreState = coreController.state()
                    desiredRunning = coreState.isRunning
                    coreMessage = "Не удалось остановить SKIPI Core: ${error.message.orEmpty()}"
                    onStopFailed(error)
                },
            )
        } finally {
            if (ownsTunnelOperation) {
                operationInProgress = false
                reconnectIfIntentDiverged()
            }
        }
    }

    /** A Power click: records the new intent first so it overrides an older automatic reconnect. */
    fun toggle(scope: CoroutineScope, profileId: String, systemProxyEnabled: () -> Boolean) {
        if (closeRequested) return
        // Record intent before looking at the in-flight operation.  This
        // makes a Power click stronger than an older automatic reconnect.
        desiredRunning = !desiredRunning
        intentVersion += 1
        val operationIntentVersion = intentVersion
        pendingReconnectReason = null
        if (operationInProgress) {
            coreMessage = if (desiredRunning) {
                "Подключение будет выполнено после текущей операции SKIPI Core."
            } else {
                "Отключение будет выполнено после текущей операции SKIPI Core."
            }
        } else scope.launch {
            operationInProgress = true
            try {
                if (desiredRunning) {
                    if (coreController.state().isRunning) {
                        coreState = coreController.state()
                        coreMessage = "Туннель SKIPI Core уже подключён."
                    } else {
                        coreMessage = "Подключение SKIPI Core…"
                        DesktopLogger.info("Tunnel", "Connecting to profile $profileId")
                        withContext(Dispatchers.IO) {
                            tunnel.connect(TunnelConnectRequest(profileId))
                        }.onSuccess {
                            coreState = coreController.state()
                            val systemProxySuffix = if (systemProxyEnabled()) {
                                " и системный прокси"
                            } else {
                                ""
                            }
                            coreMessage = "Туннель SKIPI Core$systemProxySuffix запущен."
                            DesktopLogger.info("Tunnel", "Connected to in-process SKIPI Core")
                        }.onFailure { error ->
                            coreState = coreController.state()
                            if (operationIntentVersion == intentVersion) {
                                desiredRunning = coreState.isRunning
                            }
                            coreMessage = "Не удалось запустить SKIPI Core: ${error.message.orEmpty()}"
                            DesktopLogger.error("Tunnel", "Connection failed", error)
                        }
                    }
                } else {
                    coreMessage = "Отключение SKIPI Core…"
                    DesktopLogger.info("Tunnel", "Disconnecting tunnel")
                    withContext(Dispatchers.IO) { tunnel.disconnect() }
                        .onSuccess {
                            coreState = coreController.state()
                            if (operationIntentVersion == intentVersion) {
                                desiredRunning = false
                            }
                            coreMessage = if (systemProxyEnabled()) {
                                "Туннель SKIPI Core и системный прокси остановлены."
                            } else {
                                "Локальный туннель SKIPI Core остановлен."
                            }
                            DesktopLogger.info("Tunnel", "Disconnected successfully")
                        }
                        .onFailure { error ->
                            coreState = coreController.state()
                            if (operationIntentVersion == intentVersion) {
                                desiredRunning = coreState.isRunning
                            }
                            coreMessage = "Не удалось остановить SKIPI Core: ${error.message.orEmpty()}"
                            DesktopLogger.error("Tunnel", "Disconnect failed", error)
                        }
                }
            } finally {
                operationInProgress = false
                if (
                    !closeRequested &&
                    operationIntentVersion != intentVersion &&
                    desiredRunning != coreController.state().isRunning
                ) {
                    requestReconnect("Выполняю последнее действие с туннелем.")
                }
            }
        }
    }

    private fun reconnectIfIntentDiverged() {
        if (!closeRequested && desiredRunning != coreController.state().isRunning) {
            requestReconnect("Выполняю последнее действие с туннелем.")
        }
    }
}
