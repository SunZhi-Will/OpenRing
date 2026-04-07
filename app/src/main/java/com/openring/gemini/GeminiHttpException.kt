package com.openring.gemini

/** Thrown when the Gemini REST API returns a non-success HTTP status (used for retry classification). */
class GeminiHttpException(
    val httpCode: Int,
    val responseBodyPreview: String?,
) : IllegalStateException("Gemini HTTP $httpCode: ${responseBodyPreview?.take(200) ?: "empty"}")
