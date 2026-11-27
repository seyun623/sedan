package com.moneyflow.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides the SQLCipher passphrase for the local database.
 *
 * A random 32-byte passphrase is generated once and stored wrapped (AES-GCM) by a
 * non-exportable Android Keystore key, so the raw passphrase never touches disk.
 */
class DatabaseKeyManager(private val context: Context) {

  /** Hex string form of the passphrase (ASCII-safe for SQLCipher's ATTACH ... KEY). */
  fun getOrCreatePassphraseHex(): String =
    getOrCreatePassphrase().joinToString("") { "%02x".format(it) }

  private fun getOrCreatePassphrase(): ByteArray {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_WRAPPED, null)
    val iv = prefs.getString(KEY_IV, null)
    if (stored != null && iv != null) {
      return unwrap(Base64.decode(stored, Base64.NO_WRAP), Base64.decode(iv, Base64.NO_WRAP))
    }
    val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
    val wrapped = cipher.doFinal(passphrase)
    prefs.edit()
      .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
      .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
      .commit()
    return passphrase
  }

  private fun unwrap(wrapped: ByteArray, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
    return cipher.doFinal(wrapped)
  }

  private fun keystoreKey(): SecretKey {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "moneyflow_db_key"
    const val PREFS = "moneyflow_db_key"
    const val KEY_WRAPPED = "wrapped_passphrase"
    const val KEY_IV = "iv"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
