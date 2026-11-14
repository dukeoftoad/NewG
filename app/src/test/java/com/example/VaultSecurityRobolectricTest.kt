package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.PinHasher
import com.example.data.security.VaultCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultSecurityRobolectricTest {

  @Test
  fun `salted SHA-256 PIN verification succeeds only for matching PIN`() {
    val salt = PinHasher.generateSalt()
    val hash = PinHasher.hashPin("4829", salt)

    assertNotEquals("4829", hash)
    assertTrue(PinHasher.verifyPin("4829", salt, hash))
    assertFalse(PinHasher.verifyPin("1234", salt, hash))
    assertFalse(PinHasher.verifyPin("", salt, hash))
  }

  @Test
  fun `vault crypto manager encrypts and decrypts media bytes round-trip`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = VaultCryptoManager(context)
    val payload = "toadGALLERY_private_media_payload".encodeToByteArray()

    val encryptedPath = manager.encryptMediaBytes(itemId = 999L, plainBytes = payload)
    val decrypted = manager.decryptMediaBytes(encryptedPath)

    assertNotNull(decrypted)
    assertArrayEquals(payload, decrypted)

    manager.deleteEncryptedFile(encryptedPath)
  }
}
