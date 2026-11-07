package com.example.offlineai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: LocalAIViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OfflineAIScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAIScreen(viewModel: LocalAIViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Native AI Test Harness", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.initializeModel(AIModel.QWEN) }, enabled = !isProcessing) {
                Text("Load Qwen")
            }
            Button(onClick = { viewModel.initializeModel(AIModel.LLAMA) }, enabled = !isProcessing) {
                Text("Load Llama")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(text = uiState, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter testing prompt context...") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    viewModel.generateAndWriteToFile(inputText, "ai_output.txt")
                    inputText = ""
                },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                enabled = inputText.isNotBlank() && !isProcessing
            ) {
                Text("Write text out")
            }

            Button(
                onClick = { viewModel.readPromptFromFileAndRun("prompt_input.txt", "ai_output.txt") },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                enabled = !isProcessing
            ) {
                Text("Read source file")
            }
        }
    }
}
