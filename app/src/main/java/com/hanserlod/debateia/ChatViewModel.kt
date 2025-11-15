package com.hanserlod.debateia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hanserlod.debateia.data.FalaciasManager
import com.hanserlod.debateia.data.SourceManager
import com.hanserlod.debateia.data.model.AnalisisDebate
import com.hanserlod.debateia.data.model.TipoError
import com.hanserlod.debateia.data.model.ErrorIntencional
import com.hanserlod.debateia.repository.DebateRepository
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    application: Application,
    private val repository: DebateRepository? = null
) : AndroidViewModel(application) {
    
    private val _sessions = MutableLiveData<MutableList<ChatSession>>(mutableListOf())
    val sessions: LiveData<MutableList<ChatSession>> = _sessions
    
    private val _currentSession = MutableLiveData<ChatSession>()
    val currentSession: LiveData<ChatSession> = _currentSession
    
    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _debateConfigurado = MutableLiveData<Boolean>(false)
    val debateConfigurado: LiveData<Boolean> = _debateConfigurado
    
    private val _debateProgreso = MutableLiveData<String?>()
    val debateProgreso: LiveData<String?> = _debateProgreso
    
    private val _analisisDebate = MutableLiveData<AnalisisDebate?>()
    val analisisDebate: LiveData<AnalisisDebate?> = _analisisDebate
    
    // Gestores
    private val sourceManager = SourceManager(application)
    private val falaciasManager = FalaciasManager(application)
    
    // Lista temporal de mensajes del usuario durante el debate
    private val mensajesUsuarioDebate = mutableListOf<String>()
    
    // Sistema de errores intencionales
    private val erroresIntencionalesCometidos = mutableListOf<com.hanserlod.debateia.data.model.ErrorIntencional>()
    private var contadorMensajesIA = 0
    
    // Verificar si es primer lanzamiento
    private val prefs = application.getSharedPreferences("DebateIA_Prefs", android.content.Context.MODE_PRIVATE)
    
    init {
        // Solo crear sesión si NO es la primera vez
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        if (!isFirstLaunch) {
            createNewSession()
        }
    }
    
    fun createNewSession() {
        val newSession = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "Nueva conversación ${(_sessions.value?.size ?: 0) + 1}"
        )
        
        // Agregar mensaje de bienvenida
        newSession.messages.add(
            Message(
                text = "¡Hola! Soy DebateIA, tu asistente de inteligencia artificial especializado en debates. ¿Sobre qué tema te gustaría debatir hoy?",
                isUser = false
            )
        )
        
        _sessions.value?.add(0, newSession)
        _sessions.value = _sessions.value
        selectSession(newSession)
    }
    
    fun selectSession(session: ChatSession) {
        _currentSession.value = session
        _messages.value = session.messages.toList()
    }
    
    fun addMessage(message: Message) {
        _currentSession.value?.let { session ->
            session.messages.add(message)
            session.updatedAt = System.currentTimeMillis()
            _messages.value = session.messages.toList()
            
            // Guardar mensaje del usuario en lista temporal si hay debate configurado
            if (message.isUser && session.debateConfig != null) {
                mensajesUsuarioDebate.add(message.text)
                android.util.Log.d("ChatViewModel", "Mensaje guardado. Total: ${mensajesUsuarioDebate.size}")
            }
            
            // Actualizar título si es el primer mensaje del usuario
            if (session.messages.count { it.isUser } == 1 && message.isUser) {
                updateSessionTitle(session, message.text)
            }
        }
    }
    
    /**
     * Envía un mensaje del usuario y obtiene respuesta de Gemini
     */
    fun sendMessageToAI(userMessage: String, onComplete: (Boolean) -> Unit = {}) {
        _currentSession.value?.let { session ->
            // Agregar mensaje del usuario
            val userMsg = Message(text = userMessage, isUser = true)
            addMessage(userMsg)
            
            // Validar fuentes del usuario en niveles avanzado/experto
            if (session.debateConfig != null && session.debateConfig!!.debeValidarFuentes()) {
                val validationResult = sourceManager.validateMessage(userMessage, true)
                
                // Si hay fuentes en lista negra, agregar advertencia
                if (validationResult.hasBlacklistedSources) {
                    val advertencia = Message(
                        text = "⚠️ ADVERTENCIA: Has citado fuentes no académicas:\n${validationResult.blacklistedUrls.joinToString("\n") { "• $it" }}\n\nPara un debate de nivel ${session.debateConfig!!.nivelDificultad}, se recomienda usar fuentes académicas verificadas.",
                        isUser = false
                    )
                    addMessage(advertencia)
                }
                
                // Si no tiene fuentes confiables en mensaje largo, sugerir
                if (userMessage.length > 100 && !validationResult.hasTrustedSources && validationResult.foundUrls.isEmpty()) {
                    val sugerencia = Message(
                        text = "💡 TIP: En nivel ${session.debateConfig!!.nivelDificultad}, considera respaldar tus argumentos con fuentes académicas como:\n${sourceManager.generateSourceInstructions()}",
                        isUser = false
                    )
                    addMessage(sugerencia)
                }
            }
            
            // Si no hay repository (modo prueba), usar respuesta simulada
            if (repository == null) {
                onComplete(true)
                return
            }
            
            // Mostrar indicador de carga
            _isLoading.value = true
            _error.value = null
            
            viewModelScope.launch {
                try {
                    
                    // Obtener historial sin el último mensaje (que acabamos de agregar)
                    val history = session.messages.dropLast(1)
                    
                    // Sistema de errores intencionales - decidir si insertar error
                    var errorInsertado = false
                    var tipoErrorInsertado: String? = null
                    
                    // Si hay configuración de debate, preparar contexto personalizado
                    var mensajeConContexto = if (session.debateConfig != null) {
                        """
                        ${session.debateConfig!!.generarInstruccionSistema()}
                        
                        Usuario: $userMessage
                        """.trimIndent()
                    } else {
                        userMessage
                    }
                    
                    // Decidir si insertar error intencional (solo si hay debate configurado)
                    if (session.debateConfig != null && debeInsertarErrorIntencional()) {
                        mensajeConContexto = construirPromptConError(mensajeConContexto)
                        errorInsertado = true
                        tipoErrorInsertado = detectarTipoError(mensajeConContexto)
                        android.util.Log.d("ChatViewModel", "Error intencional será insertado: $tipoErrorInsertado")
                    }
                    
                    // Llamar a Gemini
                    val result = repository.getDebateResponseWithContext(mensajeConContexto, history)
                    
                    result.fold(
                        onSuccess = { aiResponse ->
                            // Agregar respuesta de la IA
                            val aiMsg = Message(text = aiResponse, isUser = false)
                            addMessage(aiMsg)
                            
                            // Registrar error intencional si se insertó
                            if (errorInsertado && tipoErrorInsertado != null) {
                                registrarErrorIntencional(tipoErrorInsertado, aiResponse)
                            }
                            
                            // Validar fuentes de la IA si hay configuración de debate
                            if (session.debateConfig != null && session.debateConfig!!.debeValidarFuentes()) {
                                val validationResult = sourceManager.validateMessage(aiResponse, true)
                                
                                // Si hay fuentes en lista negra, agregar advertencia
                                if (validationResult.hasBlacklistedSources) {
                                    val advertencia = Message(
                                        text = "⚠️ ADVERTENCIA: La IA citó fuentes no académicas:\n${validationResult.blacklistedUrls.joinToString("\n") { "• $it" }}\n\nEstas fuentes no son confiables para un debate académico.",
                                        isUser = false
                                    )
                                    addMessage(advertencia)
                                }
                            }
                            
                            // Avanzar turno si hay configuración de debate
                            if (session.debateConfig != null) {
                                val estado = session.avanzarTurno()
                                
                                when (estado) {
                                    "FINALIZADO" -> {
                                        // Debate completado - mostrar mensaje en el chat
                                        val mensajeFinal = Message(
                                            text = "🏁 ¡Debate completado! Todos los ${session.debateConfig!!.numeroSets} sets han finalizado.\n\nPresiona el botón 📊 para ver los resultados.",
                                            isUser = false
                                        )
                                        addMessage(mensajeFinal)
                                        _debateProgreso.value = "FINALIZADO"
                                    }
                                    "SET_COMPLETADO" -> {
                                        // Set completado - mostrar mensaje en el chat
                                        val setAnterior = session.setActual - 1
                                        val mensajeSet = Message(
                                            text = "✅ Set $setAnterior/${session.debateConfig!!.numeroSets} completado",
                                            isUser = false
                                        )
                                        addMessage(mensajeSet)
                                        
                                        // Actualizar progreso para el nuevo set
                                        val turnoTexto = "Presentación"
                                        val quienTurno = if (session.esTurnoUsuario()) "Tu turno" else "Turno IA"
                                        _debateProgreso.value = "Set ${session.setActual}/${session.debateConfig!!.numeroSets} - $turnoTexto - $quienTurno"
                                    }
                                    else -> {
                                        // Continuar con el siguiente turno
                                        val turnoTexto = when (session.turnoActual) {
                                            TurnoDebate.PRESENTACION -> "Presentación"
                                            TurnoDebate.REFUTACION -> "Refutación"
                                            TurnoDebate.CIERRE -> "Cierre"
                                        }
                                        val quienTurno = if (session.esTurnoUsuario()) "Tu turno" else "Turno IA"
                                        _debateProgreso.value = "Set ${session.setActual}/${session.debateConfig!!.numeroSets} - $turnoTexto - $quienTurno"
                                    }
                                }
                            }
                            
                            _isLoading.value = false
                            onComplete(true)
                        },
                        onFailure = { exception ->
                            _error.value = "Error: ${exception.message}"
                            _isLoading.value = false
                            onComplete(false)
                        }
                    )
                } catch (e: Exception) {
                    _error.value = "Error inesperado: ${e.message}"
                    _isLoading.value = false
                    onComplete(false)
                }
            }
        }
    }
    
    /**
     * Actualiza el título de la sesión automáticamente
     */
    private fun updateSessionTitle(session: ChatSession, firstMessage: String) {
        if (repository != null) {
            viewModelScope.launch {
                try {
                    val title = repository.generateConversationTitle(firstMessage)
                    session.title = title
                    _sessions.value = _sessions.value
                } catch (e: Exception) {
                    // Si falla, usar el método básico
                    session.title = firstMessage.take(30) + if (firstMessage.length > 30) "..." else ""
                    _sessions.value = _sessions.value
                }
            }
        } else {
            session.title = firstMessage.take(30) + if (firstMessage.length > 30) "..." else ""
            _sessions.value = _sessions.value
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun deleteSession(session: ChatSession) {
        _sessions.value?.remove(session)
        _sessions.value = _sessions.value
        
        // Si se elimina la sesión actual, seleccionar otra
        if (_currentSession.value?.id == session.id) {
            _sessions.value?.firstOrNull()?.let { selectSession(it) }
                ?: createNewSession()
        }
    }
    
    fun clearCurrentChat() {
        _currentSession.value?.let { session ->
            session.messages.clear()
            session.debateConfig = null
            _debateConfigurado.value = false
            session.messages.add(
                Message(
                    text = "¡Hola! Soy DebateIA. ¿Sobre qué tema te gustaría debatir?",
                    isUser = false
                )
            )
            _messages.value = session.messages.toList()
        }
    }
    
    /**
     * Configura el debate con los parámetros especificados
     */
    fun configurarDebate(config: DebateConfig) {
        _currentSession.value?.let { session ->
            // Guardar configuración
            session.debateConfig = config
            session.title = config.tema
            _debateConfigurado.value = true
            
            // Inicializar progreso del debate
            _debateProgreso.value = "Set 1/${config.numeroSets} - Presentación"
            
            // Limpiar mensajes anteriores
            session.messages.clear()
            
            // Limpiar lista temporal de mensajes del usuario
            mensajesUsuarioDebate.clear()
            
            // Limpiar errores intencionales y contador
            erroresIntencionalesCometidos.clear()
            contadorMensajesIA = 0
            
            android.util.Log.d("ChatViewModel", "Debate configurado. Listas limpiadas. Errores esperados: ${config.porcentajeErroresIntencionales()}%")
            
            // Si la IA empieza, generar mensaje inicial
            if (config.quienEmpieza == QuienEmpieza.IA) {
                viewModelScope.launch {
                    _isLoading.value = true
                    try {
                        val mensajeInicial = config.generarMensajeInicialIA()
                        val prompt = """
                            ${config.generarInstruccionSistema()}
                            
                            Como iniciador del debate, presenta tu apertura sobre: ${config.tema}
                            Sé breve (máximo 100 palabras).
                        """.trimIndent()
                        
                        val result = repository?.getDebateResponse(prompt)
                        
                        result?.fold(
                            onSuccess = { response ->
                                val aiMsg = Message(text = response, isUser = false)
                                addMessage(aiMsg)
                                _debateProgreso.value = "Set 1/${config.numeroSets} - Refutación - Tu turno"
                                _isLoading.value = false
                            },
                            onFailure = {
                                val aiMsg = Message(text = mensajeInicial, isUser = false)
                                addMessage(aiMsg)
                                _debateProgreso.value = "Set 1/${config.numeroSets} - Refutación - Tu turno"
                                _isLoading.value = false
                            }
                        )
                    } catch (e: Exception) {
                        val aiMsg = Message(text = config.generarMensajeInicialIA(), isUser = false)
                        addMessage(aiMsg)
                        _debateProgreso.value = "Set 1/${config.numeroSets} - Refutación - Tu turno"
                        _isLoading.value = false
                    }
                }
            } else {
                val mensajeBienvenida = Message(
                    text = "Debate configurado: ${config.tema}\nTu postura: ${config.posturaUsuario.nombre}\nPostura de la IA: ${config.getPosturaIA().nombre}\n\n¡Comienza tu argumento!",
                    isUser = false
                )
                addMessage(mensajeBienvenida)
                _debateProgreso.value = "Set 1/${config.numeroSets} - Presentación - Tu turno"
            }
            
            _messages.value = session.messages.toList()
        }
    }
    
    /**
     * Genera el análisis completo del debate basado en los mensajes guardados
     */
    fun generarAnalisisDebate(onComplete: (Boolean) -> Unit) {
        val session = _currentSession.value
        
        android.util.Log.d("ChatViewModel", "generarAnalisisDebate - mensajes guardados: ${mensajesUsuarioDebate.size}")
        
        if (session == null) {
            _error.value = "No hay sesión activa para analizar"
            android.util.Log.e("ChatViewModel", "Error: session es null")
            onComplete(false)
            return
        }
        
        if (mensajesUsuarioDebate.isEmpty()) {
            _error.value = "No hay mensajes del usuario para analizar"
            android.util.Log.e("ChatViewModel", "Error: No hay mensajes guardados")
            onComplete(false)
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                // Analizar aprovechamiento de errores intencionales
                val erroresAprovechados = analizarAprovechamientoErrores()
                
                // Detectar falacias en los mensajes del usuario
                val falaciasDetectadas = mutableListOf<com.hanserlod.debateia.data.model.FalaciaDetectada>()
                mensajesUsuarioDebate.forEachIndexed { index, mensaje ->
                    val falacias = falaciasManager.detectarFalacias(mensaje)
                    falacias.forEach { (codigoFalacia, keyword) ->
                        // Obtener detalles completos de la falacia
                        val falaciaInfo = falaciasManager.obtenerFalacia(codigoFalacia)
                        if (falaciaInfo != null) {
                            falaciasDetectadas.add(
                                com.hanserlod.debateia.data.model.FalaciaDetectada(
                                    code = falaciaInfo.code,
                                    name = falaciaInfo.name,
                                    type = falaciaInfo.type,
                                    mensajeUsuario = mensaje.take(100) + if (mensaje.length > 100) "..." else "",
                                    explicacion = "Detectada por la palabra clave: '$keyword' en el mensaje ${index + 1}"
                                )
                            )
                        }
                    }
                }
                
                // Crear análisis simple basado en conteo y detección
                val totalMensajes = mensajesUsuarioDebate.size
                val promedioLongitud = if (totalMensajes > 0) {
                    mensajesUsuarioDebate.sumOf { it.length } / totalMensajes.toDouble()
                } else 0.0
                
                // Ajustar puntuaciones basándose en errores perdidos
                val erroresDetectados = erroresAprovechados.count { error -> error.fueAprovechado }
                val erroresPerdidos = erroresAprovechados.count { error -> !error.fueAprovechado }
                val totalErrores = erroresAprovechados.size
                
                // Calcular porcentaje de detección
                val porcentajeDeteccion = if (totalErrores > 0) {
                    (erroresDetectados.toFloat() / totalErrores) * 100
                } else 100f
                
                // Puntuaciones mejoradas
                val capacidadRespuesta = ((totalMensajes / 6.0) * 10.0).coerceIn(1.0, 10.0).toFloat()
                
                val usoFuentes = 6.0f // Neutral por defecto
                
                val calidadArgumentacion = when {
                    promedioLongitud > 300 -> 9.0f
                    promedioLongitud > 150 -> 7.5f
                    promedioLongitud > 80 -> 6.0f
                    else -> 4.0f
                }
                
                val coherencia = when {
                    falaciasDetectadas.isEmpty() -> 9.5f
                    falaciasDetectadas.size <= 1 -> 7.5f
                    falaciasDetectadas.size <= 3 -> 6.0f
                    else -> 4.0f
                }
                
                val profundidad = ((promedioLongitud / 50.0).coerceIn(1.0, 10.0)).toFloat()
                
                // Bonus por detectar errores de la IA
                val bonusDeteccion = (porcentajeDeteccion / 10).coerceAtMost(10f)
                
                val puntuacionGeneral = ((capacidadRespuesta + calidadArgumentacion + coherencia + profundidad + bonusDeteccion) / 5.0f)
                    .coerceIn(1.0f, 10.0f)
                
                // Generar recomendaciones incluyendo las de errores
                val recomendaciones = mutableListOf<String>()
                
                // Recomendaciones por detección de errores
                if (totalErrores > 0) {
                    when {
                        porcentajeDeteccion >= 80 -> {
                            recomendaciones.add("🌟 ¡Excelente! Detectaste ${erroresDetectados} de ${totalErrores} errores de la IA (${porcentajeDeteccion.toInt()}%)")
                        }
                        porcentajeDeteccion >= 50 -> {
                            recomendaciones.add("👍 Detectaste ${erroresDetectados} de ${totalErrores} errores, pero perdiste ${erroresPerdidos} oportunidades")
                        }
                        else -> {
                            recomendaciones.add("⚠️ Solo detectaste ${erroresDetectados} de ${totalErrores} errores. Desarrolla tu escucha crítica")
                        }
                    }
                }
                
                // Añadir recomendaciones específicas de errores
                recomendaciones.addAll(generarRecomendaciones(erroresAprovechados))
                
                // Recomendaciones por falacias
                if (falaciasDetectadas.isNotEmpty()) {
                    val falaciasFrecuentes = falaciasDetectadas.groupBy { it.name }
                        .maxByOrNull { it.value.size }?.key
                    recomendaciones.add("⚠️ Cometiste ${falaciasDetectadas.size} falacia(s). La más común: ${falaciasFrecuentes ?: "N/A"}")
                } else {
                    recomendaciones.add("✅ Excelente lógica, sin falacias detectadas")
                }
                
                // Recomendaciones por longitud
                when {
                    promedioLongitud < 80 -> {
                        recomendaciones.add("📝 Desarrolla más tus argumentos. Promedio actual: ${promedioLongitud.toInt()} caracteres")
                    }
                    promedioLongitud > 500 -> {
                        recomendaciones.add("✂️ Argumentos muy extensos. Practica la síntesis")
                    }
                    else -> {
                        recomendaciones.add("👍 Buena extensión de argumentos (${promedioLongitud.toInt()} caracteres)")
                    }
                }
                
                // Recomendaciones por participación
                if (totalMensajes < 4) {
                    recomendaciones.add("📢 Participa más activamente. Solo enviaste ${totalMensajes} mensaje(s)")
                }
                
                // Generar retroalimentación completa
                val retroalimentacion = generarRetroalimentacion(
                    totalMensajes,
                    falaciasDetectadas.size,
                    erroresAprovechados
                )
                
                val analisis = AnalisisDebate(
                    puntuacionGeneral = puntuacionGeneral,
                    capacidadRespuesta = capacidadRespuesta,
                    usoFuentes = usoFuentes,
                    calidadArgumentacion = calidadArgumentacion,
                    coherencia = coherencia,
                    profundidad = profundidad,
                    falaciasDetectadas = falaciasDetectadas,
                    recomendaciones = recomendaciones,
                    erroresIntencionales = erroresAprovechados,
                    retroalimentacion = retroalimentacion
                )
                
                _analisisDebate.value = analisis
                _isLoading.value = false
                onComplete(true)
                
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error al generar análisis", e)
                _error.value = "Error al generar análisis: ${e.message}"
                _isLoading.value = false
                onComplete(false)
            }
        }
    }
    
    /**
     * Obtiene la configuración del debate actual
     */
    fun getDebateConfig(): DebateConfig? = _currentSession.value?.debateConfig
    
    // ===== FUNCIONES DEL SISTEMA DE ERRORES INTENCIONALES =====
    
    /**
     * Decide si la IA debe insertar un error intencional en su respuesta
     * Basado en el porcentaje configurado según el nivel del usuario
     */
    private fun debeInsertarErrorIntencional(): Boolean {
        val config = _currentSession.value?.debateConfig ?: return false
        val porcentaje = config.porcentajeErroresIntencionales()
        
        // Incrementar contador y decidir basándose en porcentaje
        contadorMensajesIA++
        
        // Usar random para decidir si insertar error
        val random = (1..100).random()
        val debeInsertar = random <= porcentaje
        
        android.util.Log.d("ChatViewModel", "Error intencional? Mensaje #$contadorMensajesIA, Random=$random, Porcentaje=$porcentaje%, Resultado=$debeInsertar")
        
        return debeInsertar
    }
    
    /**
     * Construye un prompt modificado que instruye secretamente a la IA para cometer un error
     * El error debe ser apropiado al nivel (sutil en EXPERTO, obvio en BÁSICO)
     */
    private fun construirPromptConError(promptOriginal: String): String {
        val config = _currentSession.value?.debateConfig ?: return promptOriginal
        
        // Seleccionar tipo de error aleatorio
        val tiposError: List<String> = listOf(
            TipoError.GENERALIZACION_APRESURADA,
            TipoError.FALSA_DICOTOMIA,
            TipoError.APELACION_AUTORIDAD,
            TipoError.DATO_INVENTADO,
            TipoError.CONTRADICCION,
            TipoError.ARGUMENTO_CIRCULAR,
            TipoError.HOMBRE_PAJA,
            TipoError.POST_HOC
        )
        val tipoError: String = tiposError.random()
        
        // Ajustar instrucción según nivel
        val instruccionError: String = when (config.nivelDificultad) {
            NivelDificultad.BASICO -> {
                // Errores más evidentes
                when (tipoError) {
                    TipoError.GENERALIZACION_APRESURADA -> "Comete una generalización apresurada obvia: usa 'SIEMPRE' o 'NUNCA' sin fundamento."
                    TipoError.FALSA_DICOTOMIA -> "Presenta solo dos opciones extremas cuando hay más alternativas."
                    TipoError.APELACION_AUTORIDAD -> "Apela a una autoridad no experta o ficticia de forma evidente."
                    TipoError.DATO_INVENTADO -> "Inventa un dato estadístico específico y cítalo como verdadero."
                    TipoError.CONTRADICCION -> "Contradice algo que dijiste anteriormente de forma evidente."
                    TipoError.ARGUMENTO_CIRCULAR -> "Usa un argumento circular obvio (la conclusión como premisa)."
                    TipoError.HOMBRE_PAJA -> "Distorsiona el argumento del oponente de forma exagerada."
                    else -> "Comete una falacia post hoc evidente (esto pasó después, entonces fue causado por)."
                }
            }
            NivelDificultad.INTERMEDIO -> {
                // Errores moderados
                when (tipoError) {
                    TipoError.GENERALIZACION_APRESURADA -> "Haz una generalización con evidencia insuficiente."
                    TipoError.FALSA_DICOTOMIA -> "Simplifica el tema a dos opciones principales."
                    TipoError.APELACION_AUTORIDAD -> "Cita a alguien famoso pero fuera de su área de expertise."
                    TipoError.DATO_INVENTADO -> "Menciona un porcentaje o estadística sin fuente clara."
                    TipoError.CONTRADICCION -> "Contradice sutilmente un punto previo."
                    TipoError.ARGUMENTO_CIRCULAR -> "Usa razonamiento parcialmente circular."
                    TipoError.HOMBRE_PAJA -> "Simplifica ligeramente el argumento del oponente."
                    else -> "Asume causalidad de correlación sin ser totalmente obvio."
                }
            }
            NivelDificultad.AVANZADO -> {
                // Errores sutiles
                when (tipoError) {
                    TipoError.GENERALIZACION_APRESURADA -> "Extrapola de casos limitados de forma sutil."
                    TipoError.FALSA_DICOTOMIA -> "Presenta una dicotomía sutil ignorando matices."
                    TipoError.APELACION_AUTORIDAD -> "Apela a consenso o autoridad de forma cuestionable."
                    TipoError.DATO_INVENTADO -> "Usa un dato plausible pero no verificado."
                    TipoError.CONTRADICCION -> "Ten una inconsistencia menor con puntos anteriores."
                    TipoError.ARGUMENTO_CIRCULAR -> "Asume parcialmente lo que intentas probar."
                    TipoError.HOMBRE_PAJA -> "Enfócate en la versión más débil del argumento contrario."
                    else -> "Sugiere causalidad donde solo hay correlación, sutilmente."
                }
            }
            NivelDificultad.EXPERTO -> {
                // Errores muy sutiles
                when (tipoError) {
                    TipoError.GENERALIZACION_APRESURADA -> "Generaliza desde evidencia limitada de forma muy sutil."
                    TipoError.FALSA_DICOTOMIA -> "Enmarca el debate en términos binarios de forma sofisticada."
                    TipoError.APELACION_AUTORIDAD -> "Cita autoridad de forma que suene convincente pero sea cuestionable."
                    TipoError.DATO_INVENTADO -> "Incluye un dato específico que suene creíble pero sea inventado."
                    TipoError.CONTRADICCION -> "Ten una inconsistencia muy sutil con argumentos previos."
                    TipoError.ARGUMENTO_CIRCULAR -> "Construye un argumento cuya lógica sea sutilmente circular."
                    TipoError.HOMBRE_PAJA -> "Reformula el argumento contrario de forma ligeramente sesgada."
                    else -> "Implica causalidad desde correlación de forma sofisticada."
                }
            }
            else -> "Comete un error lógico en tu argumento."
        }
        
        // Construir prompt modificado
        val promptModificado = """
            $promptOriginal
            
            [INSTRUCCIÓN ESPECIAL DE ENSEÑANZA - NO MENCIONAR AL USUARIO]:
            $instruccionError
            Hazlo de forma natural en tu respuesta. El usuario debe poder detectarlo si presta atención.
            Tipo de error a cometer: $tipoError
        """.trimIndent()
        
        android.util.Log.d("ChatViewModel", "Prompt con error inyectado: $tipoError (${config.nivelDificultad})")
        
        return promptModificado
    }
    
    /**
     * Registra un error intencional que acaba de ser cometido por la IA
     */
    private fun registrarErrorIntencional(tipoError: String, argumentoErroneo: String) {
        val error = com.hanserlod.debateia.data.model.ErrorIntencional(
            tipo = tipoError,
            argumentoErroneo = argumentoErroneo,
            fueAprovechado = false // Se determinará después en el análisis
        )
        
        erroresIntencionalesCometidos.add(error)
        
        android.util.Log.d("ChatViewModel", "Error registrado: $tipoError - Total errores: ${erroresIntencionalesCometidos.size}")
    }
    
    /**
     * Detecta el tipo de error en la respuesta de la IA basándose en el prompt usado
     */
    private fun detectarTipoError(prompt: String): String? {
        // Buscar qué tipo de error se instruyó en el prompt
        return when {
            prompt.contains(TipoError.GENERALIZACION_APRESURADA) -> TipoError.GENERALIZACION_APRESURADA
            prompt.contains(TipoError.FALSA_DICOTOMIA) -> TipoError.FALSA_DICOTOMIA
            prompt.contains(TipoError.APELACION_AUTORIDAD) -> TipoError.APELACION_AUTORIDAD
            prompt.contains(TipoError.DATO_INVENTADO) -> TipoError.DATO_INVENTADO
            prompt.contains(TipoError.CONTRADICCION) -> TipoError.CONTRADICCION
            prompt.contains(TipoError.ARGUMENTO_CIRCULAR) -> TipoError.ARGUMENTO_CIRCULAR
            prompt.contains(TipoError.HOMBRE_PAJA) -> TipoError.HOMBRE_PAJA
            prompt.contains(TipoError.POST_HOC) -> TipoError.POST_HOC
            else -> null
        }
    }
    
    /**
     * Analiza si el usuario aprovechó las oportunidades de detectar errores intencionales
     * Revisa los mensajes del usuario buscando cuestionamientos o detecciones de falacias
     */
    private fun analizarAprovechamientoErrores(): List<com.hanserlod.debateia.data.model.ErrorIntencional> {
        // Para cada error cometido, verificar si el usuario lo cuestionó en mensajes posteriores
        erroresIntencionalesCometidos.forEachIndexed { index, error ->
            // Buscar en mensajes del usuario después del error
            val mensajesPosteriores = mensajesUsuarioDebate.drop(index)
            
            val fueDetectado = detectarCuestionamiento(error, mensajesPosteriores)
            
            // Actualizar el error con el resultado
            erroresIntencionalesCometidos[index] = error.copy(fueAprovechado = fueDetectado)
        }
        
        val erroresDetectados = erroresIntencionalesCometidos.count { it.fueAprovechado }
        val erroresPerdidos = erroresIntencionalesCometidos.size - erroresDetectados
        
        android.util.Log.d("ChatViewModel", "Análisis errores: ${erroresDetectados} detectados, ${erroresPerdidos} perdidos")
        
        return erroresIntencionalesCometidos.toList()
    }
    
    /**
     * Detecta si el usuario cuestionó un error específico en sus mensajes
     */
    private fun detectarCuestionamiento(error: com.hanserlod.debateia.data.model.ErrorIntencional, mensajesUsuario: List<String>): Boolean {
        // Palabras clave que indican que el usuario detectó algo
        val palabrasClave = listOf(
            "falacia", "error", "incorrecto", "no es cierto", "contradice", 
            "dato falso", "inventado", "no tiene sentido", "lógica", "argumento débil",
            "generalización", "dicotomía", "autoridad", "circular", "fuente",
            "evidencia", "prueba", "demostrar", "verificar", "cuestiono"
        )
        
        // Buscar si algún mensaje del usuario contiene cuestionamiento
        return mensajesUsuario.any { mensaje ->
            palabrasClave.any { palabra ->
                mensaje.lowercase().contains(palabra)
            }
        }
    }
    
    /**
     * Genera recomendaciones personalizadas basadas en errores detectados/perdidos
     */
    private fun generarRecomendaciones(erroresAprovechados: List<com.hanserlod.debateia.data.model.ErrorIntencional>): List<String> {
        val recomendaciones = mutableListOf<String>()
        
        val erroresDetectados = erroresAprovechados.count { error -> error.fueAprovechado }
        val erroresPerdidos = erroresAprovechados.count { error -> !error.fueAprovechado }
        
        if (erroresPerdidos > 0) {
            recomendaciones.add("⚠️ No detectaste $erroresPerdidos oportunidad(es) de cuestionar argumentos débiles de la IA")
            
            // Recomendar según tipos de errores perdidos
            val tiposPerdidos: List<String> = erroresAprovechados
                .filter { error -> !error.fueAprovechado }
                .map { error -> error.tipo }
                .distinct()
            
            tiposPerdidos.forEach { tipo: String ->
                when (tipo) {
                    TipoError.GENERALIZACION_APRESURADA -> 
                        recomendaciones.add("💡 Presta atención a generalizaciones con palabras como 'siempre', 'nunca', 'todos'")
                    TipoError.FALSA_DICOTOMIA -> 
                        recomendaciones.add("💡 Cuestiona cuando solo se presentan dos opciones extremas")
                    TipoError.APELACION_AUTORIDAD -> 
                        recomendaciones.add("💡 Verifica las credenciales de las autoridades citadas")
                    TipoError.DATO_INVENTADO -> 
                        recomendaciones.add("💡 Pide fuentes para datos estadísticos específicos")
                    TipoError.CONTRADICCION -> 
                        recomendaciones.add("💡 Compara argumentos actuales con lo dicho anteriormente")
                    TipoError.ARGUMENTO_CIRCULAR -> 
                        recomendaciones.add("💡 Analiza si la conclusión ya está asumida en las premisas")
                    TipoError.HOMBRE_PAJA -> 
                        recomendaciones.add("💡 Verifica si tu argumento fue representado correctamente")
                    TipoError.POST_HOC -> 
                        recomendaciones.add("💡 Distingue correlación de causalidad")
                }
            }
        }
        
        if (erroresDetectados > 0) {
            recomendaciones.add("✅ ¡Bien! Detectaste $erroresDetectados error(es) intencional(es) de la IA")
        }
        
        return recomendaciones
    }
    
    /**
     * Genera retroalimentación narrativa completa sobre el desempeño
     */
    private fun generarRetroalimentacion(
        totalMensajes: Int,
        falaciasUsuario: Int,
        erroresAprovechados: List<com.hanserlod.debateia.data.model.ErrorIntencional>
    ): String {
        val erroresDetectados = erroresAprovechados.count { error -> error.fueAprovechado }
        val erroresPerdidos = erroresAprovechados.count { error -> !error.fueAprovechado }
        val totalErrores = erroresAprovechados.size
        
        val config = _currentSession.value?.debateConfig
        val porcentajeDeteccion = if (totalErrores > 0) {
            ((erroresDetectados.toFloat() / totalErrores) * 100).toInt()
        } else 0
        
        return buildString {
            appendLine("📊 ANÁLISIS COMPLETO DEL DEBATE")
            appendLine()
            appendLine("Tema debatido: \"${config?.tema ?: "N/A"}\"")
            appendLine("Nivel: ${config?.nivelDificultad?.nombre ?: "N/A"}")
            appendLine("Tu postura: ${config?.posturaUsuario?.nombre ?: "N/A"}")
            appendLine("Mensajes enviados: $totalMensajes")
            appendLine()
            
            // Análisis de pensamiento crítico
            appendLine("🎯 PENSAMIENTO CRÍTICO (Detección de Errores de la IA)")
            if (totalErrores > 0) {
                appendLine("La IA cometió $totalErrores error(es) intencional(es) para entrenar tu análisis crítico:")
                appendLine()
                appendLine("✅ Detectados: $erroresDetectados ($porcentajeDeteccion%)")
                appendLine("❌ Perdidos: $erroresPerdidos")
                appendLine()
                
                when {
                    porcentajeDeteccion >= 80 -> {
                        appendLine("🌟 ¡EXCELENTE! Tienes una capacidad de análisis crítico sobresaliente.")
                        appendLine("Detectaste la mayoría de los errores que cometí intencionalmente.")
                    }
                    porcentajeDeteccion >= 50 -> {
                        appendLine("👍 BIEN. Vas por buen camino, pero hay margen de mejora.")
                        appendLine("Revisa los errores que dejaste pasar para aprender a identificarlos.")
                    }
                    else -> {
                        appendLine("💪 NECESITAS PRACTICAR MÁS. La mayoría de errores pasaron desapercibidos.")
                        appendLine("Desarrolla tu escucha activa y cuestiona cada afirmación de la IA.")
                    }
                }
                appendLine()
                
                if (erroresPerdidos > 0) {
                    appendLine("⚠️ OPORTUNIDADES QUE PERDISTE:")
                    erroresAprovechados.filter { !it.fueAprovechado }.forEach { error ->
                        appendLine("• ${error.tipo}")
                        appendLine("  \"${error.argumentoErroneo.take(100)}${if (error.argumentoErroneo.length > 100) "..." else ""}\"")
                        appendLine()
                    }
                }
                
                if (erroresDetectados > 0) {
                    appendLine("✅ ERRORES QUE DETECTASTE BIEN:")
                    erroresAprovechados.filter { it.fueAprovechado }.forEach { error ->
                        appendLine("• ${error.tipo}")
                    }
                    appendLine()
                }
            } else {
                appendLine("En este debate no hubo errores intencionales.")
                appendLine()
            }
            
            // Análisis de falacias del usuario
            appendLine("🔍 TU LÓGICA ARGUMENTATIVA")
            if (falaciasUsuario > 0) {
                appendLine("⚠️ Detecté $falaciasUsuario posible(s) falacia(s) en tus argumentos.")
                appendLine("Esto debilita la solidez de tu posición. Revisa la sección de falacias.")
            } else {
                appendLine("✅ No detecté falacias lógicas en tus argumentos. ¡Excelente!")
            }
            appendLine()
            
            // Mensaje motivacional final
            appendLine("💡 RECOMENDACIÓN FINAL")
            appendLine("El pensamiento crítico se perfecciona con la práctica constante.")
            appendLine("Cada debate es una oportunidad para mejorar tu capacidad analítica.")
            appendLine()
            appendLine("En nivel ${config?.nivelDificultad?.nombre ?: "N/A"}, cometí aproximadamente")
            appendLine("${config?.porcentajeErroresIntencionales() ?: 0}% de errores intencionales.")
            appendLine("Tu objetivo: detectarlos TODOS y cuestionarlos con evidencia.")
            appendLine()
            appendLine("¡Sigue practicando! 💪")
        }
    }
}
