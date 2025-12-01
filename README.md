<p align="center">
  <img src="https://github.com/kevincop6/AnimalRecognizer/blob/main/app/src/main/ic_launcher-playstore.png?raw=true" alt="AnimalRecognizer Logo" width="200"/>
</p>

<h1 align="center">AnimalRecognizer</h1>

<p align="center">
  <strong>Identificación de fauna silvestre de la Península de Osa mediante Inteligencia Artificial.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Estado-Prototipo-orange" alt="Estado" />
  <img src="https://img.shields.io/badge/Plataforma-Android-green" alt="Plataforma" />
  <img src="https://img.shields.io/badge/AI-TensorFlow%20Lite-blue" alt="IA" />
  <img src="https://img.shields.io/badge/Origen-Puerto%20Jiménez%2C%20CR-red" alt="Ubicación" />
</p>

---

## 📖 Sobre el Proyecto

**AnimalRecognizer** es una iniciativa nacida en **Puerto Jiménez, Costa Rica**, diseñada para conectar la tecnología con la biodiversidad única de la **Península de Osa**.

Esta aplicación móvil utiliza modelos de inteligencia artificial pre-entrenados (**TensorFlow Lite**) para permitir a los usuarios identificar especies de fauna silvestre en tiempo real. Su característica más importante es la **capacidad offline**: el reconocimiento y la consulta de datos funcionan sin necesidad de conexión a internet, una herramienta vital para exploradores y biólogos en zonas profundas de la selva.

Actualmente en fase de prototipo, el proyecto busca evolucionar hacia una herramienta de **Ciencia Ciudadana**, permitiendo recopilar datos científicos precisos sobre avistamientos locales.

---

## 🚀 Descargas y Versiones (Beta)

Este proyecto se encuentra actualmente en una fase **Beta** y está configurado exclusivamente para **pruebas en entorno local**.

Puedes encontrar el instalador (APK) y los recursos necesarios en nuestra sección de Releases:

### [🔗 Ir a Releases y Descargas](https://github.com/kevincop6/AnimalRecognizer/releases)

> [!IMPORTANT]
> **⚠️ Información para Pruebas e Instalación**
> 
> Para ejecutar esta aplicación, se requiere una configuración específica del entorno (como la dirección IP del servidor local y credenciales de base de datos) que **no se incluye en el repositorio público** por seguridad.
>
> Si deseas realizar pruebas de funcionamiento, por favor contacta al desarrollador para obtener los archivos de configuración (`properties`) y los scripts de base de datos necesarios.

---

## ✨ Funcionalidades Clave

* **🧠 IA en el Dispositivo:** Reconocimiento de imágenes procesado localmente en el teléfono (Edge AI) para una respuesta inmediata sin internet.
* **📚 Enciclopedia Taxonómica:** Fichas detalladas de cada animal que incluyen:
    * Estado de conservación.
    * Taxonomía completa (Reino, Clase, Orden, etc.).
    * Datos de distribución geográfica y orígenes.
* **📸 Red Social de Naturaleza (Meta):** Una plataforma integrada donde los usuarios pueden subir fotos y videos de sus hallazgos.
* **🔬 Aporte Científico:** El objetivo final es generar bases de datos de avistamientos reales para contribuir al estudio y conservación de las especies locales.

## 🛠️ Tecnologías Utilizadas

* **Desarrollo Móvil:** Android (Java/Kotlin).
* **Inteligencia Artificial:** TensorFlow Lite (Visión por computadora).
* **Gestión de Datos:** MySQL / MariaDB (Estructura relacional optimizada con JSON).

## 🗃️ Arquitectura de Datos

El núcleo del sistema se basa en una estructura de base de datos relacional diseñada para soportar tanto la información biológica compleja como la interacción social:

### 1. Catálogo Biológico (`animales`)
Tabla central que almacena el conocimiento científico. Utiliza campos **JSON** para manejar estructuras de datos flexibles:
* `taxonomia`: Almacena la clasificación biológica completa.
* `distribucion`: Datos geoespaciales y de hábitat.
* `descripcion`: Información textual detallada.

### 2. Comunidad (`usuarios` y `photo_profile`)
Sistema de gestión de usuarios que permite crear perfiles (con foto de avatar y biografía), clasificando a los miembros por `tipo` (ej. aficionado, investigador).

### 3. Registro de Avistamientos (`aportes`)
Es el motor social de la aplicación. Funciona como una tabla pivote que conecta a los **Usuarios** con los **Animales**:
* Permite documentar el encuentro con evidencia multimedia.
* Genera un historial de "quién vio qué" para futuros análisis de población y migración de especies.

## 🗺️ Futuro del Proyecto

Como prototipo desarrollado por un aficionado, la hoja de ruta incluye:
* [ ] Entrenamiento de un modelo de IA específico para especies endémicas de Osa.
* [ ] Implementación completa del módulo de video para avistamientos.
* [ ] Visualización de datos en mapas de calor según los `aportes`.

---
**Desarrollado con ❤️ desde el corazón de la biodiversidad en Costa Rica.**
