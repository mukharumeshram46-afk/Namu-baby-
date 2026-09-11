package com.example.personality

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MyraaMood(val displayName: String, val auraColor: Color, val secondaryColor: Color) {
    IDLE("Idle", Color(0xFF00E5FF), Color(0xFF7C4DFF)),
    HAPPY("Happy", Color(0xFFFFD54F), Color(0xFFFF4081)),
    CUTE("Cute", Color(0xFFFF80AB), Color(0xFFEA80FC)),
    SHY("Shy", Color(0xFFF48FB1), Color(0xFFCE93D8)),
    PLAYFUL("Playful", Color(0xFF00E676), Color(0xFF18FFFF)),
    TEASING("Teasing", Color(0xFFFF6E40), Color(0xFFFF1744)),
    MOCK_ANGRY("Nakhre", Color(0xFFFF5252), Color(0xFFFF4081)),
    CARING("Caring", Color(0xFF80D8FF), Color(0xFFB388FF)),
    PROUD("Proud", Color(0xFFFFD700), Color(0xFF00E5FF)),
    EXCITED("Excited", Color(0xFFFFAB00), Color(0xFFFF007F)),
    SLEEPY("Sleepy", Color(0xFF90CAF9), Color(0xFFB39DDB)),
    THINKING("Thinking", Color(0xFFB388FF), Color(0xFF00E5FF))
}

class MoodEngine(private val scope: CoroutineScope) {

    private val _currentMood = MutableStateFlow(MyraaMood.IDLE)
    val currentMood: StateFlow<MyraaMood> = _currentMood.asStateFlow()

    private var decayJob: Job? = null

    fun setMood(mood: MyraaMood) {
        _currentMood.value = mood
        scheduleDecay()
    }

    fun inferMoodFromText(text: String, isUser: Boolean = false) {
        val lower = text.lowercase()
        val detectedMood = when {
            lower.contains("nakhre") || lower.contains("gussa") || lower.contains("drama") -> MyraaMood.MOCK_ANGRY
            lower.contains("haha") || lower.contains("hehe") || lower.contains("lol") || lower.contains("joke") || lower.contains("masti") -> MyraaMood.PLAYFUL
            lower.contains("caring") || lower.contains("khayal") || lower.contains("help") || lower.contains("worry") || lower.contains("take care") -> MyraaMood.CARING
            lower.contains("shy") || lower.contains("sharam") || lower.contains("blush") -> MyraaMood.SHY
            lower.contains("cute") || lower.contains("sweet") || lower.contains("pyaari") -> MyraaMood.CUTE
            lower.contains("proud") || lower.contains("kamaal") || lower.contains("great job") || lower.contains("shabash") -> MyraaMood.PROUD
            lower.contains("excited") || lower.contains("wow") || lower.contains("awesome") || lower.contains("super") -> MyraaMood.EXCITED
            lower.contains("happy") || lower.contains("khush") || lower.contains("yay") -> MyraaMood.HAPPY
            lower.contains("sleepy") || lower.contains("neend") || lower.contains("thak") || lower.contains("good night") -> MyraaMood.SLEEPY
            lower.contains("think") || lower.contains("soch") || lower.contains("analyze") || lower.contains("calculate") -> MyraaMood.THINKING
            lower.contains("tease") || lower.contains("chhed") -> MyraaMood.TEASING
            else -> null
        }

        if (detectedMood != null) {
            setMood(detectedMood)
        }
    }

    private fun scheduleDecay() {
        decayJob?.cancel()
        decayJob = scope.launch(Dispatchers.Default) {
            delay(18000) // Mood holds for 18 seconds before gently settling back
            _currentMood.value = MyraaMood.IDLE
        }
    }
}
