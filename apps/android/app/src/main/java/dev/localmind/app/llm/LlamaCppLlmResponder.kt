package dev.localmind.app.llm

import android.content.Context
import dev.localmind.app.search.LocalMindPromptBuilder
import dev.localmind.app.search.SearchOptions
import dev.localmind.app.search.SmallLlmResponder
import dev.localmind.app.search.SmallLlmResponse
import dev.localmind.app.search.TinyLocalLlmResponder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal object LlamaNativeBridge {
    init {
        System.loadLibrary("localmind_llama")
    }

    external fun nativeLoadModel(modelPath: String, contextSize: Int, threads: Int): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeFreeModel(handle: Long)
}

/**
 * Runs an actual quantized GGUF small LLM (SmolLM2-135M-Instruct Q4_K_M)
 * inside the Android app via llama.cpp JNI.
 */
class LlamaCppLlmResponder(
    private val context: Context,
    private val assetModelPath: String = "models/smollm2-135m-instruct-q4_k_m.gguf",
    override val modelName: String = "SmolLM2-135M-Instruct (Q4_K_M · llama.cpp)",
) : SmallLlmResponder {

    override val isNativeRuntime: Boolean = true

    private val mutex = Mutex()
    private val fallback = TinyLocalLlmResponder()
    private var modelHandle: Long = 0L

    override suspend fun ensureLoaded(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (modelHandle != 0L) return@withLock Result.success(Unit)
            runCatching {
                val modelFile = ensureModelCopiedToFilesDir()
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                val handle = LlamaNativeBridge.nativeLoadModel(
                    modelPath = modelFile.absolutePath,
                    contextSize = 512,
                    threads = threads,
                )
                require(handle != 0L) { "llama.cpp failed to load ${modelFile.name}" }
                modelHandle = handle
            }
        }
    }

    override suspend fun respondToSearchAsync(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Boolean,
    ): SmallLlmResponse = withContext(Dispatchers.IO) {
        val loadResult = ensureLoaded()
        if (loadResult.isFailure || modelHandle == 0L) {
            return@withContext fallback.respondToSearch(query, options, triggeredFromWidget).copy(
                answer = "Fallback (${loadResult.exceptionOrNull()?.message ?: "model unavailable"}): " +
                    fallback.respondToSearch(query, options, triggeredFromWidget).answer,
            )
        }

        val prompt = LocalMindPromptBuilder.buildChatMlPrompt(
            query = query,
            options = options,
            triggeredFromWidget = triggeredFromWidget,
        )
        val startNanos = System.nanoTime()
        val rawOutput = mutex.withLock {
            LlamaNativeBridge.nativeGenerate(modelHandle, prompt, maxTokens = 72)
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

        val cleaned = LocalMindPromptBuilder.cleanAssistantOutput(rawOutput)
        val finalAnswer = if (cleaned.isNotBlank()) {
            cleaned
        } else {
            "Searching is not yet supported in this version."
        }

        SmallLlmResponse(
            id = "${System.nanoTime()}-${query.hashCode()}",
            modelName = modelName,
            query = query.trim(),
            options = options,
            answer = finalAnswer,
            searchSupported = false,
            triggeredFromWidget = triggeredFromWidget,
            inferenceMillis = elapsedMs,
            ranNativeLlm = true,
        )
    }

    override fun respondToSearch(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Boolean,
    ): SmallLlmResponse {
        return fallback.respondToSearch(query, options, triggeredFromWidget)
    }

    private fun ensureModelCopiedToFilesDir(): File {
        val outFile = File(context.filesDir, assetModelPath)
        outFile.parentFile?.mkdirs()
        if (outFile.exists() && outFile.length() > 1_000_000L) {
            return outFile
        }
        context.assets.open(assetModelPath).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        return outFile
    }
}
