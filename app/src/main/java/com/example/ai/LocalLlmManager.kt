package com.example.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class LlmState {
  NOT_DOWNLOADED,
  DOWNLOADING,
  INITIALIZING,
  READY,
  ERROR,
}

class LocalLlmManager(private val context: Context) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _state = MutableStateFlow(LlmState.NOT_DOWNLOADED)
  val state: StateFlow<LlmState> = _state.asStateFlow()

  private val _downloadProgress = MutableStateFlow(0f)
  val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

  private val _statusMessage = MutableStateFlow("Local AI model not installed")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private var llmInference: LlmInference? = null

  private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
  val modelFile = File(modelDir, "llm_model.bin")

  // Default lightweight quantized Gemma / SmolLM weights hosted for mobile
  val defaultModelUrl = "https://huggingface.co/google/gemma-2-2b-it-cpu-int4/resolve/main/gemma-2-2b-it-cpu-int4.bin"

  init {
    checkModelStatus()
  }

  fun checkModelStatus() {
    if (modelFile.exists() && modelFile.length() > 10 * 1024 * 1024) {
      initEngine()
    } else {
      _state.value = LlmState.NOT_DOWNLOADED
      _statusMessage.value = "Model not installed. Download (~850 MB) to enable offline AI."
    }
  }

  fun initEngine() {
    if (!modelFile.exists() || modelFile.length() < 10 * 1024 * 1024) {
      _state.value = LlmState.NOT_DOWNLOADED
      return
    }

    _state.value = LlmState.INITIALIZING
    _statusMessage.value = "Loading on-device model into memory..."

    scope.launch(Dispatchers.IO) {
      try {
        val options =
          LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .setTemperature(0.2f)
            .setTopK(40)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
        withContext(Dispatchers.Main) {
          _state.value = LlmState.READY
          _statusMessage.value = "On-device AI ready (100% offline)"
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _state.value = LlmState.ERROR
          _statusMessage.value = "Could not initialize model: ${e.localizedMessage ?: "Unknown error"}"
        }
      }
    }
  }

  suspend fun generateResponse(prompt: String): String =
    withContext(Dispatchers.IO) {
      val engine = llmInference
      if (engine == null || _state.value != LlmState.READY) {
        throw IllegalStateException("Local AI engine is not ready")
      }
      engine.generateResponse(prompt)
    }

  fun startDownload(url: String = defaultModelUrl) {
    if (_state.value == LlmState.DOWNLOADING) return

    _state.value = LlmState.DOWNLOADING
    _downloadProgress.value = 0f
    _statusMessage.value = "Connecting to download server..."

    scope.launch(Dispatchers.IO) {
      val client =
        OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .build()

      val tempFile = File(modelDir, "llm_model.bin.tmp")
      try {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
          throw IllegalStateException("Server returned HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Empty response body")
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input ->
          FileOutputStream(tempFile).use { output ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
              output.write(buffer, 0, read)
              downloadedBytes += read
              if (totalBytes > 0) {
                val progress = downloadedBytes.toFloat() / totalBytes
                withContext(Dispatchers.Main) {
                  _downloadProgress.value = progress
                  val mbDone = downloadedBytes / (1024 * 1024)
                  val mbTotal = totalBytes / (1024 * 1024)
                  _statusMessage.value = "Downloading model: $mbDone MB / $mbTotal MB (${(progress * 100).toInt()}%)"
                }
              }
            }
          }
        }

        if (modelFile.exists()) modelFile.delete()
        tempFile.renameTo(modelFile)

        withContext(Dispatchers.Main) {
          _downloadProgress.value = 1f
          _statusMessage.value = "Download complete. Initializing on-device engine..."
          initEngine()
        }
      } catch (e: Exception) {
        if (tempFile.exists()) tempFile.delete()
        withContext(Dispatchers.Main) {
          _state.value = LlmState.ERROR
          _statusMessage.value = "Download failed: ${e.localizedMessage ?: "Network error"}"
        }
      }
    }
  }

  fun importModel(sourceFile: File) {
    scope.launch(Dispatchers.IO) {
      _state.value = LlmState.INITIALIZING
      _statusMessage.value = "Importing model file..."
      try {
        sourceFile.copyTo(modelFile, overwrite = true)
        withContext(Dispatchers.Main) {
          initEngine()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _state.value = LlmState.ERROR
          _statusMessage.value = "Failed to import model: ${e.localizedMessage}"
        }
      }
    }
  }

  fun deleteModel() {
    scope.launch(Dispatchers.IO) {
      llmInference = null
      if (modelFile.exists()) modelFile.delete()
      withContext(Dispatchers.Main) {
        _state.value = LlmState.NOT_DOWNLOADED
        _statusMessage.value = "Model deleted. Reclaim ~850 MB storage."
      }
    }
  }
}
