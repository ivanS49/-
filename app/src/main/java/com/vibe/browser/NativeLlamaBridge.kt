package com.vibe.browser

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaAndroid
import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class NativeLlamaBridge(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val tag = "NativeLlamaBridge"
    private val llamaAndroid = LlamaAndroid(contentResolver)
    
    @Volatile
    private var activeContextId: Int? = null
    
    @Volatile
    private var currentModelName: String? = null

    @Volatile
    private var tokenCallback: ((String) -> Unit)? = null

    data class ModelInfo(
        val name: String,
        val contextId: Int,
        val fileSizeMb: Double,
        val contextSize: Int,
        val usedMmap: Boolean
    )

    fun isLoaded(): Boolean = activeContextId != null
    fun getLoadedModelName(): String? = currentModelName

    suspend fun loadModel(
        uri: Uri,
        modelDisplayName: String,
        contextSize: Int,
        onStatusUpdate: (String) -> Unit
    ): Result<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            // Release existing context if any
            release()

            onStatusUpdate(context.getString(R.string.llm_check_format))
            val isGguf = try {
                llamaAndroid.isGGUF(uri)
            } catch (e: Exception) {
                Log.w(tag, "isGGUF check threw exception: ${e.message}")
                true // Try continuing if check failed unexpectedly
            }

            if (!isGguf) {
                return@withContext Result.failure(IllegalArgumentException(context.getString(R.string.llm_err_not_gguf)))
            }

            onStatusUpdate(context.getString(R.string.llm_open_fd))
            val pfd = contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.llm_err_fd_null)))

            val fileSize = pfd.statSize
            val fileSizeMb = if (fileSize > 0) fileSize.toDouble() / (1024 * 1024) else 0.0

            val fd = pfd.detachFd()

            onStatusUpdate(context.getString(R.string.llm_init_mmap, String.format(java.util.Locale.US, "%.1f", fileSizeMb)))

            // Determine thread count (balanced for mobile CPUs)
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val threadCount = cpuCores.coerceIn(2, 6)

            // 1. Try with use_mmap = true first (efficient on-demand RAM paging, won't OOM)
            val configMmap = mutableMapOf<String, Any>(
                "model" to uri.toString(),
                "model_fd" to fd,
                "use_mmap" to true,
                "use_mlock" to false,
                "n_ctx" to contextSize,
                "embedding" to false,
                "n_batch" to 512,
                "n_threads" to threadCount,
                "n_gpu_layers" to 0,
                "vocab_only" to false,
                "lora" to "",
                "lora_scaled" to 1.0,
                "rope_freq_base" to 0.0,
                "rope_freq_scale" to 0.0
            )

            var result = try {
                llamaAndroid.startEngine(configMmap) { token ->
                    tokenCallback?.invoke(token)
                }
            } catch (e: Exception) {
                Log.w(tag, "startEngine with mmap failed: ${e.message}, trying without mmap...")
                null
            }

            var usedMmap = true

            // 2. If mmap failed, try fallback
            if (result == null) {
                onStatusUpdate(context.getString(R.string.llm_retry_direct_ram))
                val pfdFallback = contentResolver.openFileDescriptor(uri, "r")
                if (pfdFallback != null) {
                    val fdFallback = pfdFallback.detachFd()
                    configMmap["model_fd"] = fdFallback
                    configMmap["use_mmap"] = false
                    usedMmap = false
                    result = try {
                        llamaAndroid.startEngine(configMmap) { token ->
                            tokenCallback?.invoke(token)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "startEngine fallback failed", e)
                        null
                    }
                }
            }

            if (result == null) {
                return@withContext Result.failure(IllegalStateException(context.getString(R.string.llm_err_engine_init)))
            }

            val contextId = (result["contextId"] as? Number)?.toInt()
                ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.llm_err_no_context_id)))

            activeContextId = contextId
            currentModelName = modelDisplayName

            Result.success(
                ModelInfo(
                    name = modelDisplayName,
                    contextId = contextId,
                    fileSizeMb = fileSizeMb,
                    contextSize = contextSize,
                    usedMmap = usedMmap
                )
            )
        } catch (e: Throwable) {
            Log.e(tag, "loadModel error", e)
            Result.failure(e)
        }
    }

    @Volatile
    private var isGenerating = false

    fun isPredicting(): Boolean = isGenerating

    suspend fun stopPrediction() {
        isGenerating = false
        activeContextId?.let { id ->
            try {
                llamaAndroid.stopCompletion(id)
            } catch (e: Exception) {
                Log.w(tag, "stopCompletion error: ${e.message}")
            }
        }
    }

    suspend fun predict(
        prompt: String,
        temperature: Float = 0.7f,
        systemPrompt: String = "",
        onToken: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val contextId = activeContextId
            ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.llm_no_model)))

        isGenerating = true
        try {
            val fullTextBuilder = StringBuilder()
            val tokenCount = AtomicInteger(0)

            tokenCallback = { token ->
                if (isGenerating) {
                    fullTextBuilder.append(token)
                    tokenCount.incrementAndGet()
                    onToken(token)
                }
            }

            val finalPrompt = if (systemPrompt.isNotBlank()) {
                "System: $systemPrompt\n\nUser: $prompt\nAssistant: "
            } else {
                prompt
            }

            val params = mutableMapOf<String, Any>(
                "prompt" to finalPrompt,
                "temperature" to temperature,
                "n_predict" to 2048,
                "emit_partial_completion" to true
            )

            val completionResult = llamaAndroid.launchCompletion(contextId, params)
            val resultText = completionResult?.get("text") as? String ?: fullTextBuilder.toString()

            Result.success(resultText)
        } catch (e: Throwable) {
            Log.e(tag, "predict error", e)
            Result.failure(e)
        } finally {
            isGenerating = false
            tokenCallback = null
        }
    }

    fun release() {
        activeContextId?.let { id ->
            try {
                llamaAndroid.releaseContext(id)
            } catch (e: Exception) {
                Log.w(tag, "Error releasing context $id: ${e.message}")
            }
            activeContextId = null
            currentModelName = null
        }
    }
}
