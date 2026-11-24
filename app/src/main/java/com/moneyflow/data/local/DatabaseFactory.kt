package com.moneyflow.data.local

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

object DatabaseFactory {

  /** [passphrase] is an ASCII (hex) key; the same string is used for SQLCipher's KDF everywhere. */
  fun create(context: Context, passphrase: String, name: String = AppDatabase.NAME): AppDatabase {
    System.loadLibrary("sqlcipher")
    encryptPlaintextDatabaseIfNeeded(context, passphrase, name)
    return Room.databaseBuilder(context, AppDatabase::class.java, name)
      .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
      .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
      // v1 only ever existed in early AI Studio previews; nothing to migrate from it.
      .fallbackToDestructiveMigrationFrom(true, 1)
      .build()
  }

  /**
   * Builds before encryption stored the database in plaintext. Converts such a file in place
   * (sqlcipher_export into a new encrypted file, then swap) so existing data is kept.
   */
  private fun encryptPlaintextDatabaseIfNeeded(context: Context, passphrase: String, name: String) {
    val dbFile = context.getDatabasePath(name)
    if (!dbFile.exists() || !isPlaintextSqlite(dbFile)) return

    val tmp = File(dbFile.parentFile, "$name.encrypting")
    tmp.delete()
    val plain = SQLiteDatabase.openDatabase(dbFile.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE, null, null)
    try {
      val version = plain.version
      plain.rawExecSQL("ATTACH DATABASE '${tmp.absolutePath}' AS encrypted KEY '$passphrase'")
      plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
      plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
      plain.rawExecSQL("DETACH DATABASE encrypted")
    } finally {
      plain.close()
    }
    listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    if (!dbFile.delete() || !tmp.renameTo(dbFile)) {
      throw IllegalStateException("Could not replace plaintext database")
    }
  }

  internal fun isPlaintextSqlite(file: File): Boolean = runCatching {
    file.inputStream().use { input ->
      val header = ByteArray(16)
      input.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
    }
  }.getOrDefault(false)
}
