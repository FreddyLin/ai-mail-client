package net.thunderbird.feature.ai.provider.openai

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.ai.api.AiCapability
import net.thunderbird.feature.ai.api.AiClassificationCategory
import net.thunderbird.feature.ai.api.AiClassificationInput
import net.thunderbird.feature.ai.api.AiClassificationResult
import net.thunderbird.feature.ai.api.AiCredentialStore
import net.thunderbird.feature.ai.api.AiError
import net.thunderbird.feature.ai.api.AiModelId
import net.thunderbird.feature.ai.api.AiProvider
import net.thunderbird.feature.ai.api.AiProviderId
import net.thunderbird.feature.ai.api.AiRequest
import net.thunderbird.feature.ai.api.AiResult
import net.thunderbird.feature.ai.api.AiResultMetadata
import net.thunderbird.feature.ai.api.AiSettingsRepository
import net.thunderbird.feature.ai.api.AiSummarizationInput
import net.thunderbird.feature.ai.api.AiSummarizationResult
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

internal class OpenAiProvider(
    private val httpClient: OkHttpClient,
    private val credentialStore: AiCredentialStore,
    private val settingsRepository: AiSettingsRepository,
    private val endpoint: HttpUrl = DEFAULT_ENDPOINT,
) : AiProvider {
    init {
        require(endpoint.isHttps) { "OpenAI endpoint must use HTTPS" }
    }

    override val id: AiProviderId = OPENAI_PROVIDER_ID
    override val modelId: AiModelId = DEFAULT_MODEL_ID
    override val capabilities: Set<AiCapability> = setOf(AiCapability.CLASSIFICATION, AiCapability.SUMMARIZATION)

    override suspend fun execute(request: AiRequest): AiResult {
        if (request !is AiRequest.Classification && request !is AiRequest.Summarization) {
            return AiResult.Failure(AiError.UnsupportedCapability(request.capability))
        }

        val credential = credentialStore.read(id)
            ?: return AiResult.Failure(AiError.Authentication)
        val configuredModel = settingsRepository.settings.first().providerConfiguration
            ?.takeIf { it.providerId == id }
            ?.modelId
            ?: return AiResult.Failure(AiError.ProviderNotConfigured)

        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${credential.secret}")
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(requestBody(configuredModel, request))
            .build()

        return try {
            httpClient.newCall(httpRequest).await().use { response ->
                response.toAiResult(configuredModel, request)
            }
        } catch (_: CancellationException) {
            AiResult.Failure(AiError.Cancelled)
        } catch (_: IOException) {
            AiResult.Failure(AiError.Network)
        } catch (_: Exception) {
            AiResult.Failure(AiError.InvalidResponse)
        }
    }

    private fun requestBody(modelId: AiModelId, request: AiRequest) =
        buildJsonObject {
            put("model", JsonPrimitive(modelId.value))
            put("reasoning", buildJsonObject { put("effort", JsonPrimitive("none")) })
            when (request) {
                is AiRequest.Classification -> {
                    put("instructions", JsonPrimitive(CLASSIFICATION_INSTRUCTIONS))
                    put("input", JsonPrimitive(classificationPrompt(request.input)))
                    put("text", buildJsonObject { put("format", classificationStructuredOutputFormat()) })
                }

                is AiRequest.Summarization -> {
                    put("instructions", JsonPrimitive(SUMMARIZATION_INSTRUCTIONS))
                    put("input", JsonPrimitive(summarizationPrompt(request.input)))
                    put("text", buildJsonObject { put("format", summarizationStructuredOutputFormat()) })
                }
            }
        }.let { request ->
            json.encodeToString(JsonObject.serializer(), request)
                .toRequestBody(JSON_MEDIA_TYPE)
        }

    private fun classificationPrompt(input: AiClassificationInput): String = buildString {
        appendLine("Classify this email using only the allowed categories.")
        appendLine("Allowed categories: IMPORTANT, ACTION, INVOICE, ORDER, NEWSLETTER.")
        appendLine("Return every category that genuinely applies; return none when none applies.")
        appendLine("Sender: ${input.sender.orEmpty()}")
        appendLine("Subject: ${input.subject.orEmpty()}")
        appendLine("Preview: ${input.preview.orEmpty()}")
        input.fullMessage?.let { appendLine("Message: $it") }
        if (input.existingCategories.isNotEmpty()) {
            appendLine("Existing categories: ${input.existingCategories.joinToString()}")
        }
    }

    private fun summarizationPrompt(input: AiSummarizationInput): String = buildString {
        appendLine("Fasse diese E-Mail immer auf Deutsch in 2 bis 3 kurzen, sachlichen Sätzen zusammen.")
        appendLine("Priorisiere die wichtigsten Informationen. Erfinde nichts und füge keine Bewertung, Empfehlung oder internen Überlegungen hinzu.")
        appendLine("Verwende keine Markdown-Formatierung. Gib ausschließlich strukturiertes JSON zurück.")
        appendLine("Sender: ${input.sender.orEmpty()}")
        appendLine("Subject: ${input.subject.orEmpty()}")
        appendLine("Preview: ${input.preview.orEmpty()}")
        appendLine("Content: ${input.content.orEmpty()}")
    }

    private fun classificationStructuredOutputFormat() = buildJsonObject {
        put("type", JsonPrimitive("json_schema"))
        put("name", JsonPrimitive("mail_classification"))
        put("strict", JsonPrimitive(true))
        put("schema", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("properties", buildJsonObject {
                put("categories", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", buildJsonArray {
                            AiClassificationCategory.entries.forEach { add(JsonPrimitive(it.name)) }
                        })
                    })
                })
                put("confidence", buildJsonObject {
                    put("type", JsonPrimitive("number"))
                    put("minimum", JsonPrimitive(0.0))
                    put("maximum", JsonPrimitive(1.0))
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("categories"))
                add(JsonPrimitive("confidence"))
            })
        })
    }

    private fun summarizationStructuredOutputFormat() = buildJsonObject {
        put("type", JsonPrimitive("json_schema"))
        put("name", JsonPrimitive("mail_summary"))
        put("strict", JsonPrimitive(true))
        put("schema", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("properties", buildJsonObject {
                put("summary", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("minLength", JsonPrimitive(1))
                    put("maxLength", JsonPrimitive(MAX_SUMMARY_LENGTH))
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive("summary")) })
        })
    }

    private fun Response.toAiResult(modelId: AiModelId, request: AiRequest): AiResult {
        if (code == 401 || code == 403) return AiResult.Failure(AiError.Authentication)
        if (code == 429) return AiResult.Failure(AiError.RateLimited)
        if (!isSuccessful) return AiResult.Failure(AiError.Unknown)

        val responseBody = body?.string()?.takeIf { it.isNotBlank() }
            ?: return AiResult.Failure(AiError.InvalidResponse)
        return when (request) {
            is AiRequest.Classification -> parseClassification(responseBody, modelId)
            is AiRequest.Summarization -> parseSummarization(responseBody, modelId)
        }
    }

    private fun parseClassification(responseBody: String, modelId: AiModelId): AiResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val text = root.outputText() ?: return AiResult.Failure(AiError.InvalidResponse)
            val output = json.parseToJsonElement(text).jsonObject
            val categories = output["categories"]?.jsonArray
                ?.map { it.jsonPrimitive.contentOrNull ?: throw IllegalArgumentException() }
                ?.map { AiClassificationCategory.valueOf(it) }
                ?.toSet()
                ?: return AiResult.Failure(AiError.InvalidResponse)
            val confidence = output["confidence"]?.jsonPrimitive?.doubleOrNull
                ?.takeIf { it in 0.0..1.0 }
                ?: return AiResult.Failure(AiError.InvalidResponse)

            AiResult.Classification(
                output = AiClassificationResult(
                    categories = categories,
                    confidence = confidence,
                    metadata = AiResultMetadata(
                        providerId = id,
                        modelId = modelId,
                        completedAt = Clock.System.now(),
                    ),
                ),
            )
        } catch (_: Exception) {
            AiResult.Failure(AiError.InvalidResponse)
        }
    }

    private fun parseSummarization(responseBody: String, modelId: AiModelId): AiResult {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val text = root.outputText() ?: return AiResult.Failure(AiError.InvalidResponse)
            val summary = json.parseToJsonElement(text).jsonObject["summary"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() && it.length <= MAX_SUMMARY_LENGTH }
                ?: return AiResult.Failure(AiError.InvalidResponse)

            AiResult.Summarization(
                output = AiSummarizationResult(
                    summary = summary,
                    metadata = AiResultMetadata(
                        providerId = id,
                        modelId = modelId,
                        completedAt = Clock.System.now(),
                    ),
                ),
            )
        } catch (_: Exception) {
            AiResult.Failure(AiError.InvalidResponse)
        }
    }

    private fun JsonObject.outputText(): String? = get("output")
        ?.jsonArray
        ?.asSequence()
        ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "message" }
        ?.flatMap { item ->
            item.jsonObject["content"]?.jsonArray?.asSequence() ?: emptySequence()
        }
        ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
        ?.jsonObject
        ?.get("text")
        ?.jsonPrimitive
        ?.contentOrNull

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    private companion object {
        val DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses".toHttpUrl()
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val OPENAI_PROVIDER_ID = AiProviderId("openai")
        val DEFAULT_MODEL_ID = AiModelId("gpt-5.6-luna")
        const val CLASSIFICATION_INSTRUCTIONS =
            "Classify email. Use only the allowed categories. Return structured JSON only."
        const val SUMMARIZATION_INSTRUCTIONS =
            "Fasse die E-Mail immer auf Deutsch kurz und sachlich zusammen. Erfinde nichts. Gib ausschließlich strukturiertes JSON zurück."
        const val MAX_SUMMARY_LENGTH = 1_000
        val json = Json { ignoreUnknownKeys = true }
    }
}
