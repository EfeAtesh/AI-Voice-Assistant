package com.efea.voiceassistant.utils

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efea.voiceassistant.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelManager(context: Context) : ViewModel() {
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    init {
        viewModelScope.launch(Dispatchers.IO) {
            OnnxRuntimeManager.initialize(context.applicationContext)
            _isInitialized.value = true
        }
    }

    fun generateAndPlayAudio(context: Context, text: String, voice: String = "af_sky") {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val session = OnnxRuntimeManager.getSession()
                // In a real app, you'd convert text to phonemes first. 
                // For this example, we assume 'text' is already phonemes or handled by createAudio.
                // Kokoro usually needs phonemes.
                val (audioData, sampleRate) = createAudio(text, voice, 1.0f, session, context)
                playAudio(audioData, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playAudio(audioData: FloatArray, sampleRate: Int) {
        val bufferSize = android.media.AudioTrack.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_FLOAT
        )

        val audioTrack = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(audioData.size * 4)
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(audioData, 0, audioData.size, android.media.AudioTrack.WRITE_BLOCKING)
        audioTrack.play()
    }
}

object OnnxRuntimeManager {
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null

    @Synchronized
    fun initialize(context: Context) {
        if (environment == null) {
            environment = OrtEnvironment.getEnvironment()
            session = createSession(context)
        }
    }

    private fun createSession(context: Context): OrtSession {
        val options = SessionOptions().apply {
            addConfigEntry("nnapi.flags", "USE_FP16")
            // Some devices might fail with NNAPI, so consider making this optional
        }

        return context.resources.openRawResource(R.raw.kokoro).use { stream ->
            environment!!.createSession(stream.readBytes(), options)
        }
    }

    fun getSession() = requireNotNull(session) { "ONNX Session not initialized" }
}
