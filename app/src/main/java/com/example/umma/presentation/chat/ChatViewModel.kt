package com.example.umma.presentation.chat

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.data.source.local.AudioPlayer
import com.example.umma.data.source.local.AudioRecorder
import com.example.umma.domain.model.AIEvent
import com.example.umma.domain.model.AIState
import com.example.umma.domain.usecase.ObserveAIEventUseCase
import com.example.umma.domain.usecase.SendAudioDataUseCase
import com.example.umma.domain.usecase.StartSessionUseCase
import com.example.umma.domain.usecase.StopSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val observeAIEventUseCase: ObserveAIEventUseCase,
    private val sendAudioDataUseCase: SendAudioDataUseCase,
    private val stopSessionUseCase: StopSessionUseCase,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())

    val uiState = _uiState.asStateFlow()

    private var chatJob: Job? = null

    fun startChat() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChatting = true, errorMessage = null) }

            startSessionUseCase().onSuccess {
                audioPlayer.startPlaying()
                startChatLoop()
            }.onFailure { e ->
                _uiState.update { it.copy(isChatting = false, errorMessage = e.message) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startChatLoop() {
        chatJob = viewModelScope.launch {
            launch {
                observeAIEventUseCase().collect { event ->
                    when (event) {
                        is AIEvent.AudioResponse -> {
                            audioPlayer.playAudioChunk(event.audio)
                        }
                        is AIEvent.TextResponse -> {
                            _uiState.update { it.copy(lastTranscription = event.text) }
                        }
                        is AIEvent.StateChanged -> {
                            _uiState.update { it.copy(aiState = event.state) }
                        }
                        is AIEvent.Error -> {
                            _uiState.update { it.copy(errorMessage = event.message) }
                        }
                    }

                }
            }

            launch  {
                audioRecorder.startRecording().collect { audioChunk ->
                    sendAudioDataUseCase(audioChunk)
                }
            }
        }
    }

    fun stopChat() {
        viewModelScope.launch {
            chatJob?.cancel()
            stopSessionUseCase()
            audioPlayer.stopPlaying()
            _uiState.update { it.copy(isChatting = false, aiState = AIState.IDLE) }
        }
    }

    override fun onCleared() {
        super.onCleared()

        audioPlayer.release()
    }

}