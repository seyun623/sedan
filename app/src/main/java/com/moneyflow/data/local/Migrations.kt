package com.moneyflow.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.time.YearMonth

/**
 * v2 -> v3
 * - transactions: source / dedupKey / recurringId / goalId columns + indices
 *   (existing auto-registered and recurring rows are tagged from their memo text)
 * - budgets: one row per month (keeps the newest duplicate) + unique index;
 *   per-category limits move from a JSON column to the category_budgets table
 * - recurring_transactions: startMonth column
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // --- transactions ---
    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'")
    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `dedupKey` TEXT")
    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurringId` INTEGER")
    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `goalId` INTEGER")
    db.execSQL("UPDATE `transactions` SET `source` = 'SMS' WHERE `memo` LIKE '%SMS 자동등록%'")
    db.execSQL("UPDATE `transactions` SET `source` = 'NOTIFICATION' WHERE `source` = 'MANUAL' AND `memo` LIKE '%자동등록%'")
    db.execSQL("UPDATE `transactions` SET `source` = 'RECURRING' WHERE `memo` LIKE '[정기]%'")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_toAccountId` ON `transactions` (`toAccountId`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_dedupKey` ON `transactions` (`dedupKey`)")

    // --- budgets ---
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `category_budgets` (`monthKey` TEXT NOT NULL, `category` TEXT NOT NULL, " +
        "`limitAmount` INTEGER NOT NULL, PRIMARY KEY(`monthKey`, `category`))"
    )
    db.query(
      "SELECT monthKey, categoryBudgetsJson FROM budgets WHERE id IN (SELECT MAX(id) FROM budgets GROUP BY monthKey)"
    ).use { c ->
      while (c.moveToNext()) {
        val month = c.getString(0)
        val json = runCatching { JSONObject(c.getString(1) ?: "{}") }.getOrNull() ?: continue
        json.keys().forEach { category ->
          val limit = json.optLong(category)
          if (limit > 0) {
            db.execSQL(
              "INSERT OR REPLACE INTO category_budgets(monthKey, category, limitAmount) VALUES (?, ?, ?)",
              arrayOf<Any>(month, category, limit)
            )
          }
        }
      }
    }
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `budgets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`monthKey` TEXT NOT NULL, `totalBudget` INTEGER NOT NULL)"
    )
    db.execSQL(
      "INSERT INTO budgets_new(id, monthKey, totalBudget) SELECT id, monthKey, totalBudget FROM budgets " +
        "WHERE id IN (SELECT MAX(id) FROM budgets GROUP BY monthKey)"
    )
    db.execSQL("DROP TABLE budgets")
    db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_monthKey` ON `budgets` (`monthKey`)")

    // --- recurring ---
    db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `startMonth` TEXT NOT NULL DEFAULT ''")
    val now = YearMonth.now()
    val currentKey = "%04d-%02d".format(now.year, now.monthValue)
    db.execSQL(
      "UPDATE `recurring_transactions` SET `startMonth` = COALESCE(`lastExecutedMonth`, ?)",
      arrayOf<Any>(currentKey)
    )
  }
}

/** v3 -> v4: card spending-goal settings and card benefits (new tables only). */
val MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `card_profiles` (`accountId` INTEGER NOT NULL, `performanceTarget` INTEGER NOT NULL, " +
        "`cycleStartDay` INTEGER NOT NULL, `excludedCategories` TEXT NOT NULL, `excludedKeywords` TEXT NOT NULL, PRIMARY KEY(`accountId`))"
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `card_benefits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, " +
        "`title` TEXT NOT NULL, `category` TEXT, `merchantKeywords` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` INTEGER NOT NULL, " +
        "`monthlyCap` INTEGER NOT NULL, `minPerformance` INTEGER NOT NULL, `minAmount` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_benefits_accountId` ON `card_benefits` (`accountId`)")
  }
}

/** v4 -> v5: user category / ignore rules for auto-captured and imported payments. */
val MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `category_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`keyword` TEXT NOT NULL, `action` TEXT NOT NULL, `category` TEXT NOT NULL)"
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_rules_keyword` ON `category_rules` (`keyword`)")
  }
}

/** v5 -> v6: family members' records loaded from their backup files (combined view). */
val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `family_members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`deviceId` TEXT NOT NULL, `backupCreatedAt` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL)"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_members_deviceId` ON `family_members` (`deviceId`)")
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `family_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberId` INTEGER NOT NULL, " +
        "`type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `memo` TEXT NOT NULL, " +
        "`isExcludedFromBudget` INTEGER NOT NULL, " +
        "FOREIGN KEY(`memberId`) REFERENCES `family_members`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_transactions_memberId` ON `family_transactions` (`memberId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_transactions_timestamp` ON `family_transactions` (`timestamp`)")
  }
}

/** v6 -> v7: family members' saving goals (shared goals in the combined view). */
val MIGRATION_6_7 = object : Migration(6, 7) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `family_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberId` INTEGER NOT NULL, " +
        "`title` TEXT NOT NULL, `targetAmount` INTEGER NOT NULL, `currentAmount` INTEGER NOT NULL, `targetDate` INTEGER NOT NULL, " +
        "FOREIGN KEY(`memberId`) REFERENCES `family_members`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_goals_memberId` ON `family_goals` (`memberId`)")
  }
}
