package com.hanserlod.debateia.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GeminiService(private val apiKey: String) {
    
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = ApiConfig.GEMINI_MODEL,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = ApiConfig.TEMPERATURE
                topK = ApiConfig.TOP_K
                topP = ApiConfig.TOP_P
                maxOutputTokens = ApiConfig.MAX_OUTPUT_TOKENS
            },
            systemInstruction = content { text(ApiConfig.SYSTEM_INSTRUCTION) }
        )
    }
    
    /**
     * Envía un mensaje y recibe una respuesta de Gemini
     */
    suspend fun sendMessage(message: String): Result<String> {
        return try {
            val response = generativeModel.generateContent(message)
            val text = response.text ?: "Lo siento, no pude generar una respuesta."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Envía un mensaje y recibe la respuesta en streaming (palabra por palabra)
     */
    fun sendMessageStream(message: String): Flow<String> = flow {
        try {
            val response = generativeModel.generateContentStream(message)
            response.collect { chunk ->
                chunk.text?.let { emit(it) }
            }
        } catch (e: Exception) {
            emit("Error: ${e.message}")
        }
    }
    
    /**
     * Inicia un chat con historial de conversación
     */
    fun startChat(history: List<ChatMessage> = emptyList()): ChatSession {
        val contentHistory = history.map { message ->
            content(role = if (message.isUser) "user" else "model") {
                text(message.text)
            }
        }
        
        val chat = generativeModel.startChat(history = contentHistory)
        return ChatSession(chat)
    }
}

/**
 * Clase para manejar una sesión de chat con contexto
 */
class ChatSession(private val chat: com.google.ai.client.generativeai.Chat) {
    
    suspend fun sendMessage(message: String): Result<String> {
        return try {
            val response = chat.sendMessage(message)
            val text = response.text ?: "Lo siento, no pude generar una respuesta."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun sendMessageStream(message: String): Flow<String> = flow {
        try {
            val response = chat.sendMessageStream(message)
            response.collect { chunk ->
                chunk.text?.let { emit(it) }
            }
        } catch (e: Exception) {
            emit("Error: ${e.message}")
        }
    }
}

/**
 * Modelo simple para mensajes en el historial
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)
