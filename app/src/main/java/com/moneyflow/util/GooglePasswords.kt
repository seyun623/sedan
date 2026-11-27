package com.moneyflow.util

import android.app.Activity
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException

/**
 * Saves the family / backup passwords in the user's Google Password Manager and reads them back,
 * so a forgotten password can be found at passwords.google.com or filled in on a new phone.
 * Everything here is optional: when Google's password manager isn't available the app works as
 * before and the caller only gets a result to show.
 */
object GooglePasswords {
  const val FAMILY = "머니플로우 가족 비밀번호"
  const val BACKUP = "머니플로우 백업 비밀번호"

  sealed interface Result {
    data class Found(val name: String, val password: String) : Result
    data object Saved : Result
    data object Cancelled : Result
    data object NoneSaved : Result
    data class Unavailable(val reason: String) : Result
  }

  /** Shows Google's "save password?" sheet for [name] (FAMILY or BACKUP). */
  suspend fun save(activity: Activity, name: String, password: String): Result = try {
    CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(id = name, password = password))
    Result.Saved
  } catch (e: CreateCredentialCancellationException) {
    Result.Cancelled
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: Exception) {
    ProblemLog.record("구글 비밀번호 저장", e)
    Result.Unavailable("이 휴대폰에서는 구글 비밀번호 관리자에 저장할 수 없어요.")
  }

  /** Lets the user pick one of the passwords saved for this app. */
  suspend fun pick(activity: Activity): Result = try {
    val response = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(GetPasswordOption())))
    when (val c = response.credential) {
      is PasswordCredential -> Result.Found(c.id, c.password)
      else -> Result.NoneSaved
    }
  } catch (e: NoCredentialException) {
    Result.NoneSaved
  } catch (e: GetCredentialCancellationException) {
    Result.Cancelled
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: Exception) {
    ProblemLog.record("구글 비밀번호 불러오기", e)
    Result.Unavailable("이 휴대폰에서는 구글 비밀번호 관리자를 쓸 수 없어요.")
  }
}
