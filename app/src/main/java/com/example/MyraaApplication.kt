package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.ai.GeminiLiveManager
import com.example.ai.GeminiRestClient
import com.example.ai.ToolCall
import com.example.audio.AudioInputManager
import com.example.audio.AudioOutputManager
import com.example.browser.BrowserAgent
import com.example.camera.CameraQuickSnapManager
import com.example.health.DeviceHealthMonitor
import com.example.location.LocationHelper
import com.example.memory.MemoryEntity
import com.example.memory.MemoryRepository
import com.example.memory.MyraaDatabase
import com.example.personality.MoodEngine
import com.example.personality.MyraaMood
import com.example.personality.MyraaPersonality
import com.example.services.MyraaForegroundAudioService
import com.example.tools.ToolRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyraaApplication : Application() {

    companion object {
        private const val TAG = "MyraaApplication"
        lateinit var instance: MyraaApplication
            private set
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Subsystem Singletons
    lateinit var database: MyraaDatabase
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var moodEngine: MoodEngine
        private set
    lateinit var audioOutputManager: AudioOutputManager
        private set
    lateinit var audioInputManager: AudioInputManager
        private set
    lateinit var geminiLiveManager: GeminiLiveManager
        private set
    lateinit var geminiRestClient: GeminiRestClient
        private set
    lateinit var browserAgent: BrowserAgent
        private set
    lateinit var locationHelper: LocationHelper
        private set
    lateinit var healthMonitor: DeviceHealthMonitor
        private set
    lateinit var cameraManager: CameraQuickSnapManager
        private set
    lateinit var toolRouter: ToolRouter
        private set

    // Real-time state flows for UI
    private val _userTranscription = MutableStateFlow("")
    val userTranscription: StateFlow<String> = _userTranscription.asStateFlow()

    private val _myraaTranscription = MutableStateFlow("")
    val myraaTranscription: StateFlow<String> = _myraaTranscription.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _activeThemeColor = MutableStateFlow("violet")
    val activeThemeColor: StateFlow<String> = _activeThemeColor.asStateFlow()

    private val _lastActionStatus = MutableStateFlow<String?>(null)
    val lastActionStatus: StateFlow<String?> = _lastActionStatus.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        initChannels()
        initDatabase()
        initMoodAndTools()
        initAudioAndGemini()

        appScope.launch {
            memoryRepository.ensureDefaultMemories()
            healthMonitor.refreshHealth()
        }
    }

    private fun initChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val audioChannel = NotificationChannel(
                MyraaForegroundAudioService.CHANNEL_ID,
                getString(R.string.foreground_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.foreground_service_desc)
            }
            nm.createNotificationChannel(audioChannel)
        }
    }

    private fun initDatabase() {
        database = MyraaDatabase.getDatabase(this, appScope)
        memoryRepository = MemoryRepository(database.memoryDao())
    }

    private fun initMoodAndTools() {
        moodEngine = MoodEngine(appScope)
        browserAgent = BrowserAgent(this)
        locationHelper = LocationHelper(this)
        healthMonitor = DeviceHealthMonitor(this)
        cameraManager = CameraQuickSnapManager(this)

        toolRouter = ToolRouter(
            context = this,
            scope = appScope,
            memoryRepository = memoryRepository,
            browserAgent = browserAgent,
            locationHelper = locationHelper,
            healthMonitor = healthMonitor,
            moodEngine = moodEngine,
            onThemeChanged = { color ->
                _activeThemeColor.value = color
            }
        )
    }

    private fun initAudioAndGemini() {
        geminiRestClient = GeminiRestClient()

        audioOutputManager = AudioOutputManager(
            scope = appScope,
            onPlaybackStateChanged = { isPlaying ->
                if (isPlaying) {
                    // When companion begins speaking, highlight mood or excitement
                    if (moodEngine.currentMood.value == MyraaMood.IDLE) {
                        moodEngine.setMood(MyraaMood.HAPPY)
                    }
                }
            },
            onOutputAmplitudeChanged = { amp ->
                _outputAmplitude.value = amp
            }
        )

        geminiLiveManager = GeminiLiveManager(
            scope = appScope,
            onAudioReceived = { pcmChunk ->
                audioOutputManager.enqueueAudio(pcmChunk)
            },
            onInterrupted = {
                // Barge-in: immediately flush audio playback
                audioOutputManager.flushAndInterrupt()
                _myraaTranscription.value += " [Interrupted]"
            },
            onTranscriptionReceived = { role, text ->
                if (role == "user") {
                    _userTranscription.value = text
                    moodEngine.inferMoodFromText(text, isUser = true)
                } else {
                    _myraaTranscription.value = text
                    moodEngine.inferMoodFromText(text, isUser = false)
                }
            },
            onToolCallReceived = { toolCall ->
                handleIncomingToolCall(toolCall)
            },
            onError = { errMsg ->
                _lastActionStatus.value = errMsg
                Log.e(TAG, "Gemini Live Error: $errMsg")
            }
        )

        audioInputManager = AudioInputManager(
            scope = appScope,
            onAudioChunk = { chunk ->
                geminiLiveManager.sendAudioChunk(chunk)
            },
            onUserSpeechDetected = {
                // User started speaking while Myraa was playing audio -> Barge-In
                if (audioOutputManager.isPlaying) {
                    audioOutputManager.flushAndInterrupt()
                }
            },
            onAmplitudeChanged = { amp ->
                _inputAmplitude.value = amp
            }
        )
    }

    private fun handleIncomingToolCall(toolCall: ToolCall) {
        appScope.launch {
            moodEngine.setMood(MyraaMood.THINKING)
            _lastActionStatus.value = "Executing: ${toolCall.name}..."
            val result = toolRouter.executeTool(toolCall.name, toolCall.args)
            geminiLiveManager.sendToolResponse(toolCall.id, toolCall.name, result)
            _lastActionStatus.value = "Executed: ${toolCall.name} -> ${result["status"]}"
        }
    }

    suspend fun getFormattedSystemPrompt(): String {
        val memories = memoryRepository.allMemories.first()
        val memBlock = memoryRepository.formatMemoriesForSystemPrompt(memories)
        return MyraaPersonality.buildSystemInstruction(memBlock)
    }
}
