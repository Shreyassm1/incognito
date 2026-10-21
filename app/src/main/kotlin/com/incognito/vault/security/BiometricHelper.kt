package com.incognito.vault.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricHelper(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "IncognitoVaultBiometricKey"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val BIOMETRIC_FILE = ".vault_biometric.dat"
    }

    private val biometricFile = File(context.filesDir, BIOMETRIC_FILE)

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricEnrolledAndSaved(): Boolean {
        return isBiometricAvailable() && biometricFile.exists() && biometricFile.length() > 28
    }

    fun saveMasterKeyForBiometrics(masterKey: ByteArray): Boolean {
        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv // 12 bytes
            val encryptedKey = cipher.doFinal(masterKey)

            FileOutputStream(biometricFile).use { fos ->
                fos.write(iv.size)
                fos.write(iv)
                fos.write(encryptedKey)
                fos.flush()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun promptBiometricUnlock(
        activity: FragmentActivity,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isBiometricEnrolledAndSaved()) {
            onError("Biometrics not available or not configured")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Incognito Vault")
            .setSubtitle("Confirm your identity with biometric authentication")
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        val decryptedMasterKey = decryptStoredMasterKey()
                        if (decryptedMasterKey != null) {
                            onSuccess(decryptedMasterKey)
                        } else {
                            onError("Failed to decrypt master key via biometric")
                        }
                    } catch (e: Exception) {
                        onError("Biometric decryption error: ${e.message}")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user clicked negative button, don't treat as an error toast
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric authentication failed. Try again.")
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun decryptStoredMasterKey(): ByteArray? {
        if (!biometricFile.exists()) return null

        FileInputStream(biometricFile).use { fis ->
            val ivSize = fis.read()
            if (ivSize <= 0 || ivSize > 32) return null
            val iv = ByteArray(ivSize)
            fis.read(iv)
            val encryptedBytes = fis.readBytes()

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            return cipher.doFinal(encryptedBytes)
        }
    }

    fun clearBiometricData() {
        try {
            if (biometricFile.exists()) {
                biometricFile.delete()
            }
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val key = keyStore.getKey(KEY_ALIAS, null)
            if (key is SecretKey) return key
        }

        val keyGenerator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
