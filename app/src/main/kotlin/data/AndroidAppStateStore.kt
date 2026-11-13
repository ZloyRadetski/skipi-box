// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.skipi.app.store.AppStateStore
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.room.RoomDatabase
import app.AppState
import app.skipi.app.store.SharedApplicationStore
import data.repository.AndroidAppRepositories
import data.repository.AndroidProxyServerRepository
import features.proxy.server.usecase.AndroidRuntimeStateRepository
import features.proxy.server.usecase.createAndroidRuntimeStateRepository
import features.logs.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import utils.runCatchingCancellable

class AndroidAppStateStore private constructor(
    context: Context,
) : AppStateStore {
    private val appContext = context.applicationContext
    private var database = buildDatabase()
    private var dao = database.appStateDao()
    private val settingsPreferences = AppSettingsPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateLock = Any()
    private val saveMutex = Mutex()
    private val saveRevision = AtomicLong(0)
    private val hasPersistedState = AtomicBoolean(false)
    private val loadGate = InitialLoadGate()

    // Only the SharedPreferences settings are read here; the servers, groups and rules live in
    // Room and are merged in by [loadRoomState] a moment later.
    private val initialSettings = settingsPreferences.load()
    private val persistenceTracker = AppStatePersistenceTracker(initialSettings)
    private val mutableState = MutableStateFlow(initialSettings)

    init {
        scope.launch { loadRoomState() }
    }

    val context: Context get() = appContext
    override val state: StateFlow<AppState> = mutableState.asStateFlow()

    /** True once the Room-backed lists are part of [state]. */
    val ready: StateFlow<Boolean> get() = loadGate.isReady

    /** Suspends until the saved servers, groups and rules are in [state]. */
    suspend fun awaitReady() = loadGate.awaitReady()

    /** Blocks the calling thread until [ready]; never call it from the main thread. */
    fun awaitReadyBlocking(): Boolean = loadGate.awaitReadyBlocking()

    val proxyServerRepository: AndroidProxyServerRepository by lazy {
        AndroidProxyServerRepository(this, scope, awaitReady = ::awaitReady)
    }
    /** Process-owned runtime contract, available before an Activity exists. */
    internal val runtimeStateRepository: AndroidRuntimeStateRepository by lazy {
        createAndroidRuntimeStateRepository(appState = state, scope = scope)
    }
    /** One set of shared contracts backed by this existing Room and SharedPreferences store. */
    val appRepositories: AndroidAppRepositories by lazy {
        AndroidAppRepositories(
            stateStore = this,
            scope = scope,
            hostRuntime = runtimeStateRepository,
        )
    }
    /** One process-owned shared application store for UI and background actions. */
    val sharedApplicationStore: SharedApplicationStore by lazy {
        SharedApplicationStore(appRepositories.contracts, scope)
    }

    /** Non-reactive one-shot snapshot for decisions that must not trigger recomposition. */
    override val currentState: AppState get() = mutableState.value

    override fun update(transform: (AppState) -> AppState) {
        update(persist = true, transform = transform)
    }

    override fun update(
        persist: Boolean,
        transform: (AppState) -> AppState,
    ) {
        if (persist && !loadGate.isReady.value && !isMainThread()) {
            // A persisted change made before the lists are loaded would act on empty lists.
            loadGate.awaitReadyBlocking()
        }
        val pendingSave = synchronized(updateLock) {
            val previousState = mutableState.value
            val nextState = transform(previousState)
            mutableState.value = nextState
            if (persist) {
                PendingStateSave(
                    nextState = nextState,
                    revision = saveRevision.incrementAndGet(),
                )
            } else {
                null
            }
        } ?: return

        persist(pendingSave.nextState, pendingSave.revision)
    }

    fun flush() {
        val pendingSave = synchronized(updateLock) {
            PendingStateSave(
                nextState = mutableState.value,
                revision = saveRevision.incrementAndGet(),
            )
        }
        persist(pendingSave.nextState, pendingSave.revision)
    }

    /**
     * Removes all persisted SKIPI state and returns to the same state used by
     * a clean installation. The caller must stop a running VPN beforehand.
     */
    suspend fun resetToStockState() {
        loadGate.awaitReady()
        val stockState = AppState()
        synchronized(updateLock) {
            // Invalidates queued asynchronous writes from the pre-reset state.
            saveRevision.incrementAndGet()
            mutableState.value = stockState
        }
        saveMutex.withLock {
            settingsPreferences.clear()
            resetDatabase()
            settingsPreferences.save(stockState)
            dao.saveState(
                previousState = AppState(),
                nextState = stockState,
                replaceAll = true,
            )
            hasPersistedState.set(true)
            persistenceTracker.markPersisted(stockState)
        }
    }

    /** Loads the Room lists on the IO scope and publishes them with one state update. */
    private suspend fun loadRoomState() {
        try {
            val persistedState = runCatchingCancellable {
                dao.loadState()
            }.onFailure { error ->
                AppLog.error(LogTag, "Failed to load app state", error)
                resetDatabase()
            }.getOrNull()
            if (persistedState?.hasRoomContent() == true) {
                val pendingSave = synchronized(updateLock) {
                    val current = mutableState.value
                    // The tracker's "previous" state is what is on disk: Room content plus the
                    // settings read at start. Changes made since are saved after this point.
                    persistenceTracker.markPersisted(persistedState.toAppState(initialSettings))
                    hasPersistedState.set(true)
                    val hadUpdates = saveRevision.get() > 0
                    mutableState.value = persistedState.toAppState(current)
                        .copy(proxyRunning = current.proxyRunning)
                    // Saves queued before the lists existed describe empty lists; replace them.
                    val revision = saveRevision.incrementAndGet()
                    if (hadUpdates) PendingStateSave(mutableState.value, revision) else null
                }
                pendingSave?.let { persist(it.nextState, it.revision) }
            }
        } finally {
            loadGate.markReady()
        }
    }

    private fun persist(nextState: AppState, revision: Long) {
        scope.launch {
            // Saving before the Room lists are loaded would replace the database with empty lists.
            loadGate.awaitReady()
            saveMutex.withLock {
                if (revision != saveRevision.get()) {
                    return@withLock
                }
                val plan = persistenceTracker.plan(
                    nextState = nextState,
                    hasPersistedRoomState = hasPersistedState.get(),
                )
                runCatchingCancellable {
                    settingsPreferences.save(
                        state = plan.nextState,
                        // Unchanged large JSON blobs are not written again; a full replace writes everything.
                        previous = plan.previousState.takeUnless { plan.replaceAll },
                    )
                    dao.saveState(
                        previousState = plan.previousState,
                        nextState = plan.nextState,
                        replaceAll = plan.replaceAll,
                    )
                    hasPersistedState.set(true)
                    persistenceTracker.markPersisted(plan.nextState)
                }.onFailure { error ->
                    AppLog.error(LogTag, "Failed to persist app state", error)
                    resetDatabase()
                    runCatchingCancellable {
                        settingsPreferences.save(nextState)
                        dao.saveState(
                            previousState = AppState(),
                            nextState = nextState,
                            replaceAll = true,
                        )
                        hasPersistedState.set(true)
                        persistenceTracker.markPersisted(nextState)
                    }.onFailure { retryError ->
                        AppLog.error(LogTag, "Failed to persist app state after database reset", retryError)
                    }
                }
            }
        }
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun buildDatabase(): SkipiAppDatabase {
        return Room.databaseBuilder(
            appContext,
            SkipiAppDatabase::class.java,
            SkipiDatabaseName,
        )
            // Keep committed state in the main DB file for file-based backup tools.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .addMigrations(
                Migration1To2,
                Migration2To3,
                Migration3To4,
                Migration4To5,
                Migration5To6,
                Migration6To7,
                Migration7To8,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    private fun resetDatabase() {
        runCatching { database.close() }
            .onFailure { error -> AppLog.warn(LogTag, "Failed to close app state database before reset", error) }
        runCatching { appContext.deleteDatabase(SkipiDatabaseName) }
            .onFailure { error -> AppLog.warn(LogTag, "Failed to delete app state database during reset", error) }
        database = buildDatabase()
        dao = database.appStateDao()
        hasPersistedState.set(false)
    }

    companion object {
        private const val LogTag = "AndroidAppStateStore"

        @Volatile
        private var instance: AndroidAppStateStore? = null

        fun get(context: Context): AndroidAppStateStore {
            return instance ?: synchronized(this) {
                instance ?: AndroidAppStateStore(context).also { store ->
                    instance = store
                }
            }
        }
    }
}

private data class PendingStateSave(
    val nextState: AppState,
    val revision: Long,
)
