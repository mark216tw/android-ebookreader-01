package com.minireader.app.data.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TtsStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR,
}

data class ReaderTtsState(
    val status: TtsStatus = TtsStatus.IDLE,
    val bookId: String? = null,
    val chapterHref: String? = null,
    val chapterTitle: String = "",
    val speed: Float = 1f,
    val currentChunkIndex: Int = 0,
    val ratio: Float = 0f,
    val error: String? = null,
) {
    val isActive: Boolean
        get() = status == TtsStatus.PREPARING ||
            status == TtsStatus.PLAYING ||
            status == TtsStatus.PAUSED
}

object ReaderTtsPlayback {

    private val _state = MutableStateFlow(ReaderTtsState())
    val state: StateFlow<ReaderTtsState> = _state.asStateFlow()

    internal fun set(state: ReaderTtsState) {
        _state.value = state
    }

    internal fun update(transform: (ReaderTtsState) -> ReaderTtsState) {
        _state.update(transform)
    }
}

val TtsSpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
