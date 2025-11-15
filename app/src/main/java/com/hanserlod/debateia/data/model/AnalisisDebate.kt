package com.hanserlod.debateia.data.model

/**
 * Resultado del análisis completo del debate
 */
data class AnalisisDebate(
    // Evaluación general (1-10)
    val puntuacionGeneral: Float,
    
    // Métricas individuales
    val capacidadRespuesta: Float,      // 1-10
    val usoFuentes: Float,              // 1-10 (solo en niveles avanzado/experto)
    val calidadArgumentacion: Float,    // 1-10
    val coherencia: Float,              // 1-10
    val profundidad: Float,             // 1-10
    
    // Falacias detectadas
    val falaciasDetectadas: List<FalaciaDetectada>,
    
    // Recomendaciones de la IA
    val recomendaciones: List<String>,
    
    // Análisis de errores intencionales de la IA
    val erroresIntencionales: List<ErrorIntencional>,
    
    // Retroalimentación personalizada
    val retroalimentacion: String
)

/**
 * Falacia detectada en el debate del usuario
 */
data class FalaciaDetectada(
    val code: String,
    val name: String,
    val type: String,
    val mensajeUsuario: String,         // Fragmento donde se detectó
    val explicacion: String             // Por qué se considera una falacia
)

/**
 * Error intencional cometido por la IA para generar oportunidades
 */
data class ErrorIntencional(
    val tipo: String,                    // Tipo de error lógico
    val argumentoErroneo: String,        // El argumento con error
    val fueAprovechado: Boolean = false  // Si el usuario lo detectó
)

/**
 * Tipos de errores intencionales que la IA puede cometer
 */
object TipoError {
    const val GENERALIZACION_APRESURADA = "Generalización Apresurada"
    const val FALSA_DICOTOMIA = "Falsa Dicotomía"
    const val APELACION_AUTORIDAD = "Apelación a Autoridad sin Evidencia"
    const val DATO_INVENTADO = "Dato sin Fuente"
    const val CONTRADICCION = "Contradicción"
    const val ARGUMENTO_CIRCULAR = "Argumento Circular"
    const val HOMBRE_PAJA = "Hombre de Paja"
    const val POST_HOC = "Post Hoc (Falsa Causalidad)"
}
