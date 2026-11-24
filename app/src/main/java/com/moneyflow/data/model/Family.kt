package com.moneyflow.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A family member whose backup file was loaded for the combined view (read-only copy). */
@Entity(tableName = "family_members", indices = [Index(value = ["deviceId"])])
data class FamilyMemberEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  /** Install id stored in the backup (same phone → the member is updated instead of added). */
  val deviceId: String,
  /** When the loaded backup file was made. */
  val backupCreatedAt: Long,
  val importedAt: Long
)

/** Income / expense of a family member, copied from their backup. Transfers are not kept. */
@Entity(
  tableName = "family_transactions",
  foreignKeys = [ForeignKey(entity = FamilyMemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index(value = ["memberId"]), Index(value = ["timestamp"])]
)
data class FamilyTransactionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val memberId: Long,
  val type: TransactionType,
  val amount: Long,
  val category: String,
  val timestamp: Long,
  val memo: String,
  val isExcludedFromBudget: Boolean = false
)

/** A family member's saving goal, copied from their backup (for shared goals). */
@Entity(
  tableName = "family_goals",
  foreignKeys = [ForeignKey(entity = FamilyMemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index(value = ["memberId"])]
)
data class FamilyGoalEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val memberId: Long,
  val title: String,
  val targetAmount: Long,
  val currentAmount: Long,
  val targetDate: Long
)
