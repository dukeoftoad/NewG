package com.toadgallery.app.data.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {
  private const val ITERATIONS = 10_000
  private const val SALT_BYTES = 16

  fun generateSalt(): String {
    val bytes = ByteArray(SALT_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  fun hashPin(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    var hash = "$salt:$pin".toByteArray(StandardCharsets.UTF_8)
    repeat(ITERATIONS) {
      digest.reset()
      digest.update(salt.toByteArray(StandardCharsets.UTF_8))
      hash = digest.digest(hash)
    }
    return Base64.encodeToString(hash, Base64.NO_WRAP)
  }

  fun verifyPin(enteredPin: String, salt: String, expectedHash: String): Boolean {
    if (enteredPin.isBlank() || salt.isBlank() || expectedHash.isBlank()) return false
    val candidate = hashPin(enteredPin, salt)
    return MessageDigest.isEqual(
      candidate.toByteArray(StandardCharsets.UTF_8),
      expectedHash.toByteArray(StandardCharsets.UTF_8),
    )
  }
}
