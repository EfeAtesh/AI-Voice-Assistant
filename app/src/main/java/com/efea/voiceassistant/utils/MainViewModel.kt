package com.efea.voiceassistant.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efea.voiceassistant.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(context: Context) : ViewModel() {
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage
    
    private val _assistantResponse = MutableStateFlow("")
    val assistantResponse: StateFlow<String> = _assistantResponse

    private val gemmaManager = ModelManager(context)
    private val phonemeConverter = PhonemeConverter(context)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Initialize Kokoro
                OnnxRuntimeManager.initialize(context.applicationContext)
                
                // Initialize Gemma
                gemmaManager.initModel(object : ModelManager.OnLoadedCallback {
                    override fun onSuccess() {
                        _isInitialized.value = true
                        _statusMessage.value = "Ready"
                    }

                    override fun onError(error: String) {
                        _statusMessage.value = "Error: $error"
                    }
                })
            } catch (e: Exception) {
                _statusMessage.value = "Init Error: ${e.message}"
            }
        }
    }

    fun processVoiceInput(context: Context, text: String) {
        _statusMessage.value = "Thinking..."
        _assistantResponse.value = "" // Clear previous response
        
        gemmaManager.ask(text, object : ModelManager.OnResultCallback {
            override fun onResult(result: String) {
                _assistantResponse.value = result // Store text response
                _statusMessage.value = "Speaking..."
                generateAndPlayAudio(context, result)
            }

            override fun onError(error: String) {
                _statusMessage.value = "Gemma Error: $error"
            }
        })
    }

    private fun generateAndPlayAudio(context: Context, text: String, voice: String = "af_sky") {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Convert text to phonemes
                val phonemes = phonemeConverter.phonemize(text)
                
                val session = OnnxRuntimeManager.getSession()
                
                // 2. Generate audio from phonemes
                val (audioData, sampleRate) = createAudio(phonemes, voice, 1.0f, session, context)
                
                // 3. Play the audio
                playAudio(audioData, sampleRate)
                _statusMessage.value = "Ready"
            } catch (e: Exception) {
                _statusMessage.value = "TTS Error: ${e.message}"
                e.printStackTrace()
            }
        }
    }

    private fun playAudio(audioData: FloatArray, sampleRate: Int) {
        // Convert FloatArray to ShortArray for wider compatibility
        val shortAudioData = ShortArray(audioData.size) { i ->
            (audioData[i] * 32767).coerceIn(-32768f, 32767f).toInt().toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(shortAudioData.size * 2) // Short is 2 bytes
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(shortAudioData, 0, shortAudioData.size)
        audioTrack.play()
    }
}
