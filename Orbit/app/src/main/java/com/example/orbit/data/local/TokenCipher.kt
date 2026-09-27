package com.example.orbit.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "orbit_token_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"

/** GCM koristi IV od 12 bajtova i oznaku od 128 bitova */
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/**
 * F-13: sifruje JWT pre upisa u SharedPreferences.
 * Kljuc nastaje u Android Keystore-u i ne moze da se procita iz njega; aplikacija
 * samo trazi od Keystore-a da sifruje ili desifruje, pa u fajlu stoji samo sifrat.
 */
class TokenCipher {

    /** IV i sifrat zajedno, kao Base64 tekst za SharedPreferences */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // IV bira Keystore, nov za svako sifrovanje
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /**
     * null kad sifrat ne moze da se otvori, na primer kad je fajl vracen iz rezervne
     * kopije na drugi telefon, gde kljuc ne postoji; korisnik se tada ponovo prijavljuje.
     */
    fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
        )
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        Log.w(TAG, "Stored token cannot be decrypted", e)
        null
    } catch (e: IllegalArgumentException) {
        // Base64 nije ispravan
        Log.w(TAG, "Stored token is not valid Base64", e)
        null
    }

    /** Postojeci kljuc, ili nov pri prvom pokretanju */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "TokenCipher"
    }
}
