package com.moneyflow

import com.moneyflow.ai.AiBusyException
import com.moneyflow.ai.AiModelPicker
import com.moneyflow.ai.AiProvider
import com.moneyflow.ai.AiService
import com.moneyflow.ai.GeminiClient
import com.moneyflow.ai.HttpTransport
import com.moneyflow.ai.OpenAiCompatClient
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 3.1: 예비 AI — Gemini over its limit → Groq answers. */
@RunWith(RobolectricTestRunner::class)
class V310BackupAiTest {
  @Test fun keysAreRecognised() {
    assertEquals(AiProvider.GEMINI, AiModelPicker.providerOfKey("AIzaSyXXXX"))
    assertEquals(AiProvider.GROQ, AiModelPicker.providerOfKey(" gsk_abc "))
    assertEquals(AiProvider.OPENROUTER, AiModelPicker.providerOfKey("sk-or-v1-abc"))
    assertNull(AiModelPicker.providerOfKey("hello"))
  }

  private suspend fun setUp(backup: Boolean) = testSettingsRepository().apply {
    setAiApiKeyEncrypted(PlainSecretStore.encrypt("GEMINI"))
    setAiModel("gemini-2.5-flash")
    setAiEnabled(true)
    setAiKeyFor(AiProvider.GROQ, PlainSecretStore.encrypt("gsk_x"), "llama-3.3-70b-versatile")
    setAiBackupEnabled(backup)
  }

  private val geminiBusy = HttpTransport { _, _, _, _ -> 429 to """{"error":{"message":"quota"}}""" }
  private val groqOk = HttpTransport { _, url, _, _ ->
    if (url.endsWith("/models")) 200 to """{"data":[]}"""
    else 200 to JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", "{\"ok\":1}")))).toString()
  }

  @Test fun groqAnswersWhenGeminiIsOverItsLimit() = runBlocking {
    AiService.BUSY_WAITS_MS = listOf(0L)
    val ai = AiService(setUp(backup = true), PlainSecretStore, GeminiClient().apply { transport = geminiBusy }, OpenAiCompatClient().apply { transport = groqOk })
    assertEquals("{\"ok\":1}", ai.completeJson("p", JSONObject(), "{}"))
    // Web search can't go to Groq: null, so the benefit search answers from what the AI knows.
    assertNull(ai.searchWeb("p"))
    AiService.BUSY_WAITS_MS = listOf(0L, 2_000L, 6_000L)
  }

  @Test fun offMeansNoBackupAndPhotosStayWithGemini() = runBlocking {
    AiService.BUSY_WAITS_MS = listOf(0L)
    val off = AiService(setUp(backup = false), PlainSecretStore, GeminiClient().apply { transport = geminiBusy }, OpenAiCompatClient().apply { transport = groqOk })
    val e = runCatching { off.completeJson("p", JSONObject(), "{}") }.exceptionOrNull()
    assertTrue(e is AiBusyException && e.message!!.contains("예비 AI"))
    val on = AiService(setUp(backup = true), PlainSecretStore, GeminiClient().apply { transport = geminiBusy }, OpenAiCompatClient().apply { transport = groqOk })
    assertTrue(runCatching { on.completeJson("p", JSONObject(), "{}", "image/jpeg" to "AAAA") }.exceptionOrNull() is AiBusyException)
    AiService.BUSY_WAITS_MS = listOf(0L, 2_000L, 6_000L)
  }
}
