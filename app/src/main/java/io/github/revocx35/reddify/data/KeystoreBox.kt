package io.github.revocx35.reddify.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One small secret on disk, encrypted with a non-exportable AES-256-GCM key in the Android Keystore (file in
 * noBackupFilesDir; app backups are off). A file that cannot be decrypted is deleted. Never throws.
 */
class KeystoreBox(context: Context, fileName: String, private val alias: String) {
    private val file = File(context.noBackupFilesDir, fileName)

    fun read(): String? = runCatching {
        if (!file.exists()) return null
        val data = file.readBytes()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        String(cipher.doFinal(data, 12, data.size - 12))
    }.getOrElse { file.delete(); null }

    fun write(value: String?) {
        runCatching {
            if (value == null) {
                file.delete()
                return
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(cipher.iv + cipher.doFinal(value.toByteArray()))
            tmp.renameTo(file)
        }.onFailure { file.delete() }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
