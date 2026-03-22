package com.openring.ui.screens

import androidx.annotation.DrawableRes
import com.openring.R
import com.openring.localmodel.LocalModelCatalog

enum class ModelProvider(val displayName: String, val apiKeyUrl: String) {
    LOCAL("On-device", ""),
    GEMINI("Gemini", "https://aistudio.google.com/app/apikey"),
    OPENAI("OpenAI", "https://platform.openai.com/api-keys"),
    ANTHROPIC("Anthropic", "https://console.anthropic.com/settings/keys"),
    MISTRAL("Mistral", "https://console.mistral.ai/api-keys/"),
    DEEPSEEK("DeepSeek", "https://platform.deepseek.com/apikeys"),
    GROQ("Groq", "https://console.groq.com/keys"),
    XAI("xAI (Grok)", "https://console.x.ai/"),
    COHERE("Cohere", "https://dashboard.cohere.com/api-keys"),
}

data class KnownModel(
    val provider: ModelProvider,
    val label: String,
    val model: String
)

@DrawableRes
fun drawableResForProvider(provider: ModelProvider): Int = when (provider) {
    ModelProvider.LOCAL -> R.drawable.ic_provider_local
    ModelProvider.GEMINI -> R.drawable.ic_provider_gemini
    ModelProvider.OPENAI -> R.drawable.ic_provider_openai
    ModelProvider.ANTHROPIC -> R.drawable.ic_provider_anthropic
    ModelProvider.MISTRAL -> R.drawable.ic_provider_generic
    ModelProvider.DEEPSEEK -> R.drawable.ic_provider_generic
    ModelProvider.GROQ -> R.drawable.ic_provider_generic
    ModelProvider.XAI -> R.drawable.ic_provider_generic
    ModelProvider.COHERE -> R.drawable.ic_provider_generic
}

fun providerFromString(provider: String): ModelProvider = when (provider.lowercase()) {
    "local" -> ModelProvider.LOCAL
    "openai" -> ModelProvider.OPENAI
    "anthropic" -> ModelProvider.ANTHROPIC
    "mistral" -> ModelProvider.MISTRAL
    "deepseek" -> ModelProvider.DEEPSEEK
    "groq" -> ModelProvider.GROQ
    "xai" -> ModelProvider.XAI
    "cohere" -> ModelProvider.COHERE
    else -> ModelProvider.GEMINI
}

val KNOWN_MODELS = listOf(
    KnownModel(ModelProvider.GEMINI, "3.1 Pro (Preview)", "gemini-3.1-pro-preview"),
    KnownModel(ModelProvider.GEMINI, "3 Flash (Preview)", "gemini-3-flash-preview"),
    KnownModel(ModelProvider.GEMINI, "3.1 Flash-Lite (Preview)", "gemini-3.1-flash-lite-preview"),
    KnownModel(ModelProvider.GEMINI, "2.5 Pro", "gemini-2.5-pro"),
    KnownModel(ModelProvider.GEMINI, "2.5 Flash", "gemini-2.5-flash"),
    KnownModel(ModelProvider.GEMINI, "2.5 Flash-Lite", "gemini-2.5-flash-lite"),
    KnownModel(ModelProvider.OPENAI, "GPT-5.4", "gpt-5.4"),
    KnownModel(ModelProvider.OPENAI, "GPT-5.4 mini", "gpt-5.4-mini"),
    KnownModel(ModelProvider.OPENAI, "GPT-4.1", "gpt-4.1"),
    KnownModel(ModelProvider.OPENAI, "GPT-4.1 mini", "gpt-4.1-mini"),
    KnownModel(ModelProvider.ANTHROPIC, "Claude Opus 4.6", "claude-opus-4-6"),
    KnownModel(ModelProvider.ANTHROPIC, "Claude Sonnet 4.6", "claude-sonnet-4-6"),
    KnownModel(ModelProvider.ANTHROPIC, "Claude Haiku 4.5", "claude-haiku-4-5"),
    KnownModel(ModelProvider.MISTRAL, "Mistral Large 2.1", "mistral-large-2411"),
    KnownModel(ModelProvider.MISTRAL, "Pixtral Large", "pixtral-large-2411"),
    KnownModel(ModelProvider.MISTRAL, "Mistral Medium 3.1", "mistral-medium-2508"),
    KnownModel(ModelProvider.MISTRAL, "Codestral", "codestral-2508"),
    KnownModel(ModelProvider.DEEPSEEK, "DeepSeek Chat (V3)", "deepseek-chat"),
    KnownModel(ModelProvider.DEEPSEEK, "DeepSeek Reasoner", "deepseek-reasoner"),
    KnownModel(ModelProvider.GROQ, "Llama 3.3 70B", "llama-3.3-70b-versatile"),
    KnownModel(ModelProvider.GROQ, "Mixtral 8x7B", "mixtral-8x7b-32768"),
    KnownModel(ModelProvider.XAI, "Grok 2", "grok-2-1212"),
    KnownModel(ModelProvider.COHERE, "Command A", "command-a-03-2025")
)

/** On-device catalog entries as [KnownModel] rows (model id = catalog id). */
val KNOWN_LOCAL_MODELS: List<KnownModel> = LocalModelCatalog.ENTRIES.map { e ->
    KnownModel(ModelProvider.LOCAL, e.label, e.id)
}
