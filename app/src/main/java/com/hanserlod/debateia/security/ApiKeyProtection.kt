package com.hanserlod.debateia.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clase para proteger la API key mediante encriptación y ofuscación
 * ADVERTENCIA: Esta es una capa de seguridad, pero ningún método es 100% seguro del lado del cliente
 */
object ApiKeyProtection {
    
    // API Key encriptada (NO almacenar la key real aquí)
    // Esta se genera con el método encryptApiKey()
    private const val ENCRYPTED_KEY = "TU_KEY_ENCRIPTADA_AQUI"
    
    // Vector de inicialización (cambiar por uno único)
    private const val IV = "RandomIV16Bytes!"
    
    // Salt ofuscado (dividido para dificultar búsqueda)
    private val SALT_PARTS = arrayOf(
        "deb",
        "ate",
        "ia_",
        "sec",
        "ret",
        "_20",
        "25"
    )
    
    // Hash esperado de la firma de la app (dejar vacío en desarrollo)
    // En producción, agregar el hash SHA-256 de tu firma de release
    private const val EXPECTED_SIGNATURE_HASH = ""
    
    // Contexto de la app (se debe inicializar)
    private var appContext: Context? = null
    
    /**
     * Inicializa el contexto de la aplicación
     * Llamar desde Application.onCreate()
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
    
    /**
     * Obtiene la API key desencriptada
     * Implementa varias capas de ofuscación
     */
    fun getApiKey(): String {
        return try {
            // Verificar integridad básica de la app
            if (!isAppIntegrityValid()) {
                return "" // App modificada
            }
            
            // Desencriptar la key
            decryptApiKey(ENCRYPTED_KEY)
        } catch (e: Exception) {
            // Si falla, devolver vacío (no exponer el error)
            ""
        }
    }
    
    /**
     * Encripta la API key (usar esto SOLO para generar ENCRYPTED_KEY inicial)
     * No incluir en la versión final de producción
     */
    @Suppress("unused")
    private fun encryptApiKey(plainKey: String): String {
        val salt = SALT_PARTS.joinToString("")
        val key = generateKey(salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(IV.toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val encrypted = cipher.doFinal(plainKey.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    
    /**
     * Desencripta la API key
     */
    private fun decryptApiKey(encryptedKey: String): String {
        if (encryptedKey == "TU_KEY_ENCRIPTADA_AQUI") {
            // La key aún no se ha configurado
            return ""
        }
        
        val salt = SALT_PARTS.joinToString("")
        val key = generateKey(salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(IV.toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        val decrypted = cipher.doFinal(Base64.decode(encryptedKey, Base64.NO_WRAP))
        return String(decrypted)
    }
    
    /**
     * Genera una clave de encriptación basada en el salt
     */
    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return SecretKeySpec(hash.copyOf(16), "AES")
    }
    
    /**
     * Verifica la integridad básica de la app
     * Detecta si la app ha sido modificada
     */
    private fun isAppIntegrityValid(): Boolean {
        try {
            // 1. Verificar firma de la app (solo en producción)
            if (EXPECTED_SIGNATURE_HASH.isNotEmpty() && appContext != null) {
                if (!verifyAppSignature()) {
                    return false // Firma no coincide
                }
            }
            
            // 2. Detección básica de root (opcional)
            if (isDeviceRooted()) {
                // En producción podrías bloquear, por ahora solo advertir
                // return false
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Verifica que la firma de la app coincida con la esperada
     */
    private fun verifyAppSignature(): Boolean {
        val context = appContext ?: return false
        
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: emptyArray()
            }
            
            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                val signatureHash = Base64.encodeToString(
                    md.digest(signature.toByteArray()),
                    Base64.NO_WRAP
                )
                
                // En desarrollo, imprimir el hash (eliminar en producción)
                // Log.d("ApiKeyProtection", "Signature Hash: $signatureHash")
                
                if (EXPECTED_SIGNATURE_HASH.isEmpty() || signatureHash == EXPECTED_SIGNATURE_HASH) {
                    return true
                }
            }
        } catch (e: Exception) {
            return false
        }
        
        return false
    }
    
    /**
     * Detección básica de dispositivo rooteado
     */
    private fun isDeviceRooted(): Boolean {
        // Método 1: Buscar binario 'su'
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        for (path in paths) {
            if (java.io.File(path).exists()) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Verifica si la API key está configurada
     */
    fun isApiKeyConfigured(): Boolean {
        return ENCRYPTED_KEY != "TU_KEY_ENCRIPTADA_AQUI" && ENCRYPTED_KEY.isNotEmpty()
    }
}
