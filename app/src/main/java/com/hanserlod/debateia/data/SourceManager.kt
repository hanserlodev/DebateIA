package com.hanserlod.debateia.data

import android.content.Context
import com.google.gson.Gson
import com.hanserlod.debateia.data.model.*
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Gestor de fuentes confiables y lista negra
 * Valida que las fuentes usadas cumplan con los criterios establecidos
 */
class SourceManager(context: Context) {
    
    private var config: SourceControlConfig? = null
    private val trustedPatterns = mutableListOf<Pattern>()
    private val blacklistPatterns = mutableListOf<Pattern>()
    
    init {
        loadConfiguration(context)
    }
    
    /**
     * Carga la configuración desde el archivo JSON
     */
    private fun loadConfiguration(context: Context) {
        try {
            val inputStream = context.assets.open("fuentes.json")
            val reader = InputStreamReader(inputStream)
            config = Gson().fromJson(reader, SourceControlConfig::class.java)
            reader.close()
            
            // Compilar patrones de URLs confiables
            config?.trustedRepositories?.forEach { repo ->
                repo.urls.forEach { urlPattern ->
                    trustedPatterns.add(wildcardToPattern(urlPattern))
                }
            }
            
            // Compilar patrones de URLs bloqueadas
            config?.blacklistUrls?.forEach { blacklist ->
                blacklistPatterns.add(wildcardToPattern(blacklist.url))
            }
            
        } catch (e: Exception) {
            // Si no se encuentra el archivo, usar valores por defecto
            config = getDefaultConfig()
        }
    }
    
    /**
     * Convierte un patrón con wildcards (*) a regex Pattern
     */
    private fun wildcardToPattern(wildcard: String): Pattern {
        val regex = wildcard
            .replace(".", "\\.")
            .replace("*", ".*")
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
    }
    
    /**
     * Verifica si una URL está en la lista negra
     */
    fun isBlacklisted(url: String): Boolean {
        return blacklistPatterns.any { it.matcher(url).matches() }
    }
    
    /**
     * Verifica si una URL es de fuente confiable
     */
    fun isTrustedSource(url: String): Boolean {
        return trustedPatterns.any { it.matcher(url).matches() }
    }
    
    /**
     * Obtiene la razón de bloqueo de una URL
     */
    fun getBlacklistReason(url: String): String? {
        config?.blacklistUrls?.forEach { blacklist ->
            if (wildcardToPattern(blacklist.url).matcher(url).matches()) {
                return blacklist.reason
            }
        }
        return null
    }
    
    /**
     * Valida un mensaje buscando URLs y verificando si son válidas
     * Retorna un resultado con las URLs encontradas y su estado
     */
    fun validateMessage(message: String, checkSources: Boolean = true): SourceValidationResult {
        if (!checkSources) {
            return SourceValidationResult(
                isValid = true,
                foundUrls = emptyList(),
                blacklistedUrls = emptyList(),
                trustedUrls = emptyList(),
                hasTrustedSources = false,
                hasBlacklistedSources = false
            )
        }
        
        val urls = extractUrls(message)
        val blacklisted = mutableListOf<String>()
        val trusted = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        urls.forEach { url ->
            when {
                isBlacklisted(url) -> {
                    blacklisted.add(url)
                    val reason = getBlacklistReason(url)
                    warnings.add("⚠️ Fuente bloqueada: $url\nRazón: $reason")
                }
                isTrustedSource(url) -> {
                    trusted.add(url)
                }
                else -> {
                    warnings.add("ℹ️ Fuente no verificada: $url")
                }
            }
        }
        
        return SourceValidationResult(
            isValid = blacklisted.isEmpty(),
            foundUrls = urls,
            blacklistedUrls = blacklisted,
            trustedUrls = trusted,
            hasTrustedSources = trusted.isNotEmpty(),
            hasBlacklistedSources = blacklisted.isNotEmpty()
        )
    }
    
    /**
     * Extrae URLs de un texto
     */
    private fun extractUrls(text: String): List<String> {
        val urlPattern = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = urlPattern.matcher(text)
        val urls = mutableListOf<String>()
        
        while (matcher.find()) {
            matcher.group(1)?.let { urls.add(it) }
        }
        
        return urls
    }
    
    /**
     * Genera instrucciones para la IA sobre fuentes permitidas
     */
    fun generateSourceInstructions(): String {
        val instructions = StringBuilder()
        
        instructions.append("INSTRUCCIONES SOBRE FUENTES:\n\n")
        
        // Fuentes confiables
        instructions.append("FUENTES CONFIABLES (priorizar):\n")
        config?.trustedRepositories?.forEach { repo ->
            instructions.append("• ${repo.name}:\n")
            repo.urls.take(3).forEach { url ->
                instructions.append("  - $url\n")
            }
        }
        
        instructions.append("\nFUENTES PROHIBIDAS (NO USAR):\n")
        config?.blacklistUrls?.take(10)?.forEach { blacklist ->
            instructions.append("• ${blacklist.url}: ${blacklist.reason}\n")
        }
        
        instructions.append("\nREGLAS:\n")
        instructions.append("• SIEMPRE priorizar fuentes académicas y gubernamentales\n")
        instructions.append("• NUNCA usar redes sociales como fuente\n")
        instructions.append("• NUNCA usar blogs personales sin respaldo institucional\n")
        instructions.append("• VERIFICAR que las fuentes sean recientes y relevantes\n")
        
        return instructions.toString()
    }
    
    /**
     * Obtiene la configuración actual
     */
    fun getConfig(): SourceControlConfig? = config
    
    /**
     * Configuración por defecto si no se carga el JSON
     */
    private fun getDefaultConfig(): SourceControlConfig {
        return SourceControlConfig(
            contextControl = ContextControl(
                searchScope = "academic_and_verified_news",
                searchDepthLimit = 5,
                languageFilter = "es"
            ),
            trustedRepositories = listOf(
                TrustedRepository(
                    name = "Fuentes Académicas",
                    urls = listOf("*.edu.pe/*", "*.gob.pe/*"),
                    description = "Fuentes educativas y gubernamentales"
                )
            ),
            blacklistUrls = listOf(
                BlacklistUrl("wikipedia.org/*", "Edición abierta sin revisión rigurosa"),
                BlacklistUrl("facebook.com/*", "Red social sin verificación"),
                BlacklistUrl("twitter.com/*", "Red social sin verificación"),
                BlacklistUrl("youtube.com/*", "Videos sin revisión editorial")
            ),
            apiEndpointConfig = ApiEndpointConfig(
                model = "gemini-2.5-flash",
                toolSettings = ToolSettings(
                    googleSearch = GoogleSearchSettings(
                        enabled = true,
                        useContextInjection = true,
                        trustedSearchFilter = true,
                        applyBlacklist = true,
                        prioritizeTrustedSources = true
                    )
                )
            )
        )
    }
}

