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
      .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
      // v1 only ever existed in early AI Studio previews; nothing to migrate from it.
      .fallbackToDestructiveMigrationFrom(true, 1)
      .build()
  }

  /**
   * Builds before encryption stored the database in plaintext. Converts such a file in place:
   * every table, row, index and the schema version are copied into a new encrypted file, which
   * then replaces the old one, so existing data is kept.
   */
  private fun encryptPlaintextDatabaseIfNeeded(context: Context, passphrase: String, name: String) {
    val dbFile = context.getDatabasePath(name)
    val tmp = File(dbFile.parentFile, "$name.encrypting")
    // A conversion by an older version stopped between deleting the old file and renaming the new one.
    if (!dbFile.exists() && tmp.exists() && tmp.length() > 0 && !isPlaintextSqlite(tmp)) {
      if (!tmp.renameTo(dbFile)) throw IllegalStateException("Could not recover the converted database")
      return
    }
    if (!dbFile.exists() || !isPlaintextSqlite(dbFile)) return

    listOf("", "-wal", "-shm", "-journal").forEach { File(tmp.path + it).delete() }
    val plain = android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
    try {
      val encrypted = SQLiteDatabase.openDatabase(
        tmp.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null
      )
      try {
        copyAll(plain, encrypted)
      } finally {
        encrypted.close()
      }
    } finally {
      plain.close()
    }
    listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
    // rename() replaces the old file in one step: there is never a moment without a database.
    if (!tmp.renameTo(dbFile)) {
      throw IllegalStateException("Could not replace plaintext database")
    }
  }

  private fun copyAll(from: android.database.sqlite.SQLiteDatabase, to: SQLiteDatabase) {
    data class Obj(val type: String, val name: String, val sql: String)
    val objects = mutableListOf<Obj>()
    from.rawQuery(
      "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
      null
    ).use { c -> while (c.moveToNext()) objects += Obj(c.getString(0), c.getString(1), c.getString(2)) }
    val tables = objects.filter { it.type == "table" }
    to.beginTransaction()
    try {
      tables.forEach { to.execSQL(it.sql) }
      tables.forEach { t -> copyRows(from, to, t.name) }
      // AUTOINCREMENT counters, so new ids never reuse old ones.
      val hasSequence = from.rawQuery("SELECT 1 FROM sqlite_master WHERE name = 'sqlite_sequence'", null).use { it.moveToFirst() }
      if (hasSequence) {
        to.execSQL("DELETE FROM sqlite_sequence")
        copyRows(from, to, "sqlite_sequence")
      }
      objects.filter { it.type != "table" }.forEach { to.execSQL(it.sql) }
      to.version = from.version
      to.setTransactionSuccessful()
    } finally {
      to.endTransaction()
    }
  }

  private fun copyRows(from: android.database.sqlite.SQLiteDatabase, to: SQLiteDatabase, table: String) {
    from.rawQuery("SELECT * FROM `$table`", null).use { c ->
      while (c.moveToNext()) {
        val values = android.content.ContentValues()
        for (i in 0 until c.columnCount) {
          val col = "`" + c.getColumnName(i) + "`"
          when (c.getType(i)) {
            android.database.Cursor.FIELD_TYPE_NULL -> values.putNull(col)
            android.database.Cursor.FIELD_TYPE_INTEGER -> values.put(col, c.getLong(i))
            android.database.Cursor.FIELD_TYPE_FLOAT -> values.put(col, c.getDouble(i))
            android.database.Cursor.FIELD_TYPE_BLOB -> values.put(col, c.getBlob(i))
            else -> values.put(col, c.getString(i))
          }
        }
        to.insertOrThrow("`$table`", null, values)
      }
    }
  }

  internal fun isPlaintextSqlite(file: File): Boolean = runCatching {
    file.inputStream().use { input ->
      val header = ByteArray(16)
      input.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
    }
  }.getOrDefault(false)
}
