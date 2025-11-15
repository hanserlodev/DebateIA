package com.hanserlod.debateia

import android.app.Application
import com.hanserlod.debateia.security.ApiKeyProtection
import com.hanserlod.debateia.security.UsageLimiter

/**
 * Clase Application personalizada para inicialización global
 */
class DebateIAApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar protección de API key
        ApiKeyProtection.initialize(this)
        
        // Inicializar limitador de uso
        UsageLimiter.initialize(this)
    }
}
