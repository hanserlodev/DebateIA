package com.hanserlod.debateia.data.model

/**
 * Configuración de control de fuentes para debates
 */
data class SourceControlConfig(
    val contextControl: ContextControl,
    val trustedRepositories: List<TrustedRepository>,
    val blacklistUrls: List<BlacklistUrl>,
    val apiEndpointConfig: ApiEndpointConfig
)

/**
 * Control de contexto para búsquedas
 */
data class ContextControl(
    val searchScope: String,
    val searchDepthLimit: Int,
    val languageFilter: String
)

/**
 * Repositorio de fuentes confiables
 */
data class TrustedRepository(
    val name: String,
    val urls: List<String>,
    val description: String
)

/**
 * URLs bloqueadas
 */
data class BlacklistUrl(
    val url: String,
    val reason: String
)

/**
 * Configuración del endpoint de API
 */
data class ApiEndpointConfig(
    val model: String,
    val toolSettings: ToolSettings
)

/**
 * Configuración de herramientas (Google Search, etc.)
 */
data class ToolSettings(
    val googleSearch: GoogleSearchSettings
)

/**
 * Configuración de Google Search
 */
data class GoogleSearchSettings(
    val enabled: Boolean,
    val useContextInjection: Boolean,
    val trustedSearchFilter: Boolean,
    val applyBlacklist: Boolean,
    val prioritizeTrustedSources: Boolean
)
