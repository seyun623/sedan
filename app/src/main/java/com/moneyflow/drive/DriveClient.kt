package com.moneyflow.drive

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP with byte bodies (Drive files are binary). */
fun interface DriveTransport {
  fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray>
}

object UrlConnectionDriveTransport : DriveTransport {
  override fun execute(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      // HttpURLConnection has no PATCH: use the override header Google APIs accept.
      if (method == "PATCH") {
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
      } else {
        conn.requestMethod = method
      }
      conn.connectTimeout = 20_000
      conn.readTimeout = 60_000
      headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
      if (body != null) {
        conn.doOutput = true
        conn.outputStream.use { it.write(body) }
      }
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      return code to (stream?.use { it.readBytes() } ?: ByteArray(0))
    } finally {
      conn.disconnect()
    }
  }
}

class DriveException(message: String, val code: Int = 0) : Exception(message)

data class DriveFile(val id: String, val name: String, val modifiedTime: String, val ownerName: String?, val isFolder: Boolean)

/** Google Drive v3 calls made as the bot. It only sees what was shared with the bot's email. */
class DriveClient(
  private val key: ServiceAccountKey,
  private val transport: DriveTransport = UrlConnectionDriveTransport,
  private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) {
  private var token: String? = null
  private var tokenExpires = 0L

  /** An access token (cached for its lifetime). */
  fun accessToken(): String {
    token?.let { if (nowSeconds() < tokenExpires - 60) return it }
    val body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + enc(key.assertion(nowSeconds()))
    val (code, bytes) = transport.execute("POST", key.tokenUri, mapOf("Content-Type" to "application/x-www-form-urlencoded"), body.toByteArray())
    val text = bytes.toString(Charsets.UTF_8)
    if (code !in 200..299) {
      val err = runCatching { JSONObject(text).optString("error_description").ifEmpty { JSONObject(text).optString("error") } }.getOrDefault("")
      throw DriveException(
        when {
          err.contains("invalid_grant", true) || err.contains("Invalid JWT", true) ->
            "봇 열쇠를 쓸 수 없어요. 열쇠가 삭제됐거나 휴대폰 시간이 맞지 않아요. (날짜·시간 자동 설정 확인)"
          err.contains("account not found", true) || err.contains("invalid_client", true) -> "봇 계정을 찾을 수 없어요. 열쇠 파일을 다시 받아 주세요."
          else -> "구글 로그인에 실패했어요 ($code) $err".trim()
        },
        code
      )
    }
    val o = JSONObject(text)
    token = o.getString("access_token")
    tokenExpires = nowSeconds() + o.optLong("expires_in", 3600)
    return token!!
  }

  /** Folders shared with the bot. */
  fun sharedFolders(): List<DriveFile> =
    list("mimeType='application/vnd.google-apps.folder' and trashed=false")

  fun filesIn(folderId: String): List<DriveFile> =
    list("'${folderId.replace("'", "")}' in parents and trashed=false and mimeType!='application/vnd.google-apps.folder'")

  fun download(fileId: String): ByteArray = call("GET", "$API/files/${enc(fileId)}?alt=media&supportsAllDrives=true", null, null)

  /** Replaces a file's content; the file stays owned by whoever created it. */
  fun update(fileId: String, content: ByteArray) {
    call("PATCH", "$UPLOAD/files/${enc(fileId)}?uploadType=media&supportsAllDrives=true", content, "application/octet-stream")
  }

  private fun list(query: String): List<DriveFile> {
    val out = mutableListOf<DriveFile>()
    var pageToken: String? = null
    do {
      val url = "$API/files?q=${enc(query)}&pageSize=200&supportsAllDrives=true&includeItemsFromAllDrives=true" +
        "&fields=${enc("nextPageToken,files(id,name,mimeType,modifiedTime,owners(displayName))")}" +
        (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
      val o = JSONObject(call("GET", url, null, null).toString(Charsets.UTF_8))
      val files = o.optJSONArray("files")
      if (files != null) for (i in 0 until files.length()) {
        val f = files.getJSONObject(i)
        out += DriveFile(
          id = f.getString("id"),
          name = f.optString("name"),
          modifiedTime = f.optString("modifiedTime"),
          ownerName = f.optJSONArray("owners")?.optJSONObject(0)?.optString("displayName")?.takeIf { it.isNotBlank() },
          isFolder = f.optString("mimeType") == "application/vnd.google-apps.folder"
        )
      }
      pageToken = o.optString("nextPageToken").takeIf { it.isNotBlank() }
    } while (pageToken != null)
    return out
  }

  private fun call(method: String, url: String, body: ByteArray?, contentType: String?): ByteArray {
    val headers = buildMap {
      put("Authorization", "Bearer ${accessToken()}")
      contentType?.let { put("Content-Type", it) }
    }
    val (code, bytes) = transport.execute(method, url, headers, body)
    if (code !in 200..299) throw DriveException(errorMessage(code, bytes.toString(Charsets.UTF_8)), code)
    return bytes
  }

  companion object {
    const val API = "https://www.googleapis.com/drive/v3"
    const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    internal fun errorMessage(code: Int, body: String): String {
      val o = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
      val reason = o?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
      val message = o?.optString("message").orEmpty()
      return when {
        reason == "storageQuotaExceeded" -> "봇은 새 파일을 만들 수 없어요. 파일은 각자 앱에서 먼저 만들어 주세요."
        reason == "accessNotConfigured" || message.contains("has not been used", true) || message.contains("disabled", true) ->
          "구글 클라우드에서 Google Drive API가 꺼져 있어요. 안내서 2단계를 확인해 주세요."
        code == 404 -> "파일이나 폴더를 찾을 수 없어요. 봇 이메일에 폴더를 공유했는지 확인해 주세요."
        code == 403 && (reason == "insufficientFilePermissions" || message.contains("permission", true)) ->
          "봇에게 편집 권한이 없어요. 폴더 공유에서 봇 이메일을 '편집자'로 바꿔 주세요."
        code == 403 -> "드라이브가 요청을 거부했어요. ($message)"
        code == 401 -> "봇 로그인이 거부됐어요. 열쇠 파일을 다시 불러와 주세요."
        code == 429 || code >= 500 -> "구글 드라이브가 바빠요. 잠시 후 다시 시도해 주세요."
        else -> "드라이브 요청에 실패했어요 ($code) $message".trim()
      }
    }
  }
}
