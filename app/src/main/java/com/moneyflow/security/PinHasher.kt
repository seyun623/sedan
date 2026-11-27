package com.moneyflow.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 hashing for the app PIN. Only the hash and salt are stored. */
object PinHasher {
  private const val ITERATIONS = 120_000
  private const val KEY_BITS = 256

  data class Hashed(val hash: String, val salt: String)

  fun hash(pin: String, salt: ByteArray = newSalt()): Hashed {
    val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
    val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return Hashed(Base64.encodeToString(bytes, Base64.NO_WRAP), Base64.encodeToString(salt, Base64.NO_WRAP))
  }

  fun verify(pin: String, hash: String, salt: String): Boolean {
    val candidate = hash(pin, Base64.decode(salt, Base64.NO_WRAP)).hash
    return MessageDigest.isEqual(candidate.toByteArray(), hash.toByteArray())
  }

  fun isValidPin(pin: String): Boolean = pin.length in 4..6 && pin.all { it.isDigit() }

  private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }
}
