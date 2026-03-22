package com.openring.gemini.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val tools: List<Tool>? = null,
    @SerialName("systemInstruction")
    val systemInstruction: Content? = null
)

@Serializable
data class Tool(
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject
)

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class InlineData(
    @SerialName("mimeType")
    val mimeType: String,
    val data: String
)

@Serializable
data class Part(
    val text: String? = null,
    @SerialName("functionCall")
    val functionCall: FunctionCall? = null,
    @SerialName("functionResponse")
    val functionResponse: FunctionResponse? = null,
    @SerialName("thoughtSignature")
    val thoughtSignature: String? = null,
    @SerialName("inlineData")
    val inlineData: InlineData? = null
)

@Serializable
data class FunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val id: String? = null
)

@Serializable
data class FunctionResponse(
    val name: String,
    val response: JsonObject,
    val id: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
) {
    fun firstText(): String? =
        candidates.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull { it.text != null }
            ?.text

    fun functionCalls(): List<FunctionCall> =
        candidates.firstOrNull()
            ?.content
            ?.parts
            ?.mapNotNull { it.functionCall }
            ?: emptyList()
}

@Serializable
data class Candidate(
    val content: Content? = null
)

