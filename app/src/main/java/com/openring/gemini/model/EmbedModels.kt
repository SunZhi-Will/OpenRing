package com.openring.gemini.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmbedContentRequest(
    val model: String,
    val content: Content,
    val taskType: String? = null,
)

@Serializable
data class EmbedContentResponse(
    val embedding: ContentEmbedding? = null,
)

@Serializable
data class ContentEmbedding(
    val values: List<Double> = emptyList(),
)
