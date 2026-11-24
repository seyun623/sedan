package com.moneyflow.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelPickerTest {
  @Test fun geminiPrefersStableFlash() {
    val models = listOf("gemini-embedding-001", "gemini-2.5-flash-image", "gemini-3-pro-preview", "gemini-3-flash", "gemini-3-flash-lite", "gemini-2.5-flash")
    assertEquals("gemini-3-flash", AiModelPicker.pick(AiProvider.GEMINI, models, exclude = "gemini-2.5-flash"))
    assertEquals("gemini-2.5-flash", AiModelPicker.pick(AiProvider.GEMINI, models, exclude = "gemini-1.5-flash"))
    assertNull(AiModelPicker.pick(AiProvider.GEMINI, listOf("gemini-embedding-001")))
  }

  @Test fun openRouterPrefersFreeTextModels() {
    val models = listOf("openai/gpt-5", "qwen/qwen2.5-vl-32b-instruct:free", "deepseek/deepseek-chat-v3:free", "x/other:free")
    assertEquals("deepseek/deepseek-chat-v3:free", AiModelPicker.pick(AiProvider.OPENROUTER, models, exclude = "meta-llama/llama-3.3-70b-instruct:free"))
  }

  @Test fun groqSkipsSpeechAndGuardModels() {
    val models = listOf("whisper-large-v3", "llama-guard-4-12b", "openai/gpt-oss-120b")
    assertEquals("openai/gpt-oss-120b", AiModelPicker.pick(AiProvider.GROQ, models, exclude = "llama-3.3-70b-versatile"))
  }
}
