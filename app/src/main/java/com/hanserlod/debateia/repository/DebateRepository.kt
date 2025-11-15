package com.hanserlod.debateia.repository

import com.hanserlod.debateia.Message
import com.hanserlod.debateia.api.ApiConfig
import com.hanserlod.debateia.api.ChatMessage
import com.hanserlod.debateia.api.GeminiService
import kotlinx.coroutines.flow.Flow

class DebateRepository(private val geminiService: GeminiService) {
    
    /**
     * Obtiene una respuesta de debate sin contexto previo
     */
    suspend fun getDebateResponse(userMessage: String): Result<String> {
        return geminiService.sendMessage(userMessage)
    }
    
    /**
     * Obtiene una respuesta de debate con contexto de la conversación
     */
    suspend fun getDebateResponseWithContext(
        userMessage: String,
        conversationHistory: List<Message>
    ): Result<String> {
        // Limitar el historial para evitar exceder el contexto
        val limitedHistory = conversationHistory.takeLast(ApiConfig.MAX_HISTORY_MESSAGES)
        
        // Convertir el historial al formato que espera Gemini
        val chatHistory = limitedHistory.map { message ->
            ChatMessage(
                text = message.text,
                isUser = message.isUser
            )
        }
        
        // Iniciar chat con historial
        val chatSession = geminiService.startChat(chatHistory)
        
        // Enviar el mensaje actual
        return chatSession.sendMessage(userMessage)
    }
    
    /**
     * Obtiene una respuesta en streaming (palabra por palabra)
     */
    fun getDebateResponseStream(
        userMessage: String,
        conversationHistory: List<Message>
    ): Flow<String> {
        // Limitar el historial para evitar exceder el contexto
        val limitedHistory = conversationHistory.takeLast(ApiConfig.MAX_HISTORY_MESSAGES)
        
        val chatHistory = limitedHistory.map { message ->
            ChatMessage(
                text = message.text,
                isUser = message.isUser
            )
        }
        
        val chatSession = geminiService.startChat(chatHistory)
        return chatSession.sendMessageStream(userMessage)
    }
    
    /**
     * Genera un título sugerido para la conversación basado en el primer mensaje
     */
    suspend fun generateConversationTitle(firstUserMessage: String): String {
        val prompt = """
            Genera un título corto (máximo 5 palabras) para una conversación de debate que comienza con:
            "$firstUserMessage"
            
            Solo responde con el título, sin comillas ni explicaciones.
        """.trimIndent()
        
        return try {
            val result = geminiService.sendMessage(prompt)
            result.getOrNull()?.take(50) ?: firstUserMessage.take(30)
        } catch (e: Exception) {
            firstUserMessage.take(30)
        }
    }
    
    /**
     * Analiza un tema y sugiere puntos de debate
     */
    suspend fun analyzeDebateTopic(topic: String): Result<String> {
        val prompt = """
            Analiza el siguiente tema de debate: "$topic"
            
            Proporciona:
            1. 3 argumentos a favor
            2. 3 argumentos en contra
            3. 2 preguntas clave para profundizar el debate
            
            Sé conciso y directo.
        """.trimIndent()
        
        return geminiService.sendMessage(prompt)
    }
}
