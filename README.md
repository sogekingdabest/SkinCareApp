# SkinCare AI - Detector de Melanomas con IA

<div align="center">
  <img src="https://img.shields.io/badge/Android-API%2029+-brightgreen" alt="Android API">
  <img src="https://img.shields.io/badge/Kotlin-1.8+-blue" alt="Kotlin">
  <img src="https://img.shields.io/badge/TensorFlow%20Lite-2.17.0-orange" alt="TensorFlow Lite">
  <img src="https://img.shields.io/badge/OpenCV-4.x-red" alt="OpenCV">
  <img src="https://img.shields.io/badge/Firebase-33.1.0-yellow" alt="Firebase">
</div>

## Descripción

SkinCare AI es una aplicación móvil Android que utiliza inteligencia artificial avanzada para el análisis y detección temprana de melanomas. Combina un modelo de Machine Learning basado en **EfficientNetV2-B0** con los criterios clínicos **ABCDE** tradicionales para proporcionar evaluaciones precisas de lesiones cutáneas.

### Aviso Importante
Esta aplicación es una **herramienta de apoyo** y **NO sustituye** el criterio clínico de un profesional médico. Los resultados son únicamente informativos y siempre se debe consultar con un dermatólogo para evaluaciones definitivas.

## Características Principales

### Modelo de Inteligencia Artificial
- Modelo usado como base: **EfficientNetV2-B0**
- Entrenado con el DataSet **SIIM-ISIC Melanoma Classification**
- Optimizado con cuantificación para su uso en dispositivos de bajos recursos

### Análisis ABCDE Automatizado
- **A**simetría: Detección automática de irregularidades
- **B**ordes: Análisis de contornos y definición
- **C**olor: Evaluación de variaciones cromáticas
- **D**iámetro: Medición precisa del tamaño
- **E**volución: Seguimiento de cambios temporales

### Funcionalidades Clave
- Análisis en tiempo real con guías de captura inteligentes
- Procesamiento del analisis 100% local (sin envío de datos)
- Historial completo de análisis por lunar
- Seguimiento de evolución temporal
- Interfaz intuitiva con mapeo corporal
- Notificaciones de recordatorio personalizables
- Comparación de resultados IA vs. observación personal

### Stack Tecnológico
- **Lenguaje**: Kotlin
- **UI**: Material Design 3, View Binding
- **IA**: TensorFlow Lite 2.17.0
- **Visión**: OpenCV 4.x, CameraX
- **Backend**: Firebase (Auth, Firestore, Storage)

## Requisitos Mínimos del Sistema

- Android 10 (API 29) o superior
- RAM: 512MB mínimo, 1GB recomendado
- Almacenamiento: 50MB libres
- Cámara: 5MP mínimo

## Instalación y Configuración

### Prerrequisitos
1. Android Studio Arctic Fox o superior
2. JDK 17
3. Gradle 8.7
4. SDK de Android con API 29+

### Pasos de Instalación

1. **Clonar el repositorio**
```bash
git clone https://github.com/tu-usuario/skincare-ai.git
cd skincare-ai
```

2. **Configurar Firebase**
   - Crear proyecto en [Firebase Console](https://console.firebase.google.com)
   - Descargar `google-services.json`
   - Colocar en `app/` directory
   - Habilitar Authentication, Firestore y Storage
   - Recomendado seguir los pasos descritos por Firebase: https://firebase.google.com/docs/android/setup?hl=es

3. **Configurar OpenCV**
   - Descargar la versión 4.12.0 para android (https://opencv.org/releases/)
   - Descomprimirlo e importarlo en Android Studio
   - Sincronizar proyecto en Android Studio

4. **Compilar y ejecutar**
```bash
./gradlew assembleDebug
```

### Estructura del Proyecto
```
src/main/
├── java/es/monsteraltech/skincare_tfm/
│   ├── analysis/          # Módulos de análisis IA y ABCDE
│   ├── data/             # Gestión de datos y sesiones
│   ├── fragments/        # Fragmentos de UI
│   ├── utils/           # Utilidades y helpers
│   └── MainActivity.kt
├── res/
│   ├── layout/          # Layouts XML
│   ├── values/          # Strings, colores, estilos
│   └── drawable/        # Recursos gráficos
└── assets/
    └── models/          # Modelos TensorFlow Lite
```

---

<div align="center">
  <p><strong>Recuerda: Esta aplicación es una herramienta de apoyo. Siempre consulta con un profesional médico para diagnósticos definitivos.</strong></p>
  <p>Desarrollado con ❤️ para la detección temprana del melanoma</p>
</div>