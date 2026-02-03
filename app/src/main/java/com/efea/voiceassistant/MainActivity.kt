package com.efea.voiceassistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efea.voiceassistant.ui.theme.MyApplicationTheme
import com.efea.voiceassistant.utils.MainViewModel
import com.efea.voiceassistant.utils.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(context)
                )
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceAssistantScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceAssistantScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val isInitialized: State<Boolean> = viewModel.isInitialized.collectAsState(initial = false)
    val statusMessage: State<String> = viewModel.statusMessage.collectAsState(initial = "Initializing...")
    val assistantResponse: State<String> = viewModel.assistantResponse.collectAsState(initial = "")
    val context = LocalContext.current
    var spokenText by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = data?.get(0) ?: ""
            spokenText = text
            if (text.isNotEmpty()) {
                viewModel.processVoiceInput(context, text)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isInitialized.value) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusMessage.value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        } else {
            if (spokenText.isNotEmpty()) {
                Text(
                    text = "You: $spokenText",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (assistantResponse.value.isNotEmpty()) {
                Text(
                    text = "Assistant: ${assistantResponse.value}",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else if (spokenText.isEmpty()) {
                Text(
                    text = "Tap the mic and speak",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            Text(
                text = "Status: ${statusMessage.value}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "How can I help you?")
                    }
                    try {
                        launcher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Speech recognition not supported", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        modifier = Modifier.size(40.dp)
                    )
                    Text("Speak")
                }
            }
        }
    }
}
