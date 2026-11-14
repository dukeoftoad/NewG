package com.example.data.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
  AVAILABLE,
  NOT_ENROLLED,
  UNAVAILABLE,
}

object BiometricAuthHelper {

  private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.BIOMETRIC_WEAK

  fun checkAvailability(context: Context): BiometricAvailability {
    val manager = BiometricManager.from(context)
    return when (manager.canAuthenticate(AUTHENTICATORS)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
      else -> BiometricAvailability.UNAVAILABLE
    }
  }

  fun authenticate(
    context: Context,
    title: String = "Unlock Hidden Vault",
    subtitle: String = "Confirm your identity to decrypt private media",
    onSuccess: () -> Unit,
    onFallbackPin: (String) -> Unit,
  ) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
      onFallbackPin("Biometric prompt requires an active window; enter PIN")
      return
    }
    if (checkAvailability(activity) != BiometricAvailability.AVAILABLE) {
      onFallbackPin("No biometrics enrolled on this device; enter your 4-digit PIN")
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt =
      BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
              errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
              onFallbackPin(errString.toString())
            }
          }
        },
      )

    val info =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(AUTHENTICATORS)
        .setNegativeButtonText("Use PIN")
        .build()

    prompt.authenticate(info)
  }

  private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
      is FragmentActivity -> this
      is ContextWrapper -> baseContext.findFragmentActivity()
      else -> null
    }
}
