package com.hanserlod.debateia.data

import android.content.Context
import com.google.gson.Gson
import com.hanserlod.debateia.data.model.Falacia
import com.hanserlod.debateia.data.model.FalaciasConfig
import java.io.InputStreamReader

/**
 * Gestor de detección de falacias lógicas
 */
class FalaciasManager(context: Context) {
    
    private var falacias: List<Falacia> = emptyList()
    
    init {
        loadFalacias(context)
    }
    
    /**
     * Carga las falacias desde el archivo JSON
     */
    private fun loadFalacias(context: Context) {
        try {
            val inputStream = context.assets.open("falacias.json")
            val reader = InputStreamReader(inputStream)
            val config = Gson().fromJson(reader, FalaciasConfig::class.java)
            reader.close()
            
            falacias = config.falaciasList
        } catch (e: Exception) {
            e.printStackTrace()
            falacias = emptyList()
        }
    }
    
    /**
     * Analiza un mensaje del usuario buscando posibles falacias
     * Retorna lista de códigos de falacias detectadas con sus keywords
     */
    fun detectarFalacias(mensaje: String): List<Pair<String, String>> {
        val mensajeLower = mensaje.lowercase()
        val falaciasEncontradas = mutableListOf<Pair<String, String>>()
        
        falacias.forEach { falacia ->
            falacia.keywordsDetection.forEach { keyword ->
                if (mensajeLower.contains(keyword.lowercase())) {
                    falaciasEncontradas.add(Pair(falacia.code, keyword))
                }
            }
        }
        
        return falaciasEncontradas
    }
    
    /**
     * Obtiene información completa de una falacia por su código
     */
    fun obtenerFalacia(code: String): Falacia? {
        return falacias.find { it.code == code }
    }
    
    /**
     * Genera instrucciones para que la IA analice falacias
     */
    fun generarInstruccionesAnalisisFalacias(): String {
        val falaciasList = falacias.joinToString("\n") { 
            "- ${it.code}: ${it.name} - ${it.description}"
        }
        
        return """
        ANÁLISIS DE FALACIAS LÓGICAS:
        Analiza todos los mensajes del usuario buscando las siguientes falacias:
        
        $falaciasList
        
        Para cada falacia detectada, indica:
        1. El fragmento exacto del mensaje donde ocurrió
        2. Por qué constituye esa falacia
        3. Cómo podría reformularse correctamente
        """.trimIndent()
    }
    
    /**
     * Obtiene todas las falacias disponibles
     */
    fun obtenerTodasFalacias(): List<Falacia> = falacias
}
