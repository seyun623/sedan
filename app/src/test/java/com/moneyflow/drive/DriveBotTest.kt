package com.moneyflow.drive

import com.moneyflow.data.repository.DriveRepository
import com.moneyflow.testing.FakeClock
import com.moneyflow.testing.PlainSecretStore
import com.moneyflow.testing.testSettingsRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class)
class DriveBotTest {
  private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
  private val pem = "-----BEGIN PRIVATE KEY-----\n" +
    android.util.Base64.encodeToString(keyPair.private.encoded, android.util.Base64.DEFAULT) + "-----END PRIVATE KEY-----\n"
  private val keyJson = JSONObject()
    .put("type", "service_account").put("project_id", "moneyflow-family")
    .put("private_key", pem).put("client_email", "moneyflow-bot@moneyflow-family.iam.gserviceaccount.com")
    .put("token_uri", "https://oauth2.googleapis.com/token").toString()

  /** A tiny fake of Google's token endpoint and Drive API. */
  private class FakeGoogle : DriveTransport {
    val calls = mutableListOf<Pair<String, String>>()
    val files = mutableMapOf("f1" to "moneyflow drive test 1".toByteArray())
    var shared = true
    var editor = true
    override fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
      calls += method to url
      fun json(s: String) = 200 to s.toByteArray()
      return when {
        url.startsWith("https://oauth2.googleapis.com/token") -> json("""{"access_token":"tok","expires_in":3600}""")
        headers["Authorization"] != "Bearer tok" -> 401 to "{}".toByteArray()
        url.contains("/drive/v3/files?") && url.contains("folder%27+and") ->
          json(if (shared) """{"files":[{"id":"folder1","name":"머니플로우 가족","mimeType":"application/vnd.google-apps.folder","owners":[{"displayName":"황세윤"}]}]}""" else """{"files":[]}""")
        url.contains("/drive/v3/files?") ->
          json("""{"files":[{"id":"f1","name":"moneyflow-drive-test.txt","mimeType":"text/plain","modifiedTime":"2026-11-24T10:00:00Z","owners":[{"displayName":"황세윤"}]}]}""")
        url.contains("/files/folder1?") -> json("""{"id":"folder1"}""")
        method == "GET" && url.contains("/files/f1?alt=media") -> 200 to files.getValue("f1")
        method == "PATCH" && url.contains("/upload/drive/v3/files/f1") ->
          if (editor) { files["f1"] = body!!; json("{}") }
          else 403 to """{"error":{"message":"The user does not have sufficient permissions for this file.","errors":[{"reason":"insufficientFilePermissions"}]}}""".toByteArray()
        else -> 404 to "{}".toByteArray()
      }
    }
  }

  @Test fun parsesKeyAndSignsAValidJwt() {
    val key = ServiceAccountKey.parse(keyJson)
    assertEquals("moneyflow-bot@moneyflow-family.iam.gserviceaccount.com", key.clientEmail)
    val jwt = key.assertion(1_700_000_000)
    val (h, c, sig) = jwt.split(".")
    val claims = JSONObject(String(android.util.Base64.decode(c, android.util.Base64.URL_SAFE)))
    assertEquals(key.clientEmail, claims.getString("iss"))
    assertEquals(ServiceAccountKey.DRIVE_SCOPE, claims.getString("scope"))
    assertEquals(1_700_003_540L, claims.getLong("exp"))
    assertTrue(String(android.util.Base64.decode(h, android.util.Base64.URL_SAFE)).contains("RS256"))
    val ok = Signature.getInstance("SHA256withRSA").run {
      initVerify(keyPair.public); update("$h.$c".toByteArray()); verify(android.util.Base64.decode(sig, android.util.Base64.URL_SAFE))
    }
    assertTrue(ok)
  }

  @Test fun rejectsWrongFiles() {
    listOf("not json", """{"type":"authorized_user"}""", """{"type":"service_account","client_email":"x"}""").forEach {
      try { ServiceAccountKey.parse(it); fail(it) } catch (e: DriveKeyException) { }
    }
  }

  @Test fun connectionCheckPassesAndExplainsFailures() = runBlocking {
    val settings = testSettingsRepository()
    val google = FakeGoogle()
    val repo = DriveRepository(settings, PlainSecretStore, FakeClock())
    repo.clientFactory = { DriveClient(it, google) }

    assertEquals("moneyflow-bot@moneyflow-family.iam.gserviceaccount.com", repo.importKey(keyJson))
    // No folder chosen yet.
    assertEquals(listOf(true, false), repo.runCheck().map { it.ok })
    val folder = repo.sharedFolders().single()
    repo.chooseFolder(folder)
    settings.setDriveTestFile("moneyflow-drive-test.txt")

    val checks = repo.runCheck()
    assertEquals(checks.toString(), listOf(true, true, true, true), checks.map { it.ok })
    assertTrue(google.files.getValue("f1").toString(Charsets.UTF_8).startsWith("updated-by-bot"))
    assertTrue(google.calls.any { it.first == "PATCH" })

    google.editor = false
    val denied = repo.runCheck()
    assertFalse(denied.last().ok)
    assertTrue(denied.last().detail.contains("편집자"))
  }

  @Test fun driveErrorsAreExplained() {
    assertTrue(DriveClient.errorMessage(403, """{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"message":"x"}}""").contains("새 파일"))
    assertTrue(DriveClient.errorMessage(403, """{"error":{"errors":[{"reason":"accessNotConfigured"}],"message":"Drive API has not been used"}}""").contains("Drive API"))
    assertTrue(DriveClient.errorMessage(404, "{}").contains("공유"))
  }
}
