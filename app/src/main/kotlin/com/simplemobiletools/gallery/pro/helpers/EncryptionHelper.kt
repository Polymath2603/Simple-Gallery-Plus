package com.simplemobiletools.gallery.pro.helpers

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    // Header markers for our secret-based format: 16-byte salt + 12-byte GCM IV precede ciphertext.
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 10000

    // ---- Keystore-backed (legacy / fallback) ----

    fun encryptFile(context: Context, inputFile: File, outputFile: File) {
        val encryptedFile = EncryptedFile.Builder(
            outputFile,
            context,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        FileInputStream(inputFile).use { inputStream ->
            encryptedFile.openFileOutput().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    fun decryptFile(context: Context, inputFile: File, outputFile: File) {
        val encryptedFile = EncryptedFile.Builder(
            inputFile,
            context,
            masterKeyAlias,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileInput().use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    // ---- Secret (pattern/pin hash) backed ----

    /**
     * Encrypt [inputFile] into [outputFile] using [secret] (the user's lock pattern/pin hash) as key material.
     * Format: [16-byte salt][12-byte GCM IV][AES-GCM ciphertext].
     */
    fun encryptFile(context: Context, inputFile: File, outputFile: File, secret: String) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveSecretKey(secret, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        FileOutputStream(outputFile).use { fos ->
            fos.write(salt)
            fos.write(cipher.iv)
            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(inputFile).use { it.copyTo(cos) }
            }
        }
    }

    /**
     * Decrypt [inputFile] using [secret]. Falls back to the legacy Keystore format if the
     * secret-based attempt fails (e.g. files encrypted before this scheme existed), so existing
     * .enc files remain readable.
     */
    fun decryptFile(context: Context, inputFile: File, outputFile: File, secret: String) {
        try {
            val salt = ByteArray(SALT_LENGTH)
            val iv = ByteArray(IV_LENGTH)
            FileInputStream(inputFile).use { fis ->
                val readSalt = fis.read(salt)
                val readIv = fis.read(iv)
                if (readSalt != SALT_LENGTH || readIv != IV_LENGTH) {
                    throw Exception("Bad encrypted header")
                }
                val key = deriveSecretKey(secret, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(outputFile).use { cis.copyTo(it) }
                }
            }
        } catch (e: Exception) {
            // Fallback to the legacy Keystore-backed decryption.
            decryptFile(context, inputFile, outputFile)
        }
    }

    private fun deriveSecretKey(secret: String, salt: ByteArray): javax.crypto.SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
