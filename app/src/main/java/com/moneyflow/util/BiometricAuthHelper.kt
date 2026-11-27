package com.moneyflow.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricStatus(val description: String, val isAvailable: Boolean) {
  READY("생체 인증 사용 가능", true),
  NOT_ENROLLED("등록된 생체 정보가 없습니다 (기기 설정 필요)", false),
  NO_HARDWARE("생체 인식 센서를 지원하지 않는 기기입니다", false),
  UNAVAILABLE("생체 인식 센서를 현재 사용할 수 없습니다", false),
  UNKNOWN("생체 인증 상태를 확인할 수 없습니다", false)
}

object BiometricAuthHelper {

  fun checkBiometricAvailability(context: Context): BiometricStatus {
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    return when (biometricManager.canAuthenticate(authenticators)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.READY
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
      else -> BiometricStatus.UNKNOWN
    }
  }

  fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String = "머니플로우 금융 보안 인증",
    subtitle: String = "지문 또는 얼굴 인식으로 잠금을 해제하세요",
    description: String = "자산 및 가계부 데이터를 안전하게 보호하고 있습니다",
    negativeButtonText: String = "간편 비밀번호(PIN) 입력",
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (errorCode: Int, errString: CharSequence) -> Unit,
    onFailed: () -> Unit
  ) {
    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        onSuccess(result)
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        onError(errorCode, errString)
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        onFailed()
      }
    }

    val biometricPrompt = BiometricPrompt(activity, executor, callback)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setDescription(description)
      .setNegativeButtonText(negativeButtonText)
      .setConfirmationRequired(false)
      .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
      .build()

    biometricPrompt.authenticate(promptInfo)
  }

  /** The phone has a screen lock (pattern, PIN, password; fingerprints and faces need one too). */
  fun isDeviceSecure(context: Context): Boolean =
    context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true

  /**
   * Asks for the phone's own lock: fingerprint / face when registered, otherwise (or by choice)
   * the phone's pattern, PIN or password. No app-specific secret is involved.
   */
  fun showDeviceLockPrompt(
    activity: FragmentActivity,
    title: String = "머니플로우 열기",
    subtitle: String = "휴대폰 잠금(지문·얼굴·패턴·PIN)으로 확인해요",
    onSuccess: () -> Unit,
    onError: (errorCode: Int, message: CharSequence) -> Unit,
    /**
     * Fingerprint first: only strong biometrics (fingerprint; most phones' face unlock is the weak
     * kind) plus the phone PIN/pattern. Android 11+; older phones keep the usual prompt.
     */
    fingerprintFirst: Boolean = false
  ) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode, errString)
    }
    val strongOnly = fingerprintFirst && android.os.Build.VERSION.SDK_INT >= 30
    val info = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setConfirmationRequired(false)
      // WEAK + DEVICE_CREDENTIAL works on every supported Android version; no negative button allowed.
      .setAllowedAuthenticators(
        (if (strongOnly) BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.BIOMETRIC_WEAK) or
          BiometricManager.Authenticators.DEVICE_CREDENTIAL
      )
      .build()
    try {
      BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    } catch (e: Exception) {
      onError(BiometricPrompt.ERROR_HW_UNAVAILABLE, "휴대폰 잠금 확인을 열 수 없어요.")
    }
  }
}
