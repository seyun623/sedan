package com.moneyflow.ai

/** Chooses a sensible text model from a provider's model list (when the chosen one disappeared). */
object AiModelPicker {
  /** Models that can't answer text prompts as JSON. */
  private val UNSUITABLE = Regex("embed|image|imagen|tts|audio|speech|whisper|vision-only|guard|moderation|live|veo|aqa|learnlm|transcrib|rerank", RegexOption.IGNORE_CASE)

  fun pick(provider: AiProvider, models: List<String>, exclude: String? = null): String? {
    val usable = models.filter { it != exclude && !UNSUITABLE.containsMatchIn(it) }
    if (usable.isEmpty()) return null
    val preferences: List<(String) -> Boolean> = when (provider) {
      AiProvider.GEMINI -> listOf(
        { it == provider.defaultModel },
        { it == "gemini-flash-latest" },
        { it.contains("flash") && !it.contains("lite") && !it.contains("preview") && !it.contains("exp") },
        { it.contains("flash") && !it.contains("preview") && !it.contains("exp") },
        { it.contains("flash") },
        { it.contains("pro") && !it.contains("preview") }
      )
      AiProvider.OPENROUTER -> listOf(
        { it == provider.defaultModel },
        { it.endsWith(":free") && Regex("llama|qwen|gemma|deepseek|mistral|gpt-oss", RegexOption.IGNORE_CASE).containsMatchIn(it) && !it.contains("vl") },
        { it.endsWith(":free") }
      )
      AiProvider.GROQ -> listOf(
        { it == provider.defaultModel },
        { it.contains("llama") && it.contains("70b") },
        { it.contains("gpt-oss") || it.contains("qwen") || it.contains("llama") }
      )
      AiProvider.CUSTOM -> emptyList()
    }
    return preferences.firstNotNullOfOrNull { p -> usable.firstOrNull(p) } ?: usable.first()
  }
}
