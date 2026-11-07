package com.example.offlineai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexasw.llama.LlamaContext
import com.hexasw.llama.LlamaModel
import com.hexasw.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AIModel(val fileName: String, val displayName: String) {
    QWEN("qwen2.5-1.5b-instruct-q4_k_m.gguf", "Qwen 2.5 1.5B (Q4_K_M)"),
    LLAMA("llama-3.2-3b-instruct-q4_k_m.gguf", "Llama 3.2 3B (Q4_K_M)")
}

class LocalAIViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<String>("Select a model to begin.")
    val uiState: StateFlow<String> = _uiState

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private var nativeModel: LlamaModel? = null
    private var nativeContext: LlamaContext? = null
    
    private val aiStorageDir: File = File(getApplication<Application>().filesDir, "ai_documents")

    init {
        if (!aiStorageDir.exists()) { aiStorageDir.mkdirs() }
    }

    fun initializeModel(model: AIModel) {
        viewModelScope.launch {
            _isProcessing.value = true
            _uiState.value = "Binding memory map addresses for ${model.displayName}..."

            val localFile = File(getApplication<Application>().filesDir, model.fileName)
            
            if (!localFile.exists()) {
                _uiState.value = "Extracting model from APK asset layer..."
                extractAssetToDisk(model.fileName, localFile)
            }

            try {
                withContext(Dispatchers.Default) {
                    nativeContext?.release()
                    nativeModel?.release()

                    val config = LlamaConfig().apply {
                        contextSize = 2048
                        threads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
                    }

                    nativeModel = LlamaModel.load(localFile.absolutePath)
                    nativeContext = nativeModel?.createContext(config)
                }
                _uiState.value = "${model.displayName} active on native thread!\nReady to read and write files."
            } catch (e: Exception) {
                Log.e("OfflineAI", "Native model mapping failure", e)
                _uiState.value = "Native Module Init Error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun generateAndWriteToFile(prompt: String, outputFileName: String) {
        val context = nativeContext
        if (context == null) {
            _uiState.value = "Aborted: Native context object is uninitialized."
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            _uiState.value = "Processing native token evaluation loops..."

            try {
                val fullResponseBuilder = StringBuilder()
                context.inputPrompt(prompt)
                
                while (!context.isSequenceFinished()) {
                    val tokenPiece = context.computeNextTokenPiece()
                    fullResponseBuilder.append(tokenPiece)
                }

                val finalOutputText = fullResponseBuilder.toString()
                val targetFile = File(aiStorageDir, outputFileName)
                
                // Pure Native I/O Write to internal sandboxed storage
                FileOutputStream(targetFile).use { stream ->
                    stream.write(finalOutputText.toByteArray())
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = "File Written Successfully!\nPath: ${targetFile.absolutePath}\n\nContent:\n$finalOutputText"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = "Inference Exception Loop Error: ${e.localizedMessage}"
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun readPromptFromFileAndRun(inputFileName: String, outputFileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val inputFile = File(aiStorageDir, inputFileName)
            if (!inputFile.exists()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = "Input payload structure '$inputFileName' missing from tracking directory."
                }
                return
            }

            try {
                // Pure Native I/O Read from internal sandboxed storage
                val readPrompt = inputFile.readText()
                generateAndWriteToFile(prompt = readPrompt, outputFileName = outputFileName)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = "Storage Read Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private suspend fun extractAssetToDisk(assetName: String, targetFile: File) = withContext(Dispatchers.IO) {
        getApplication<Application>().assets.open(assetName).use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nativeContext?.release()
        nativeModel?.release()
    }
}
