package com.hanserlod.debateia.security

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Sistema de límites de uso para proteger contra abuso de la API
 */
object UsageLimiter {
    
    private const val PREFS_NAME = "api_usage_limits"
    private const val KEY_DAILY_COUNT = "daily_count"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"
    private const val KEY_TOTAL_COUNT = "total_count"
    
    // Límites configurables
    private const val MAX_REQUESTS_PER_DAY = 100 // Máximo de requests por día
    private const val MAX_REQUESTS_PER_SESSION = 50 // Máximo en una sesión continua
    
    private var sessionCount = 0
    private var prefs: SharedPreferences? = null
    
    /**
     * Inicializa el limitador
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        resetDailyCountIfNeeded()
    }
    
    /**
     * Verifica si se puede hacer una nueva petición
     * Retorna true si está permitido, false si se alcanzó el límite
     */
    fun canMakeRequest(): Boolean {
        val prefs = prefs ?: return false
        
        resetDailyCountIfNeeded()
        
        val dailyCount = prefs.getInt(KEY_DAILY_COUNT, 0)
        
        // Verificar límite diario
        if (dailyCount >= MAX_REQUESTS_PER_DAY) {
            return false
        }
        
        // Verificar límite de sesión
        if (sessionCount >= MAX_REQUESTS_PER_SESSION) {
            return false
        }
        
        return true
    }
    
    /**
     * Registra que se hizo una petición
     */
    fun recordRequest() {
        val prefs = prefs ?: return
        
        resetDailyCountIfNeeded()
        
        val dailyCount = prefs.getInt(KEY_DAILY_COUNT, 0)
        val totalCount = prefs.getInt(KEY_TOTAL_COUNT, 0)
        
        prefs.edit()
            .putInt(KEY_DAILY_COUNT, dailyCount + 1)
            .putInt(KEY_TOTAL_COUNT, totalCount + 1)
            .apply()
        
        sessionCount++
    }
    
    /**
     * Obtiene el número de peticiones restantes hoy
     */
    fun getRemainingRequests(): Int {
        val prefs = prefs ?: return 0
        resetDailyCountIfNeeded()
        
        val dailyCount = prefs.getInt(KEY_DAILY_COUNT, 0)
        val remaining = MAX_REQUESTS_PER_DAY - dailyCount
        
        return maxOf(0, remaining)
    }
    
    /**
     * Obtiene estadísticas de uso
     */
    fun getUsageStats(): UsageStats {
        val prefs = prefs ?: return UsageStats(0, 0, 0, 0)
        
        resetDailyCountIfNeeded()
        
        val dailyCount = prefs.getInt(KEY_DAILY_COUNT, 0)
        val totalCount = prefs.getInt(KEY_TOTAL_COUNT, 0)
        val sessionRemaining = MAX_REQUESTS_PER_SESSION - sessionCount
        val dailyRemaining = MAX_REQUESTS_PER_DAY - dailyCount
        
        return UsageStats(
            requestsToday = dailyCount,
            requestsThisSession = sessionCount,
            remainingToday = maxOf(0, dailyRemaining),
            remainingThisSession = maxOf(0, sessionRemaining)
        )
    }
    
    /**
     * Resetea el contador diario si es un nuevo día
     */
    private fun resetDailyCountIfNeeded() {
        val prefs = prefs ?: return
        
        val today = getCurrentDateString()
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        
        if (today != lastResetDate) {
            // Es un nuevo día, resetear contador
            prefs.edit()
                .putInt(KEY_DAILY_COUNT, 0)
                .putString(KEY_LAST_RESET_DATE, today)
                .apply()
        }
    }
    
    /**
     * Obtiene la fecha actual como string (YYYY-MM-DD)
     */
    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
    
    /**
     * Reinicia el contador de sesión (llamar cuando la app se cierra/abre)
     */
    fun resetSessionCount() {
        sessionCount = 0
    }
}

/**
 * Estadísticas de uso
 */
data class UsageStats(
    val requestsToday: Int,
    val requestsThisSession: Int,
    val remainingToday: Int,
    val remainingThisSession: Int
)
