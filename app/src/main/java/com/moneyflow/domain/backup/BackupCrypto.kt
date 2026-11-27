package com.moneyflow.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("비밀번호가 맞지 않거나 파일이 손상되었어요.")
class NotABackupFileException : Exception("머니플로우 백업 파일이 아니에요.")

/**
 * Backup file format (all binary):
 *   "MFBK" magic (4) | format version (1) | salt (16) | iv (12) | AES-256-GCM(gzip(json))
 * The key is derived from the user's backup password with PBKDF2-HMAC-SHA256.
 * The header is authenticated as GCM associated data, so any change to the file is detected.
 */
object BackupCrypto {
  private val MAGIC = byteArrayOf('M'.code.toByte(), 'F'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
  private const val VERSION: Byte = 1
  private const val ITERATIONS = 210_000
  private const val HEADER = 4 + 1 + 16 + 12

  fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val header = MAGIC + byteArrayOf(VERSION) + salt + iv
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
    cipher.updateAAD(header)
    return header + cipher.doFinal(gzip(plain))
  }

  fun decrypt(file: ByteArray, password: CharArray): ByteArray {
    if (!isBackupFile(file)) throw NotABackupFileException()
    if (file[4] != VERSION) throw NotABackupFileException()
    val header = file.copyOfRange(0, HEADER)
    val salt = file.copyOfRange(5, 21)
    val iv = file.copyOfRange(21, 33)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
    cipher.updateAAD(header)
    val compressed = try {
      cipher.doFinal(file, HEADER, file.size - HEADER)
    } catch (e: AEADBadTagException) {
      throw WrongPasswordException()
    }
    return gunzip(compressed)
  }

  fun isBackupFile(file: ByteArray): Boolean =
    file.size > HEADER + 16 && file.copyOfRange(0, 4).contentEquals(MAGIC)

  private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
    try {
      val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
      return SecretKeySpec(bytes, "AES")
    } finally {
      spec.clearPassword()
    }
  }

  private fun gzip(data: ByteArray): ByteArray =
    ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

  private fun gunzip(data: ByteArray): ByteArray =
    GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
