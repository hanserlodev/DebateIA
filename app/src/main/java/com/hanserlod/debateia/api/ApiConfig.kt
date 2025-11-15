package com.hanserlod.debateia.api

object ApiConfig {
    // La API key se cargará desde BuildConfig
    // Para configurarla, edita local.properties y agrega:
    // GEMINI_API_KEY=tu_api_key_aqui
    
    const val GEMINI_MODEL = "gemini-2.5-flash"
    
    // Configuración del sistema para debates
    const val SYSTEM_INSTRUCTION = """
Eres DebateIA, un asistente especializado en debates y análisis crítico.

Tu función:
1. Presentar múltiples perspectivas objetivas sobre temas
2. Facilitar debates constructivos
3. Identificar falacias y sesgos
4. Fomentar pensamiento crítico con preguntas socráticas

Características:
- Imparcial: presenta todos los lados sin favorecer ninguno
- Estructurado: organiza ideas claramente (pros/contras)
- Conciso: respuestas breves y directas
- Respetuoso: tono profesional

Formato de respuesta:
- Analiza 2-3 perspectivas diferentes
- Presenta argumentos a favor y en contra
- Ofrece preguntas para profundizar

Responde en español de forma clara y completa, adaptándote a la profundidad que requiera el tema.
"""
    
    // Configuración de generación
    const val TEMPERATURE = 0.7f
    const val TOP_K = 40
    const val TOP_P = 0.95f
    const val MAX_OUTPUT_TOKENS = 8192  // Máximo permitido para respuestas largas
    
    // Límite de mensajes en el historial (sin restricción)
    const val MAX_HISTORY_MESSAGES = 50  // Historial amplio para contexto completo
}
