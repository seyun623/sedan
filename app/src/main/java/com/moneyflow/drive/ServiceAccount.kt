package com.moneyflow.drive

import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class DriveKeyException(message: String) : Exception(message)

/** The bot (Google service account) key file the user imports once. */
data class ServiceAccountKey(val clientEmail: String, val privateKeyPem: String, val tokenUri: String, val projectId: String) {
  /** Never prints the private key (logs, error messages). */
  override fun toString(): String = "ServiceAccountKey(clientEmail=$clientEmail, projectId=$projectId, privateKey=***)"

  companion object {
    const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    const val CLOCK_SKEW_SECONDS = 60L

    /** Reads a key JSON downloaded from Google Cloud ("서비스 계정 → 키 → JSON"). */
    fun parse(json: String): ServiceAccountKey {
      val o = try { JSONObject(json) } catch (e: Exception) { throw DriveKeyException("열쇠 파일(JSON)이 아니에요.") }
      if (o.optString("type") != "service_account") throw DriveKeyException("서비스 계정 열쇠 파일이 아니에요. 안내서 4단계의 JSON 파일을 골라 주세요.")
      val email = o.optString("client_email")
      val key = o.optString("private_key")
      if (!email.contains("@") || !key.contains("PRIVATE KEY")) throw DriveKeyException("열쇠 파일에 필요한 정보가 없어요.")
      val tokenUri = o.optString("token_uri").takeIf { it.startsWith("https://") } ?: DEFAULT_TOKEN_URI
      return ServiceAccountKey(email, key, tokenUri, o.optString("project_id"))
    }

    internal fun base64Url(bytes: ByteArray): String =
      android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
  }

  fun privateKey(): PrivateKey {
    val body = privateKeyPem
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replace("\\n", "")
      .replace(Regex("\\s"), "")
    val der = try { android.util.Base64.decode(body, android.util.Base64.DEFAULT) } catch (e: Exception) { throw DriveKeyException("열쇠를 읽을 수 없어요.") }
    return try {
      KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    } catch (e: Exception) {
      throw DriveKeyException("열쇠를 읽을 수 없어요.")
    }
  }

  /** Signed JWT asking Google for a 1-hour access token ("JWT bearer" grant). */
  fun assertion(nowSeconds: Long, scope: String = DRIVE_SCOPE): String {
    val header = JSONObject().put("alg", "RS256").put("typ", "JWT").toString()
    val claims = JSONObject()
      .put("iss", clientEmail)
      .put("scope", scope)
      .put("aud", tokenUri)
      // A little in the past: Google rejects tokens "issued in the future" when the phone clock runs fast.
      .put("iat", nowSeconds - CLOCK_SKEW_SECONDS)
      .put("exp", nowSeconds - CLOCK_SKEW_SECONDS + 3600)
      .toString()
    val signingInput = base64Url(header.toByteArray()) + "." + base64Url(claims.toByteArray())
    val signature = Signature.getInstance("SHA256withRSA").run {
      initSign(privateKey())
      update(signingInput.toByteArray())
      sign()
    }
    return signingInput + "." + base64Url(signature)
  }
}
