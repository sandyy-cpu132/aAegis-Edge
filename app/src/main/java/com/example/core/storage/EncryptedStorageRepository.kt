package com.example.core.storage

import android.content.Context
import com.example.core.model.AppConfiguration
import com.example.core.model.AutofillProfile
import com.example.core.security.KeystoreCryptoManager
import com.example.core.security.SecureLogger
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Centralized encrypted local storage repository.
 *
 * Security properties:
 * - Stores files exclusively inside `context.noBackupFilesDir` (private internal app sandbox,
 *   automatically excluded from Android Auto Backup).
 * - Encrypts both `config.enc` and `autofill_profiles.enc` with Android Keystore AES-256-GCM
 *   and distinct Associated Authenticated Data (AAD) tags so files cannot be swapped or tampered with.
 * - Zeroes plaintext byte buffers immediately after read/write operations.
 */
class EncryptedStorageRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    val cryptoManager = KeystoreCryptoManager(appContext)

    private val storageDir: File = File(appContext.noBackupFilesDir, "aegis_vault").apply {
        if (!exists()) mkdirs()
    }
    private val configFile = File(storageDir, "aegis_config_v1.enc")
    private val autofillFile = File(storageDir, "aegis_autofill_v1.enc")

    private val configAad = "AEGIS_CONFIG_V1_AAD".toByteArray(StandardCharsets.UTF_8)
    private val autofillAad = "AEGIS_AUTOFILL_V1_AAD".toByteArray(StandardCharsets.UTF_8)

    private val _configFlow = MutableStateFlow(loadConfigurationInternal())
    val configFlow: StateFlow<AppConfiguration> = _configFlow.asStateFlow()

    private val _profilesFlow = MutableStateFlow(loadProfilesInternal())
    val profilesFlow: StateFlow<List<AutofillProfile>> = _profilesFlow.asStateFlow()

    @Synchronized
    fun updateConfiguration(transform: (AppConfiguration) -> AppConfiguration) {
        val updated = transform(_configFlow.value)
        saveConfigurationInternal(updated)
        _configFlow.value = updated
    }

    @Synchronized
    fun saveProfile(profile: AutofillProfile) {
        val current = _profilesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveProfilesInternal(current)
        _profilesFlow.value = current
        SecureLogger.info("EncryptedStorage", "Saved encrypted autofill profile")
    }

    @Synchronized
    fun deleteProfile(profileId: String) {
        val updated = _profilesFlow.value.filterNot { it.id == profileId }
        saveProfilesInternal(updated)
        _profilesFlow.value = updated
        SecureLogger.info("EncryptedStorage", "Deleted autofill profile")
    }

    @Synchronized
    fun wipeAllLocalData() {
        try {
            if (configFile.exists()) {
                overwriteWithZerosAndDelete(configFile)
            }
            if (autofillFile.exists()) {
                overwriteWithZerosAndDelete(autofillFile)
            }
            cryptoManager.deleteMasterKey()
        } catch (e: Exception) {
            SecureLogger.warn("EncryptedStorage", "Error during secure wipe")
        }
        val freshConfig = AppConfiguration()
        saveConfigurationInternal(freshConfig)
        saveProfilesInternal(emptyList())
        _configFlow.value = freshConfig
        _profilesFlow.value = emptyList()
        SecureLogger.info("EncryptedStorage", "Completed cryptographic wipe and key rotation")
    }

    private fun overwriteWithZerosAndDelete(file: File) {
        try {
            val len = file.length().toInt().coerceIn(0, 256 * 1024)
            if (len > 0) {
                file.writeBytes(ByteArray(len))
            }
        } catch (_: Exception) {
        } finally {
            file.delete()
        }
    }

    private fun loadConfigurationInternal(): AppConfiguration {
        if (!configFile.exists()) {
            val defaultCfg = AppConfiguration()
            saveConfigurationInternal(defaultCfg)
            return defaultCfg
        }
        var decryptedBytes: ByteArray? = null
        return try {
            val encrypted = configFile.readBytes()
            decryptedBytes = cryptoManager.decrypt(encrypted, configAad)
            val jsonString = String(decryptedBytes, StandardCharsets.UTF_8)
            AppConfiguration.fromJson(JSONObject(jsonString))
        } catch (e: Exception) {
            SecureLogger.warn("EncryptedStorage", "Config integrity check failed; resetting to safe defaults")
            AppConfiguration()
        } finally {
            decryptedBytes?.fill(0)
        }
    }

    private fun saveConfigurationInternal(config: AppConfiguration) {
        var plaintextBytes: ByteArray? = null
        try {
            plaintextBytes = config.toJson().toString().toByteArray(StandardCharsets.UTF_8)
            val encrypted = cryptoManager.encrypt(plaintextBytes, configAad)
            val tmpFile = File(storageDir, "aegis_config_v1.tmp")
            tmpFile.writeBytes(encrypted)
            tmpFile.renameTo(configFile)
        } catch (e: Exception) {
            SecureLogger.warn("EncryptedStorage", "Failed to write encrypted config")
        } finally {
            plaintextBytes?.fill(0)
        }
    }

    private fun loadProfilesInternal(): List<AutofillProfile> {
        if (!autofillFile.exists()) {
            val seed = listOf(
                AutofillProfile(
                    id = "default_personal",
                    profileName = "Primary Personal",
                    firstName = "",
                    lastName = "",
                    email = "",
                    phone = "",
                    streetAddress = "",
                    postalCode = "",
                    customFields = mapOf("landmark" to "", "upi_handle" to ""),
                    requireBiometricForFill = true
                )
            )
            saveProfilesInternal(seed)
            return seed
        }
        var decryptedBytes: ByteArray? = null
        return try {
            val encrypted = autofillFile.readBytes()
            decryptedBytes = cryptoManager.decrypt(encrypted, autofillAad)
            val arr = JSONArray(String(decryptedBytes, StandardCharsets.UTF_8))
            val list = mutableListOf<AutofillProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) list.add(AutofillProfile.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            SecureLogger.warn("EncryptedStorage", "Autofill vault integrity check failed; returning empty list")
            emptyList()
        } finally {
            decryptedBytes?.fill(0)
        }
    }

    private fun saveProfilesInternal(profiles: List<AutofillProfile>) {
        var plaintextBytes: ByteArray? = null
        try {
            val arr = JSONArray()
            profiles.forEach { arr.put(it.toJson()) }
            plaintextBytes = arr.toString().toByteArray(StandardCharsets.UTF_8)
            val encrypted = cryptoManager.encrypt(plaintextBytes, autofillAad)
            val tmpFile = File(storageDir, "aegis_autofill_v1.tmp")
            tmpFile.writeBytes(encrypted)
            tmpFile.renameTo(autofillFile)
        } catch (e: Exception) {
            SecureLogger.warn("EncryptedStorage", "Failed to write encrypted autofill vault")
        } finally {
            plaintextBytes?.fill(0)
        }
    }

    companion object {
        @Volatile
        private var instance: EncryptedStorageRepository? = null

        fun getInstance(context: Context): EncryptedStorageRepository {
            return instance ?: synchronized(this) {
                instance ?: EncryptedStorageRepository(context).also { instance = it }
            }
        }
    }
}
