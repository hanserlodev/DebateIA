package com.hanserlod.debateia.data

/**
 * Resultado de la validación de fuentes en un mensaje
 */
data class SourceValidationResult(
    val isValid: Boolean,                      // Si el mensaje es válido según las reglas
    val foundUrls: List<String>,               // Todas las URLs encontradas en el mensaje
    val blacklistedUrls: List<String>,         // URLs que están en la lista negra
    val trustedUrls: List<String>,             // URLs que son fuentes confiables
    val hasTrustedSources: Boolean,            // Tiene al menos una fuente confiable
    val hasBlacklistedSources: Boolean         // Tiene al menos una fuente prohibida
)
