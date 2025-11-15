package com.hanserlod.debateia

/**
 * Configuración para iniciar un debate
 */
data class DebateConfig(
    val tema: String,
    val nivelDificultad: NivelDificultad,
    val posturaUsuario: PosturaDebate,
    val quienEmpieza: QuienEmpieza,
    val numeroSets: Int = 2  // Por defecto 2 sets
) {
    /**
     * Obtiene la postura que tomará la IA (contraria al usuario)
     */
    fun getPosturaIA(): PosturaDebate {
        return when (posturaUsuario) {
            PosturaDebate.A_FAVOR -> PosturaDebate.EN_CONTRA
            PosturaDebate.EN_CONTRA -> PosturaDebate.A_FAVOR
            PosturaDebate.NEUTRAL -> PosturaDebate.NEUTRAL
        }
    }

    /**
     * Genera la instrucción personalizada para la IA basada en esta configuración
     */
    fun generarInstruccionSistema(): String {
        val posturaIA = getPosturaIA()
        val descripcionDificultad = when (nivelDificultad) {
            NivelDificultad.BASICO -> "Usa lenguaje simple y argumentos claros. Explica conceptos básicos."
            NivelDificultad.INTERMEDIO -> "Usa argumentos sólidos con ejemplos. Introduce conceptos moderadamente complejos."
            NivelDificultad.AVANZADO -> "Usa argumentos sofisticados, datos específicos y análisis profundo. IMPORTANTE: Cita fuentes académicas y verifica la credibilidad de tus referencias."
            NivelDificultad.EXPERTO -> "Usa argumentos académicos, evidencia científica, análisis exhaustivo y terminología técnica. CRÍTICO: SIEMPRE cita fuentes académicas verificadas (journals, instituciones gubernamentales, organismos internacionales). NUNCA uses Wikipedia, redes sociales, blogs personales o fuentes no verificadas."
        }

        val descripcionPostura = when (posturaIA) {
            PosturaDebate.A_FAVOR -> "Defiende la postura A FAVOR del tema"
            PosturaDebate.EN_CONTRA -> "Defiende la postura EN CONTRA del tema"
            PosturaDebate.NEUTRAL -> "Analiza ambas posturas objetivamente sin tomar un lado específico"
        }
        
        // Incluir instrucciones de fuentes solo en niveles avanzado/experto
        val instruccionesFuentes = if (nivelDificultad == NivelDificultad.AVANZADO || nivelDificultad == NivelDificultad.EXPERTO) {
            """

FUENTES PERMITIDAS (priorizar):
• Instituciones académicas (.edu, universidades)
• Organismos gubernamentales (.gob, .gov)
• Organizaciones internacionales (ONU, OMS, CEPAL, etc.)
• Journals científicos (JSTOR, SciELO, Dialnet, etc.)

FUENTES PROHIBIDAS (NUNCA usar):
• Wikipedia
• Redes sociales (Facebook, Twitter/X, Instagram, TikTok)
• Blogs personales (Medium, Blogspot, WordPress)
• YouTube (excepto canales institucionales oficiales)
• Foros de opinión sin respaldo académico

FORMATO DE CITACIÓN:
Si usas una fuente, menciona: [Institución/Autor - Año - Título breve]
"""
        } else {
            ""
        }

        return """
Eres DebateIA, participando en un debate estructurado sobre: "$tema"

CONFIGURACIÓN DEL DEBATE:
- Nivel: ${nivelDificultad.nombre}
- Tu postura: $descripcionPostura
- Postura del usuario: ${posturaUsuario.nombre}

INSTRUCCIONES:
$descripcionDificultad

Comportamiento en el debate:
- ${if (posturaIA != PosturaDebate.NEUTRAL) "Mantén firmemente tu postura: $descripcionPostura" else "Presenta ambos lados equilibradamente"}
- Presenta argumentos sólidos y bien estructurados
- Refuta los argumentos del oponente con respeto
- Usa evidencia y lógica según tu nivel de dificultad
- Mantén un tono profesional y respetuoso
- Respuestas concisas (máximo 150 palabras)
$instruccionesFuentes
${if (posturaIA != PosturaDebate.NEUTRAL) 
    "Recuerda: Debes defender tu postura ($descripcionPostura) de manera consistente durante todo el debate." 
else 
    "Recuerda: Presenta análisis objetivo sin favorecer ningún lado."}
        """.trimIndent()
    }
    
    /**
     * Verifica si se debe validar fuentes según el nivel
     */
    fun debeValidarFuentes(): Boolean {
        return nivelDificultad == NivelDificultad.AVANZADO || nivelDificultad == NivelDificultad.EXPERTO
    }
    
    /**
     * Retorna el porcentaje de errores intencionales según el nivel
     */
    fun porcentajeErroresIntencionales(): Int {
        return when (nivelDificultad) {
            NivelDificultad.BASICO -> 30
            NivelDificultad.INTERMEDIO -> 20
            NivelDificultad.AVANZADO -> 10
            NivelDificultad.EXPERTO -> 5
        }
    }

    /**
     * Genera el mensaje inicial de la IA si ella empieza el debate
     */
    fun generarMensajeInicialIA(): String {
        val posturaIA = getPosturaIA()
        return when (posturaIA) {
            PosturaDebate.A_FAVOR -> 
                "Presentaré argumentos a favor de: $tema. ¿Estás listo para defender la postura contraria?"
            PosturaDebate.EN_CONTRA -> 
                "Presentaré argumentos en contra de: $tema. ¿Estás listo para defender la postura a favor?"
            PosturaDebate.NEUTRAL -> 
                "Analicemos el tema: $tema desde múltiples perspectivas. Comienza presentando tu punto de vista."
        }
    }
}

/**
 * Nivel de dificultad del debate
 */
enum class NivelDificultad(val nombre: String) {
    BASICO("Básico"),
    INTERMEDIO("Intermedio"),
    AVANZADO("Avanzado"),
    EXPERTO("Experto")
}

/**
 * Postura en el debate
 */
enum class PosturaDebate(val nombre: String) {
    A_FAVOR("A favor"),
    EN_CONTRA("En contra"),
    NEUTRAL("Neutral")
}

/**
 * Quién comienza el debate
 */
enum class QuienEmpieza {
    USUARIO,
    IA
}
