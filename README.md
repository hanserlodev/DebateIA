# 🎯 DebateIA

**DebateIA** es una aplicación Android educativa diseñada para mejorar tus habilidades de debate y pensamiento crítico mediante debates interactivos con inteligencia artificial.

## ✨ Características Principales

### 🤖 Sistema de IA con Errores Intencionales

La característica más innovadora de DebateIA es su **sistema de errores intencionales educativos**. La IA comete deliberadamente errores lógicos como oportunidades de aprendizaje:

- **BÁSICO**: 30% de errores (más evidentes)
- **INTERMEDIO**: 20% de errores (moderados)
- **AVANZADO**: 10% de errores (sutiles)
- **EXPERTO**: 5% de errores (muy sutiles)

#### Tipos de Errores Intencionales

1. **Generalización Apresurada**: Uso indebido de "siempre", "nunca", "todos" sin evidencia
2. **Falsa Dicotomía**: Presentar solo dos opciones cuando existen más
3. **Apelación a la Autoridad**: Citar autoridades no expertas o cuestionables
4. **Dato Inventado**: Estadísticas o datos sin fuente verificable
5. **Contradicción**: Inconsistencias con argumentos previos
6. **Argumento Circular**: La conclusión se asume en las premisas
7. **Hombre de Paja**: Distorsión del argumento del oponente
8. **Post Hoc**: Confundir correlación con causalidad

### 📊 Sistema de Análisis Detallado

Al finalizar cada debate, recibes un análisis completo que incluye:

- **Puntuación General**: Basada en múltiples métricas
- **Capacidad de Respuesta**: Participación activa
- **Calidad de Argumentación**: Desarrollo de ideas
- **Coherencia**: Consistencia en argumentos
- **Profundidad**: Nivel de análisis
- **Falacias Detectadas**: Errores en tus argumentos
- **Oportunidades Perdidas**: Errores de la IA que no detectaste
- **Recomendaciones Personalizadas**: Basadas en tu desempeño

### 🎨 Configuración Personalizable

#### Panel de Ajustes Completo

1. **Apariencia**
   - Tema: Claro, Oscuro o Automático
   - Tamaño de fuente: 12-20sp

2. **Debate**
   - Número de sets por defecto: 1-5
   - Mostrar indicador de progreso

3. **Privacidad**
   - Guardar historial de debates
   - Limpiar historial

4. **Notificaciones**
   - Sonidos habilitados
   - Vibración habilitada

5. **Acerca de**
   - Versión de la app
   - Reiniciar tutorial

### 🎓 Niveles de Debate

- **BÁSICO**: Ideal para principiantes
- **INTERMEDIO**: Para usuarios con experiencia
- **AVANZADO**: Requiere validación de fuentes académicas
- **EXPERTO**: Máximo nivel de exigencia

### 📚 Validación de Fuentes

En niveles **AVANZADO** y **EXPERTO**, la app valida las fuentes citadas:

✅ **Fuentes Confiables**: `.edu`, `.gov`, `.org`, revistas académicas
❌ **Fuentes No Confiables**: Wikipedia, blogs personales, redes sociales

### 🏆 Posturas Disponibles

1. **A Favor**
2. **En Contra**
3. **Moderador** (perspectiva neutral)

### 🎯 Estructura de Debate

Cada set incluye tres turnos:
1. **Presentación**: Argumento inicial
2. **Refutación**: Respuesta al oponente
3. **Apelación**: Argumento final

## 🚀 Cómo Usar

1. **Inicio**: La primera vez verás una pantalla de bienvenida
2. **Configurar Debate**: Presiona ➕ para crear un nuevo debate
   - Selecciona el tema
   - Elige tu postura
   - Define el nivel de dificultad
   - Configura número de sets
   - Decide quién empieza
3. **Debatir**: Intercambia argumentos con la IA
4. **Analizar**: Al finalizar, presiona 📊 para ver resultados detallados

### 💡 Consejos para Mejorar

- **Detecta Errores**: Presta atención a las falacias de la IA
- **Cuestiona Activamente**: Usa palabras como "falacia", "error", "incorrecto"
- **Desarrolla Argumentos**: Mensajes detallados obtienen mejor puntuación
- **Cita Fuentes**: En niveles altos, respalda tus argumentos con fuentes académicas
- **Sé Coherente**: Mantén consistencia en tus argumentos

## 🛠️ Tecnologías

- **Lenguaje**: Kotlin
- **UI**: Material Design 3
- **Arquitectura**: MVVM con LiveData
- **IA**: Google Gemini API
- **Almacenamiento**: SharedPreferences
- **Build System**: Gradle con Kotlin DSL

## 📱 Requisitos

- Android SDK 26 (Android 8.0) o superior
- Conexión a Internet para debates con IA
- API Key de Google Gemini (configurada en `local.properties`)

## 🔐 Seguridad

- Límite de uso: Máximo 50 peticiones por día
- Validación de fuentes académicas
- Detección automática de falacias lógicas

## 📈 Métricas de Evaluación

El sistema evalúa tu desempeño en tiempo real:

- **Penalización por Errores Perdidos**: -0.5 puntos por cada error no detectado (máx -3.0)
- **Detección de Falacias**: Reduce puntuación si cometes falacias
- **Longitud de Argumentos**: Mensajes muy cortos afectan la calidad
- **Participación**: Más mensajes = mejor capacidad de respuesta

## 🎓 Sistema Educativo

DebateIA no solo evalúa, **también enseña**:

1. **Errores Intencionales**: La IA comete errores calculados
2. **Feedback Inmediato**: Recomendaciones específicas por error
3. **Aprendizaje Progresivo**: Errores más sutiles en niveles altos
4. **Retroalimentación Narrativa**: Explicación detallada del desempeño

## 🌟 Próximas Características

- [ ] Historial de debates guardado
- [ ] Estadísticas de progreso a largo plazo
- [ ] Más temas predefinidos
- [ ] Modo multijugador
- [ ] Exportar análisis en PDF

## 👨‍💻 Desarrollo

Creado con ❤️ para mejorar el pensamiento crítico y las habilidades de debate.

---

**DebateIA** - Entrena tu mente, mejora tus argumentos. 🧠✨
