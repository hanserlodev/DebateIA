package com.hanserlod.debateia

data class ChatSession(
    val id: String,
    var title: String,
    val messages: MutableList<Message> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var debateConfig: DebateConfig? = null,  // Configuración del debate si aplica
    var setActual: Int = 1,  // Set actual (comienza en 1)
    var turnoActual: TurnoDebate = TurnoDebate.PRESENTACION,  // Turno actual en el set
    var debateFinalizado: Boolean = false  // Si el debate ya terminó
) {
    fun getPreview(): String {
        return messages.lastOrNull()?.text?.take(50) ?: "Nueva conversación"
    }
    
    /**
     * Verifica si esta sesión tiene una configuración de debate
     */
    fun isDebateConfigurado(): Boolean = debateConfig != null
    
    /**
     * Avanza al siguiente turno del debate después de cada mensaje
     * Retorna "FINALIZADO" si el debate terminó, "SET_COMPLETADO" si se completó un set, o null si continúa
     */
    fun avanzarTurno(): String? {
        if (debateFinalizado) return "FINALIZADO"
        
        when (turnoActual) {
            TurnoDebate.PRESENTACION -> {
                // Presentación → Refutación
                turnoActual = TurnoDebate.REFUTACION
            }
            TurnoDebate.REFUTACION -> {
                // Refutación → Cierre
                turnoActual = TurnoDebate.CIERRE
            }
            TurnoDebate.CIERRE -> {
                // Termina el set actual
                debateConfig?.let { config ->
                    if (setActual >= config.numeroSets) {
                        // Debate finalizado
                        debateFinalizado = true
                        return "FINALIZADO"
                    } else {
                        // Set completado, avanza al siguiente
                        setActual++
                        turnoActual = TurnoDebate.PRESENTACION
                        return "SET_COMPLETADO"
                    }
                }
            }
        }
        return null
    }
    
    /**
     * Determina quién debe hablar en el turno actual
     * Retorna true si es turno del usuario, false si es turno de la IA
     */
    fun esTurnoUsuario(): Boolean {
        debateConfig?.let { config ->
            // Presentación y Cierre: quien empezó
            // Refutación: el contrincante
            return when (turnoActual) {
                TurnoDebate.PRESENTACION, TurnoDebate.CIERRE -> config.quienEmpieza == QuienEmpieza.USUARIO
                TurnoDebate.REFUTACION -> config.quienEmpieza == QuienEmpieza.IA
            }
        }
        return true
    }
    
    /**
     * Obtiene descripción del turno actual
     */
    fun getDescripcionTurno(): String {
        return when (turnoActual) {
            TurnoDebate.PRESENTACION -> "Presentación"
            TurnoDebate.REFUTACION -> "Refutación"
            TurnoDebate.CIERRE -> "Cierre"
        }
    }
}

/**
 * Turnos en un set de debate (3 turnos por set)
 */
enum class TurnoDebate {
    PRESENTACION,  // Turno 1: Presentar idea inicial (quien inicia)
    REFUTACION,    // Turno 2: Refutar el argumento (contrario)
    CIERRE         // Turno 3: Cierre/respuesta final (quien inicia)
}
