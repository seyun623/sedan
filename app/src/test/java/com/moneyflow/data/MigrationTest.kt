package com.moneyflow.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.moneyflow.data.local.AppDatabase
import com.moneyflow.data.local.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  @Test fun migrate2To3_keepsDataAndNormalizesBudgets() {
    helper.createDatabase(DB, 2).apply {
      execSQL("INSERT INTO accounts(id,name,bankName,accountType,accountNumber,balance,colorHex,isFavorite,isDebt) VALUES(1,'통장','토스뱅크','BANK','',1000,0,0,0)")
      execSQL("INSERT INTO transactions(id,type,amount,category,accountId,accountName,toAccountId,toAccountName,timestamp,memo,paymentMethod,isExcludedFromBudget) VALUES(1,'EXPENSE',500,'식비',1,'통장',NULL,NULL,1000,'스타벅스 (신한카드 SMS 자동등록)','체크카드',0)")
      execSQL("INSERT INTO transactions(id,type,amount,category,accountId,accountName,toAccountId,toAccountName,timestamp,memo,paymentMethod,isExcludedFromBudget) VALUES(2,'EXPENSE',700,'주거/통신',1,'통장',NULL,NULL,2000,'[정기] 월세','계좌이체',0)")
      // duplicate budget rows for the same month (old bug)
      execSQL("INSERT INTO budgets(id,monthKey,totalBudget,categoryBudgetsJson) VALUES(1,'2026-10',1000,'{\"식비\":100}')")
      execSQL("INSERT INTO budgets(id,monthKey,totalBudget,categoryBudgetsJson) VALUES(2,'2026-10',2000,'{\"식비\":300,\"쇼핑\":50}')")
      execSQL("INSERT INTO recurring_transactions(id,title,type,amount,category,dayOfMonth,accountId,accountName,memo,paymentMethod,isActive,lastExecutedMonth) VALUES(1,'월세','EXPENSE',700,'주거/통신',25,1,'통장','','계좌이체',1,'2026-10')")
      close()
    }
    val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)

    db.query("SELECT source FROM transactions ORDER BY id").use { c ->
      c.moveToNext(); assertEquals("SMS", c.getString(0))
      c.moveToNext(); assertEquals("RECURRING", c.getString(0))
    }
    db.query("SELECT totalBudget FROM budgets WHERE monthKey='2026-10'").use { c ->
      assertEquals(1, c.count); c.moveToNext(); assertEquals(2000, c.getLong(0))
    }
    db.query("SELECT category, limitAmount FROM category_budgets ORDER BY category").use { c ->
      assertEquals(2, c.count)
    }
    db.query("SELECT startMonth FROM recurring_transactions").use { c ->
      c.moveToNext(); assertEquals("2026-10", c.getString(0))
    }
  }

  private companion object { const val DB = "migration-test" }
}
