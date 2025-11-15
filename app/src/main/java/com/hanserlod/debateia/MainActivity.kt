package com.hanserlod.debateia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.hanserlod.debateia.api.GeminiService
import com.hanserlod.debateia.data.model.AnalisisDebate
import com.hanserlod.debateia.databinding.ActivityMainBinding
import com.hanserlod.debateia.repository.DebateRepository
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var sessionAdapter: SessionAdapter
    private lateinit var viewModel: ChatViewModel
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // Gemini Service - se inicializa si hay API key
    private var geminiService: GeminiService? = null
    private var repository: DebateRepository? = null
    private var isUsingRealAPI = false
    
    private val debateConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val tema = data.getStringExtra(DebateConfigActivity.EXTRA_TEMA) ?: return@let
                val nivelStr = data.getStringExtra(DebateConfigActivity.EXTRA_NIVEL) ?: return@let
                val posturaStr = data.getStringExtra(DebateConfigActivity.EXTRA_POSTURA_USUARIO) ?: return@let
                val quienEmpiezaStr = data.getStringExtra(DebateConfigActivity.EXTRA_QUIEN_EMPIEZA) ?: return@let
                val numeroSets = data.getIntExtra(DebateConfigActivity.EXTRA_NUMERO_SETS, 2)
                
                val config = DebateConfig(
                    tema = tema,
                    nivelDificultad = NivelDificultad.valueOf(nivelStr),
                    posturaUsuario = PosturaDebate.valueOf(posturaStr),
                    quienEmpieza = QuienEmpieza.valueOf(quienEmpiezaStr),
                    numeroSets = numeroSets
                )
                
                viewModel.createNewSession()
                viewModel.configurarDebate(config)
                
                Toast.makeText(this, "✓ Debate configurado: $tema", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, "Permiso de micrófono requerido", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            
            hideVoiceRecordingView()
            
            if (!spokenText.isNullOrEmpty()) {
                binding.editTextMessage.setText(spokenText)
                sendMessage(spokenText)
            }
        } else {
            hideVoiceRecordingView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Habilitar edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Gemini si hay API key disponible
        initializeGemini()
        
        // Inicializar ViewModel con o sin repository
        viewModel = ChatViewModelFactory(application, repository).create(ChatViewModel::class.java)
        
        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupSessionsRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Verificar si es la primera vez que abre la app
        checkFirstLaunch()
        
        // Mostrar estado de la API
        showApiStatus()
    }
    
    private fun initializeGemini() {
        try {
            // Obtener la API key de forma segura
            val apiKey = com.hanserlod.debateia.security.ApiKeyProtection.getApiKey()
            
            // Fallback: intentar desde BuildConfig (desarrollo)
            val devApiKey = if (apiKey.isEmpty()) {
                try {
                    BuildConfig.GEMINI_API_KEY
                } catch (e: Exception) {
                    ""
                }
            } else {
                apiKey
            }
            
            if (devApiKey.isNotEmpty() && devApiKey != "TU_API_KEY_AQUI") {
                geminiService = GeminiService(devApiKey)
                repository = DebateRepository(geminiService!!)
                isUsingRealAPI = true
            } else {
                isUsingRealAPI = false
            }
        } catch (e: Exception) {
            isUsingRealAPI = false
            Toast.makeText(
                this,
                "Error al inicializar Gemini: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun showApiStatus() {
        val statusMessage = if (isUsingRealAPI) {
            "✓ Conectado a Gemini AI"
        } else {
            "⚠ Modo demo - Configura tu API key en local.properties"
        }
        
        Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
    }
    
    private fun setupWindowInsets() {
        // Aplicar insets al toolbar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, 0)
            windowInsets
        }
        
        // Aplicar insets al contenedor de input
        ViewCompat.setOnApplyWindowInsetsListener(binding.messageInputContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                insets.bottom
            )
            windowInsets
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }
    
    private fun setupSessionsRecyclerView() {
        sessionAdapter = SessionAdapter(
            onSessionClick = { session ->
                viewModel.selectSession(session)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onDeleteClick = { session ->
                showDeleteSessionDialog(session)
            }
        )
        
        binding.recyclerViewSessions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = sessionAdapter
        }
    }

    private fun setupClickListeners() {
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text?.toString()?.trim()
            if (!messageText.isNullOrEmpty()) {
                sendMessage(messageText)
            }
        }
        
        binding.buttonNewChat.setOnClickListener {
            showDebateConfigDialog()
        }
        
        binding.buttonNewChatDrawer.setOnClickListener {
            showDebateConfigDialog()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        binding.buttonVoice.setOnClickListener {
            checkAudioPermissionAndStart()
        }
        
        binding.buttonStopVoice.setOnClickListener {
            hideVoiceRecordingView()
        }
        
        binding.buttonSettings.setOnClickListener {
            mostrarDialogoConfiguracion()
        }
        
        binding.btnVerResultados.setOnClickListener {
            mostrarPantallaResultados()
        }
        
        // Botón de bienvenida
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEmpezar)
            ?.setOnClickListener {
                iniciarPrimeraConfiguracion()
            }
    }
    
    /**
     * Verifica si es la primera vez que se abre la app
     */
    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences("DebateIA_Prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        
        if (isFirstLaunch) {
            // Mostrar pantalla de bienvenida
            showWelcomeScreen()
        } else {
            // Mostrar chat normal
            showChatScreen()
        }
    }
    
    /**
     * Muestra la pantalla de bienvenida
     */
    private fun showWelcomeScreen() {
        findViewById<View>(R.id.welcomeScreen)?.visibility = View.VISIBLE
        binding.recyclerViewMessages.visibility = View.GONE
        binding.messageInputContainer.visibility = View.GONE
        binding.cardDebateProgreso.visibility = View.GONE
    }
    
    /**
     * Muestra la pantalla de chat
     */
    private fun showChatScreen() {
        findViewById<View>(R.id.welcomeScreen)?.visibility = View.GONE
        binding.recyclerViewMessages.visibility = View.VISIBLE
        binding.messageInputContainer.visibility = View.VISIBLE
    }
    
    /**
     * Inicia la configuración del primer debate
     */
    private fun iniciarPrimeraConfiguracion() {
        // Marcar que ya no es la primera vez
        val prefs = getSharedPreferences("DebateIA_Prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("isFirstLaunch", false).apply()
        
        // Mostrar pantalla de chat
        showChatScreen()
        
        // Abrir diálogo de configuración de debate
        showDebateConfigDialog()
    }
    
    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages)
            scrollToBottom()
        }
        
        viewModel.sessions.observe(this) { sessions ->
            sessionAdapter.submitList(sessions.toList())
        }
        
        viewModel.currentSession.observe(this) { session ->
            binding.toolbar.title = session.title
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showTypingIndicator()
            } else {
                hideTypingIndicator()
            }
        }
        
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        viewModel.debateProgreso.observe(this) { progreso ->
            if (progreso != null) {
                if (progreso == "FINALIZADO") {
                    // Mostrar card de progreso con botón de resultados
                    binding.cardDebateProgreso.visibility = View.VISIBLE
                    binding.tvDebateProgreso.text = "🏁 Debate finalizado"
                    binding.btnVerResultados.visibility = View.VISIBLE
                } else {
                    binding.cardDebateProgreso.visibility = View.VISIBLE
                    binding.tvDebateProgreso.text = progreso
                    binding.btnVerResultados.visibility = View.GONE
                }
            } else {
                binding.cardDebateProgreso.visibility = View.GONE
                binding.btnVerResultados.visibility = View.GONE
            }
        }
    }

    private fun sendMessage(text: String) {
        if (isUsingRealAPI) {
            // Usar Gemini API real
            viewModel.sendMessageToAI(text) { success ->
                if (!success) {
                    // Si falla, mostrar error (ya se muestra en el observer)
                }
            }
        } else {
            // Modo demo - agregar mensaje del usuario
            val userMessage = Message(text = text, isUser = true)
            viewModel.addMessage(userMessage)
            scrollToBottom()

            // Limpiar input
            binding.editTextMessage.text?.clear()

            // Simular respuesta de la IA
            showTypingIndicator()
            simulateAiResponse(text)
        }
        
        // Limpiar input siempre
        binding.editTextMessage.text?.clear()
    }

    private fun simulateAiResponse(userMessage: String) {
        coroutineScope.launch {
            delay(1500) // Simular tiempo de procesamiento

            val aiResponse = generateAiResponse(userMessage)
            val aiMessage = Message(text = aiResponse, isUser = false)
            
            hideTypingIndicator()
            viewModel.addMessage(aiMessage)
            scrollToBottom()
        }
    }

    private fun generateAiResponse(userMessage: String): String {
        // Aquí puedes integrar una API real de IA
        return when {
            userMessage.contains("hola", ignoreCase = true) -> 
                "¡Hola! Estoy listo para debatir contigo. ¿Qué tema te gustaría explorar desde diferentes perspectivas?"
            
            userMessage.contains("debate", ignoreCase = true) ->
                "Excelente elección para debatir. Puedo ayudarte a explorar múltiples perspectivas sobre este tema. ¿Quieres que presente argumentos a favor, en contra, o ambos?"
            
            userMessage.contains("ayuda", ignoreCase = true) ->
                "Como asistente de debate, puedo ayudarte a:\n• Analizar temas desde múltiples perspectivas\n• Presentar argumentos sólidos\n• Identificar falacias lógicas\n• Prepararte para debates formales\n\n¿Sobre qué tema quieres debatir?"
            
            userMessage.contains("gracias", ignoreCase = true) ->
                "¡Un placer debatir contigo! ¿Hay algún otro tema que te gustaría explorar?"
            
            else ->
                "Interesante punto. En un debate sobre este tema, podríamos considerar diferentes perspectivas. Por ejemplo, algunos argumentarían que... mientras que otros podrían sostener que... ¿Qué postura te gustaría explorar más a fondo?"
        }
    }
    
    private fun checkAudioPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceRecognition()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(
                this,
                "Reconocimiento de voz no disponible en este dispositivo",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        showVoiceRecordingView()
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...")
        }
        
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            hideVoiceRecordingView()
            Toast.makeText(this, "Error al iniciar reconocimiento de voz", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showVoiceRecordingView() {
        binding.voiceRecordingView.visibility = View.VISIBLE
    }
    
    private fun hideVoiceRecordingView() {
        binding.voiceRecordingView.visibility = View.GONE
    }
    
    private fun showDeleteSessionDialog(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar conversación")
            .setMessage("¿Estás seguro de que quieres eliminar \"${session.title}\"?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteSession(session)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showTypingIndicator() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideTypingIndicator() {
        binding.progressBar.visibility = View.GONE
    }

    private fun scrollToBottom() {
        binding.recyclerViewMessages.postDelayed({
            viewModel.messages.value?.let { messages ->
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
                }
            }
        }, 100)
    }
    
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    /**
     * Muestra el diálogo de configuración del debate
     */
    private fun showDebateConfigDialog() {
        val intent = Intent(this, DebateConfigActivity::class.java)
        debateConfigLauncher.launch(intent)
    }
    
    /**
     * Muestra la pantalla de resultados del debate
     */
    private fun mostrarPantallaResultados() {
        val config = viewModel.getDebateConfig()
        
        if (config == null) {
            Toast.makeText(this, "Error: No se encontró configuración del debate", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Mostrar diálogo de carga
        val loadingDialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Generar análisis con la IA
        viewModel.generarAnalisisDebate { success ->
            loadingDialog.dismiss()
            
            if (!success) {
                val errorMsg = viewModel.error.value ?: "Error desconocido al generar el análisis"
                android.util.Log.e("MainActivity", "Error en análisis: $errorMsg")
                
                AlertDialog.Builder(this)
                    .setTitle("Error al generar análisis")
                    .setMessage(errorMsg)
                    .setPositiveButton("OK", null)
                    .show()
                return@generarAnalisisDebate
            }
            
            val analisis = viewModel.analisisDebate.value
            if (analisis == null) {
                AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage("No se pudo obtener el análisis. Intenta nuevamente.")
                    .setPositiveButton("OK", null)
                    .show()
                return@generarAnalisisDebate
            }
            
            // Mostrar diálogo con resultados
            mostrarDialogoResultados(config, analisis)
        }
    }
    
    /**
     * Muestra el diálogo con el análisis completo del debate
     */
    private fun mostrarDialogoResultados(config: DebateConfig, analisis: AnalisisDebate) {
        android.util.Log.d("MainActivity", "=== MOSTRANDO RESULTADOS ===")
        android.util.Log.d("MainActivity", "Puntuación: ${analisis.puntuacionGeneral}")
        android.util.Log.d("MainActivity", "Falacias: ${analisis.falaciasDetectadas.size}")
        android.util.Log.d("MainActivity", "Errores: ${analisis.erroresIntencionales.size}")
        android.util.Log.d("MainActivity", "Recomendaciones: ${analisis.recomendaciones.size}")
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_resultados_debate, null)
        
        // Referencias a las vistas
        val tvTemaDebate = dialogView.findViewById<android.widget.TextView>(R.id.tvTemaDebate)
        val tvPuntuacionGeneral = dialogView.findViewById<android.widget.TextView>(R.id.tvPuntuacionGeneral)
        val layoutMetricas = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutMetricas)
        val layoutFalacias = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutFalacias)
        val tvSinFalacias = dialogView.findViewById<android.widget.TextView>(R.id.tvSinFalacias)
        val layoutRecomendaciones = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutRecomendaciones)
        val layoutErroresIntencionales = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutErroresIntencionales)
        val tvRetroalimentacion = dialogView.findViewById<android.widget.TextView>(R.id.tvRetroalimentacion)
        val btnCerrar = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCerrar)
        
        android.util.Log.d("MainActivity", "Vistas inicializadas correctamente")
        
        // Configurar tema y puntuación general
        tvTemaDebate.text = "Tema: ${config.tema}"
        tvPuntuacionGeneral.text = String.format("%.1f/10", analisis.puntuacionGeneral)
        
        // Color de puntuación según el valor
        val colorPuntuacion = when {
            analisis.puntuacionGeneral >= 8.0 -> android.graphics.Color.parseColor("#4CAF50") // Verde
            analisis.puntuacionGeneral >= 6.0 -> android.graphics.Color.parseColor("#FFC107") // Amarillo
            else -> android.graphics.Color.parseColor("#F44336") // Rojo
        }
        tvPuntuacionGeneral.setTextColor(colorPuntuacion)
        
        // Agregar métricas individuales
        android.util.Log.d("MainActivity", "Agregando métricas...")
        agregarMetrica(layoutMetricas, "💬 Capacidad de Respuesta", analisis.capacidadRespuesta)
        if (config.debeValidarFuentes()) {
            agregarMetrica(layoutMetricas, "📚 Uso de Fuentes", analisis.usoFuentes)
        }
        agregarMetrica(layoutMetricas, "🎯 Calidad de Argumentación", analisis.calidadArgumentacion)
        agregarMetrica(layoutMetricas, "🔗 Coherencia", analisis.coherencia)
        agregarMetrica(layoutMetricas, "🌊 Profundidad", analisis.profundidad)
        
        // Mostrar falacias detectadas
        android.util.Log.d("MainActivity", "Procesando falacias: ${analisis.falaciasDetectadas.size}")
        if (analisis.falaciasDetectadas.isEmpty()) {
            tvSinFalacias.visibility = View.VISIBLE
            layoutFalacias.visibility = View.GONE
            android.util.Log.d("MainActivity", "No hay falacias, mostrando mensaje positivo")
        } else {
            tvSinFalacias.visibility = View.GONE
            layoutFalacias.visibility = View.VISIBLE
            android.util.Log.d("MainActivity", "Mostrando ${analisis.falaciasDetectadas.size} falacias")
            analisis.falaciasDetectadas.forEach { falacia ->
                android.util.Log.d("MainActivity", "Agregando falacia: ${falacia.name}")
                agregarFalacia(layoutFalacias, falacia)
            }
        }
        
        // Agregar recomendaciones
        android.util.Log.d("MainActivity", "Agregando ${analisis.recomendaciones.size} recomendaciones")
        analisis.recomendaciones.forEach { recomendacion ->
            agregarRecomendacion(layoutRecomendaciones, recomendacion)
        }
        
        // Agregar errores intencionales (solo mostrar sección si hay errores)
        android.util.Log.d("MainActivity", "Procesando errores intencionales: ${analisis.erroresIntencionales.size}")
        val seccionErrores = dialogView.findViewById<View>(R.id.tvTituloErrores).parent as android.view.ViewGroup
        if (analisis.erroresIntencionales.isEmpty()) {
            // Ocultar toda la sección si no hay errores
            seccionErrores.visibility = View.GONE
            android.util.Log.d("MainActivity", "No hay errores intencionales, ocultando sección")
        } else {
            seccionErrores.visibility = View.VISIBLE
            android.util.Log.d("MainActivity", "Mostrando ${analisis.erroresIntencionales.size} errores")
            analisis.erroresIntencionales.forEach { error ->
                android.util.Log.d("MainActivity", "Agregando error: ${error.tipo}, aprovechado: ${error.fueAprovechado}")
                agregarErrorIntencional(layoutErroresIntencionales, error)
            }
        }
        
        // Retroalimentación completa
        android.util.Log.d("MainActivity", "Configurando retroalimentación: ${analisis.retroalimentacion.length} caracteres")
        tvRetroalimentacion.text = analisis.retroalimentacion
        
        // Crear y mostrar diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Agrega una métrica al layout de métricas
     */
    private fun agregarMetrica(parent: android.widget.LinearLayout, nombre: String, valor: Float) {
        val metricaView = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
        val text1 = metricaView.findViewById<android.widget.TextView>(android.R.id.text1)
        val text2 = metricaView.findViewById<android.widget.TextView>(android.R.id.text2)
        
        text1.text = nombre
        text2.text = String.format("%.1f/10", valor)
        text2.textSize = 18f
        text2.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val color = when {
            valor >= 8.0 -> android.graphics.Color.parseColor("#4CAF50")
            valor >= 6.0 -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#F44336")
        }
        text2.setTextColor(color)
        
        parent.addView(metricaView)
    }
    
    /**
     * Agrega una falacia al layout de falacias
     */
    private fun agregarFalacia(parent: android.widget.LinearLayout, falacia: com.hanserlod.debateia.data.model.FalaciaDetectada) {
        val falaciaView = android.widget.TextView(this).apply {
            text = "❌ ${falacia.name} (${falacia.type})\n\n" +
                   "📝 Fragmento: \"${falacia.mensajeUsuario}\"\n\n" +
                   "💡 ${falacia.explicacion}"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
            setTextColor(android.graphics.Color.parseColor("#C62828"))
        }
        
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)
        falaciaView.layoutParams = params
        
        parent.addView(falaciaView)
    }
    
    /**
     * Agrega una recomendación al layout de recomendaciones
     */
    private fun agregarRecomendacion(parent: android.widget.LinearLayout, recomendacion: String) {
        val recomendacionView = android.widget.TextView(this).apply {
            text = "• $recomendacion"
            textSize = 14f
            setPadding(0, 8, 0, 8)
            setTextColor(resources.getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium, theme))
        }
        parent.addView(recomendacionView)
    }
    
    /**
     * Agrega un error intencional al layout de errores
     */
    private fun agregarErrorIntencional(parent: android.widget.LinearLayout, error: com.hanserlod.debateia.data.model.ErrorIntencional) {
        val icono = if (error.fueAprovechado) "✅" else "❌"
        val estado = if (error.fueAprovechado) "DETECTADO" else "NO DETECTADO"
        val colorFondo = if (error.fueAprovechado) "#E8F5E9" else "#FFEBEE"
        val colorTexto = if (error.fueAprovechado) "#2E7D32" else "#C62828"
        
        val errorView = android.widget.TextView(this).apply {
            text = "$icono ${error.tipo} - $estado\n\n" +
                   "Argumento erróneo: \"${error.argumentoErroneo.take(150)}${if (error.argumentoErroneo.length > 150) "..." else ""}\"\n\n" +
                   if (error.fueAprovechado) {
                       "¡Bien! Lo cuestionaste correctamente."
                   } else {
                       "Oportunidad perdida: Debiste cuestionar este argumento débil."
                   }
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor(colorFondo))
            setTextColor(android.graphics.Color.parseColor(colorTexto))
        }
        
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)
        errorView.layoutParams = params
        
        parent.addView(errorView)
    }
    
    /**
     * Muestra el diálogo de configuración de la aplicación
     */
    private fun mostrarDialogoConfiguracion() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_configuracion, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Cargar preferencias actuales
        val prefs = getSharedPreferences("DebateIA_Prefs", MODE_PRIVATE)
        
        // Configurar RadioGroup de tema
        val radioGroupTema = dialogView.findViewById<android.widget.RadioGroup>(R.id.radioGroupTema)
        when (prefs.getInt("tema_app", 2)) { // 0=claro, 1=oscuro, 2=sistema
            0 -> radioGroupTema.check(R.id.radioTemaClaro)
            1 -> radioGroupTema.check(R.id.radioTemaOscuro)
            2 -> radioGroupTema.check(R.id.radioTemaSistema)
        }
        
        // Configurar slider de tamaño de fuente
        val sliderTamanoFuente = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.sliderTamanoFuente)
        val tvTamanoFuente = dialogView.findViewById<android.widget.TextView>(R.id.tvTamanoFuente)
        sliderTamanoFuente.value = prefs.getInt("tamano_fuente", 14).toFloat()
        tvTamanoFuente.text = "Tamaño: ${getTamanoFuenteTexto(sliderTamanoFuente.value.toInt())} (${sliderTamanoFuente.value.toInt()}sp)"
        
        sliderTamanoFuente.addOnChangeListener { _, value, _ ->
            tvTamanoFuente.text = "Tamaño: ${getTamanoFuenteTexto(value.toInt())} (${value.toInt()}sp)"
        }
        
        // Configurar slider de número de sets
        val sliderNumeroSets = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.sliderNumeroSets)
        val tvNumeroSets = dialogView.findViewById<android.widget.TextView>(R.id.tvNumeroSets)
        sliderNumeroSets.value = prefs.getInt("numero_sets_defecto", 2).toFloat()
        tvNumeroSets.text = "Sets: ${sliderNumeroSets.value.toInt()}"
        
        sliderNumeroSets.addOnChangeListener { _, value, _ ->
            tvNumeroSets.text = "Sets: ${value.toInt()}"
        }
        
        // Configurar switches
        val switchMostrarProgreso = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchMostrarProgreso)
        switchMostrarProgreso.isChecked = prefs.getBoolean("mostrar_progreso", true)
        
        val switchGuardarHistorial = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGuardarHistorial)
        switchGuardarHistorial.isChecked = prefs.getBoolean("guardar_historial", true)
        
        val switchSonidos = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchSonidos)
        switchSonidos.isChecked = prefs.getBoolean("sonidos_habilitados", false)
        
        val switchVibracion = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchVibracion)
        switchVibracion.isChecked = prefs.getBoolean("vibracion_habilitada", false)
        
        // Botón borrar historial
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBorrarHistorial).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Borrar historial")
                .setMessage("¿Estás seguro de que deseas borrar todo el historial de debates? Esta acción no se puede deshacer.")
                .setPositiveButton("Borrar") { _, _ ->
                    viewModel.sessions.value?.clear()
                    Toast.makeText(this, "Historial borrado correctamente", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        
        // Botón ver tutorial
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVerTutorial).setOnClickListener {
            prefs.edit().putBoolean("isFirstLaunch", true).apply()
            dialog.dismiss()
            Toast.makeText(this, "Reinicia la app para ver el tutorial de nuevo", Toast.LENGTH_LONG).show()
        }
        
        // Botón cancelar
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelar).setOnClickListener {
            dialog.dismiss()
        }
        
        // Botón guardar
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGuardar).setOnClickListener {
            // Guardar preferencias
            val editor = prefs.edit()
            
            // Tema
            val temaSeleccionado = when (radioGroupTema.checkedRadioButtonId) {
                R.id.radioTemaClaro -> 0
                R.id.radioTemaOscuro -> 1
                R.id.radioTemaSistema -> 2
                else -> 2
            }
            editor.putInt("tema_app", temaSeleccionado)
            
            // Tamaño de fuente
            editor.putInt("tamano_fuente", sliderTamanoFuente.value.toInt())
            
            // Número de sets
            editor.putInt("numero_sets_defecto", sliderNumeroSets.value.toInt())
            
            // Switches
            editor.putBoolean("mostrar_progreso", switchMostrarProgreso.isChecked)
            editor.putBoolean("guardar_historial", switchGuardarHistorial.isChecked)
            editor.putBoolean("sonidos_habilitados", switchSonidos.isChecked)
            editor.putBoolean("vibracion_habilitada", switchVibracion.isChecked)
            
            editor.apply()
            
            // Aplicar tema inmediatamente
            aplicarTema(temaSeleccionado)
            
            dialog.dismiss()
            Toast.makeText(this, "✓ Configuración guardada", Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
    }
    
    /**
     * Obtiene el texto descriptivo del tamaño de fuente
     */
    private fun getTamanoFuenteTexto(tamano: Int): String {
        return when {
            tamano <= 12 -> "Pequeño"
            tamano <= 14 -> "Mediano"
            tamano <= 16 -> "Grande"
            else -> "Muy Grande"
        }
    }
    
    /**
     * Aplica el tema seleccionado
     */
    private fun aplicarTema(tema: Int) {
        val modo = when (tema) {
            0 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(modo)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}

