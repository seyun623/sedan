package com.moneyflow

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.local.DatabaseFactory
import com.moneyflow.data.model.AccountEntity
import com.moneyflow.data.model.AccountType
import com.moneyflow.security.DatabaseKeyManager
import com.moneyflow.security.KeystoreSecretStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real device / emulator: SQLCipher, the Android Keystore and file encryption. */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val names = listOf("it-encrypted.db", "it-plain.db")

  @After fun cleanUp() {
    names.forEach { context.deleteDatabase(it) }
  }

  private fun account(name: String) = AccountEntity(name = name, bankName = "테스트은행", accountType = AccountType.BANK, balance = 12_345)

  @Test fun keystorePassphraseIsStableAndHex() {
    val a = DatabaseKeyManager(context).getOrCreatePassphraseHex()
    val b = DatabaseKeyManager(context).getOrCreatePassphraseHex()
    assertEquals(a, b)
    assertEquals(64, a.length)
    assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test fun databaseFileIsEncryptedAndReopens() = runBlocking {
    val key = DatabaseKeyManager(context).getOrCreatePassphraseHex()
    val db = DatabaseFactory.create(context, key, "it-encrypted.db")
    db.accountDao().insert(account("암호화 통장"))
    db.close()
    val file = context.getDatabasePath("it-encrypted.db")
    assertTrue(file.exists())
    assertFalse("must not be a plaintext SQLite file", DatabaseFactory.isPlaintextSqlite(file))
    val text = file.readBytes().toString(Charsets.ISO_8859_1)
    assertFalse(text.contains("테스트은행".toByteArray().toString(Charsets.ISO_8859_1)))

    val reopened = DatabaseFactory.create(context, key, "it-encrypted.db")
    assertEquals("암호화 통장", reopened.accountDao().getAll().single().name)
    reopened.close()
  }

  @Test fun plaintextDatabaseIsConvertedKeepingData() = runBlocking {
    val plain = Room.databaseBuilder(context, AppDatabase::class.java, "it-plain.db").build()
    plain.accountDao().insert(account("예전 통장"))
    plain.close()
    assertTrue(DatabaseFactory.isPlaintextSqlite(context.getDatabasePath("it-plain.db")))

    val key = DatabaseKeyManager(context).getOrCreatePassphraseHex()
    val db = DatabaseFactory.create(context, key, "it-plain.db")
    assertEquals("예전 통장", db.accountDao().getAll().single().name)
    db.close()
    assertFalse(DatabaseFactory.isPlaintextSqlite(context.getDatabasePath("it-plain.db")))
  }

  @Test fun keystoreSecretStoreRoundTrip() {
    val store = KeystoreSecretStore()
    val sealed = store.encrypt("AIza-test-key")
    assertNotEquals("AIza-test-key", sealed)
    assertFalse(sealed.contains("AIza"))
    assertEquals("AIza-test-key", store.decrypt(sealed))
  }
}
