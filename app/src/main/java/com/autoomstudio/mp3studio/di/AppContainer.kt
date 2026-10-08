package com.autoomstudio.mp3studio.di

import android.content.Context
import android.util.Log
import com.autoomstudio.mp3studio.BuildConfig
import com.autoomstudio.mp3studio.data.account.AuthRepository
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.account.GoogleSignIn
import com.autoomstudio.mp3studio.data.account.ProfileRepository
import com.autoomstudio.mp3studio.data.account.SecureSessionStore
import com.autoomstudio.mp3studio.data.account.SupabaseAuthBackend
import com.autoomstudio.mp3studio.data.clip.ClipExporter
import com.autoomstudio.mp3studio.data.clip.ClipStore
import com.autoomstudio.mp3studio.data.clip.RingtoneSetter
import com.autoomstudio.mp3studio.data.clip.WaveformExtractor
import com.autoomstudio.mp3studio.data.duplicates.DuplicateRepository
import com.autoomstudio.mp3studio.data.duplicates.FileFingerprinter
import com.autoomstudio.mp3studio.data.library.AudioFolderScanner
import com.autoomstudio.mp3studio.data.library.AudioFolderWatcher
import com.autoomstudio.mp3studio.data.library.LibraryPreferences
import com.autoomstudio.mp3studio.data.library.MediaStoreSongSource
import com.autoomstudio.mp3studio.data.library.SongDeleter
import com.autoomstudio.mp3studio.data.library.SongRepository
import com.autoomstudio.mp3studio.data.plan.DataStoreEntitlementsCache
import com.autoomstudio.mp3studio.data.plan.DeviceId
import com.autoomstudio.mp3studio.data.plan.EntitlementsRepository
import com.autoomstudio.mp3studio.data.plan.SupabaseEntitlementsBackend
import com.autoomstudio.mp3studio.data.playlist.MPlayDatabase
import com.autoomstudio.mp3studio.data.playlist.PlaylistRepository
import com.autoomstudio.mp3studio.playback.PlaybackController
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.data.stems.StemRepository
import com.autoomstudio.mp3studio.data.tempo.SongTempoAnalyzer
import com.autoomstudio.mp3studio.metronome.MetronomeController
import com.autoomstudio.mp3studio.metronome.MusicTimeline
import com.autoomstudio.mp3studio.playback.PlaybackSessionStore
import com.autoomstudio.mp3studio.separation.BundledModelProvider
import com.autoomstudio.mp3studio.separation.SeparationBackend
import com.autoomstudio.mp3studio.separation.SeparationController
import com.autoomstudio.mp3studio.separation.WorkManagerSeparationBackend
import com.autoomstudio.mp3studio.singalong.RecordingStore
import com.autoomstudio.mp3studio.singalong.SingAlongSession
import com.autoomstudio.mp3studio.widget.WidgetStatePublisher
import com.autoomstudio.mp3studio.widget.WidgetStateStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    /** Outlives screens and services, for writes that must finish after their caller is gone. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val songRepository: SongRepository by lazy {
        val scanner = AudioFolderScanner(appContext)
        SongRepository(
            contentResolver = contentResolver,
            source = MediaStoreSongSource(contentResolver),
            scanner = scanner,
            watcher = AudioFolderWatcher(scanner.folders()),
        )
    }

    val playbackSessionStore: PlaybackSessionStore by lazy { PlaybackSessionStore(appContext) }

    val libraryPreferences: LibraryPreferences by lazy { LibraryPreferences(appContext) }

    private val database: MPlayDatabase by lazy { MPlayDatabase.create(appContext) }

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database.playlistDao()) }

    val duplicateRepository: DuplicateRepository by lazy {
        DuplicateRepository(database.duplicateDao(), FileFingerprinter(contentResolver), applicationScope)
    }

    val stemRepository: StemRepository by lazy {
        StemRepository(appContext, database.stemDao(), FileFingerprinter(contentResolver), applicationScope)
    }

    val tempoAnalyzer: SongTempoAnalyzer by lazy {
        SongTempoAnalyzer(appContext, database.tempoDao(), stemRepository)
    }

    val separationBackend: SeparationBackend by lazy { WorkManagerSeparationBackend(appContext, appSettings) }

    val separationController: SeparationController by lazy {
        SeparationController(
            stemRepository,
            separationBackend,
            BundledModelProvider(appContext),
            appSettings,
            applicationScope,
            isSignedIn = { authRepository.isSignedIn },
        )
    }

    val clipStore: ClipStore by lazy { ClipStore(appContext) }

    val clipExporter: ClipExporter by lazy { ClipExporter(appContext) }

    val waveformExtractor: WaveformExtractor by lazy { WaveformExtractor(appContext) }

    val ringtoneSetter: RingtoneSetter by lazy { RingtoneSetter(appContext) }

    val songDeleter: SongDeleter by lazy { SongDeleter(appContext) }

    val widgetStateStore: WidgetStateStore by lazy { WidgetStateStore(appContext) }

    val appSettings: AppSettings by lazy { AppSettings(appContext) }

    private val sessionStore: SecureSessionStore by lazy { SecureSessionStore(appContext) }

    /** Supabase (auth and Postgrest). A build without settings gets a placeholder URL and fails at sign-in. */
    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://not-configured.invalid" },
            supabaseKey = BuildConfig.SUPABASE_KEY.ifBlank { "not-configured" },
        ) {
            httpEngine = OkHttp.create()
            install(Auth) {
                sessionManager = sessionStore
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Functions)
        }
    }

    val entitlementsRepository: EntitlementsRepository by lazy {
        EntitlementsRepository(
            backend = SupabaseEntitlementsBackend(supabase) { DeviceId.of(appContext) },
            cache = DataStoreEntitlementsCache(appContext),
            scope = applicationScope,
            onRefreshFailed = { Log.w("Entitlements", "Could not refresh entitlements", it) },
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(SupabaseAuthBackend(supabase, sessionStore), applicationScope)
    }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(supabase) }

    fun signedInUserId(): String? = (authRepository.state.value as? AuthState.SignedIn)?.user?.id

    val googleSignIn: GoogleSignIn by lazy { GoogleSignIn(BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    /** Whether MPlay's own player is audible right now, set by the playback service. */
    val musicPlaying = MutableStateFlow(false)

    /** For main-thread work that outlives screens, like the MediaController the metronome follows. */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Lets the metronome follow the song; it only watches, so it leaves restoring the queue to the UI's controller. */
    private val musicTimeline: MusicTimeline by lazy {
        PlaybackController(appContext, mainScope, songRepository, playbackSessionStore, restoresSession = false)
    }

    val metronomeController: MetronomeController by lazy {
        MetronomeController(appContext, appSettings, musicPlaying, musicTimeline, applicationScope)
    }

    val recordingStore: RecordingStore by lazy { RecordingStore(appContext) }

    val singAlongSession: SingAlongSession by lazy {
        SingAlongSession(appContext, stemRepository, recordingStore, metronomeController, ::createPlaybackController)
    }

    fun createWidgetStatePublisher(): WidgetStatePublisher =
        WidgetStatePublisher(appContext, widgetStateStore, applicationScope)

    fun createPlaybackController(scope: CoroutineScope): PlaybackController =
        PlaybackController(appContext, scope, songRepository, playbackSessionStore)
}
