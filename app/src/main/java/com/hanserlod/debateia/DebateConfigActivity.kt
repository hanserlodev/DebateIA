package com.hanserlod.debateia

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.ChipGroup
import com.hanserlod.debateia.databinding.ActivityDebateConfigBinding

class DebateConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebateConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Habilitar edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityDebateConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupListeners()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Aplicar padding al AppBarLayout para la barra superior
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            
            // Aplicar padding al botón para la barra inferior
            val buttonParams = binding.btnComenzarDebate.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            buttonParams.setMargins(
                24.dpToPx(),
                24.dpToPx(),
                24.dpToPx(),
                systemBars.bottom + 24.dpToPx()
            )
            binding.btnComenzarDebate.layoutParams = buttonParams
            
            insets
        }
    }
    
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupListeners() {
        var numeroSets = 2  // Por defecto 2 sets
        
        // Actualizar contador de sets
        binding.btnDecrementSets.setOnClickListener {
            if (numeroSets > 1) {
                numeroSets--
                binding.tvNumeroSets.text = "$numeroSets sets"
            }
        }
        
        binding.btnIncrementSets.setOnClickListener {
            if (numeroSets < 10) {
                numeroSets++
                binding.tvNumeroSets.text = "$numeroSets sets"
            }
        }
        
        // Actualizar postura de la IA cuando cambia la del usuario
        binding.chipGroupPostura.setOnCheckedStateChangeListener { _, checkedIds ->
            val posturaIA = when (checkedIds.firstOrNull()) {
                R.id.chipAFavor -> "La IA tomará la postura EN CONTRA"
                R.id.chipEnContra -> "La IA tomará la postura A FAVOR"
                R.id.chipNeutral -> "La IA analizará objetivamente ambas posturas"
                else -> "La IA tomará la postura contraria"
            }
            binding.tvPosturaIA.text = posturaIA
        }
        
        binding.btnComenzarDebate.setOnClickListener {
            val tema = binding.etTema.text?.toString()?.trim()
            
            if (tema.isNullOrEmpty()) {
                binding.tilTema.error = "Por favor ingresa un tema"
                return@setOnClickListener
            }
            
            // Obtener nivel de dificultad
            val nivelDificultad = when (binding.chipGroupDificultad.checkedChipId) {
                R.id.chipBasico -> NivelDificultad.BASICO
                R.id.chipIntermedio -> NivelDificultad.INTERMEDIO
                R.id.chipAvanzado -> NivelDificultad.AVANZADO
                R.id.chipExperto -> NivelDificultad.EXPERTO
                else -> NivelDificultad.BASICO
            }
            
            // Obtener postura del usuario
            val posturaUsuario = when (binding.chipGroupPostura.checkedChipId) {
                R.id.chipAFavor -> PosturaDebate.A_FAVOR
                R.id.chipEnContra -> PosturaDebate.EN_CONTRA
                R.id.chipNeutral -> PosturaDebate.NEUTRAL
                else -> PosturaDebate.A_FAVOR
            }
            
            // Obtener quién empieza
            val quienEmpieza = when (binding.chipGroupQuienEmpieza.checkedChipId) {
                R.id.chipUsuarioEmpieza -> QuienEmpieza.USUARIO
                R.id.chipIAEmpieza -> QuienEmpieza.IA
                else -> QuienEmpieza.USUARIO
            }
            
            // Crear configuración
            val config = DebateConfig(
                tema = tema,
                nivelDificultad = nivelDificultad,
                posturaUsuario = posturaUsuario,
                quienEmpieza = quienEmpieza,
                numeroSets = numeroSets
            )
            
            // Devolver resultado
            setResult(RESULT_OK, intent.apply {
                putExtra(EXTRA_TEMA, config.tema)
                putExtra(EXTRA_NIVEL, config.nivelDificultad.name)
                putExtra(EXTRA_POSTURA_USUARIO, config.posturaUsuario.name)
                putExtra(EXTRA_QUIEN_EMPIEZA, config.quienEmpieza.name)
                putExtra(EXTRA_NUMERO_SETS, config.numeroSets)
            })
            
            finish()
        }
    }
    
    companion object {
        const val EXTRA_TEMA = "extra_tema"
        const val EXTRA_NIVEL = "extra_nivel"
        const val EXTRA_POSTURA_USUARIO = "extra_postura_usuario"
        const val EXTRA_QUIEN_EMPIEZA = "extra_quien_empieza"
        const val EXTRA_NUMERO_SETS = "extra_numero_sets"
    }
}
