# Bruma

App Android para descargar vídeo y música desde un enlace, guardarlos con su carátula y reproducirlos sin conexión.

- **Lenguaje:** Kotlin · **Interfaz:** Jetpack Compose (estilo vidrio, modo claro y oscuro)
- **Motor:** yt-dlp + FFmpeg (librería youtubedl-android)
- **Descargas:** WorkManager en primer plano, cola, pausa/reanudar, reintentos automáticos
- **Biblioteca:** Room · **Reproductor:** Media3 (ExoPlayer) con listas, aleatorio, repetición y temporizador
- Android 8.0 o superior

---

## Cómo obtener el APK con GitHub (sin instalar nada)

### 1. Crear el repositorio
1. Entra a [github.com](https://github.com) con tu cuenta y toca **New** (botón verde) para crear un repositorio.
2. Nómbralo `bruma`, déjalo **privado** y **no** marques "Add a README".
3. Toca **Create repository**.

### 2. Subir el proyecto
1. En la página del repositorio vacío toca **uploading an existing file**.
2. Descomprime `Bruma-app.zip` en tu PC y arrastra **todo el contenido de la carpeta** (las carpetas `app`, `gradle`, `.github` y los archivos sueltos) a la página.
   - La carpeta `.github` está oculta en algunos sistemas. En Windows: Explorador → Vista → marcar "Elementos ocultos". En Mac: `Cmd + Shift + .`
3. Abajo toca **Commit changes**.

> Si la carpeta `.github` no se sube arrastrando, créala a mano: **Add file → Create new file**, escribe como nombre `.github/workflows/build.yml` y pega el contenido de ese archivo.

### 3. (Recomendado) Configurar la firma
Abre `Bruma-firma-PRIVADO.txt` (viene aparte, no está en el ZIP) y sigue sus pasos para crear los 4 secretos en
**Settings → Secrets and variables → Actions**. Así cada versión nueva se instala encima de la anterior sin perder tu biblioteca.
Si te lo saltas, igual funciona, pero para actualizar tendrás que desinstalar primero.

### 4. Compilar
1. Ve a la pestaña **Actions**. Si pregunta, toca **I understand my workflows, go ahead and enable them**.
2. Elige **Compilar APK** → **Run workflow** → **Run workflow**.
3. Espera unos 8 a 12 minutos hasta que salga el chulito verde.

### 5. Descargar e instalar en el celular
1. Desde el celular entra a tu repositorio → **Releases** (columna derecha).
2. Descarga **Bruma-1.0-arm64-v8a.apk** (sirve para casi todos los celulares de los últimos años).
3. Ábrelo. Android pedirá permitir "instalar apps desconocidas" para tu navegador o administrador de archivos: actívalo y vuelve.
4. Toca **Instalar**.

### Si la compilación falla
Entra a la ejecución con la ✗ roja → **build** → abre el paso **Compilar** y copia las últimas 40 líneas del registro. Con eso se puede corregir el error.

---

## Qué hace cada carpeta

```
app/src/main/java/com/bruma/app/
├── BrumaApp.kt            Arranque de la app y contenedor de dependencias
├── data/                  Base de datos (Room) y ajustes (DataStore)
├── engine/Engine.kt       Análisis de enlaces y armado de la orden de yt-dlp
├── download/              Cola, worker de descarga, reintentos, notificaciones, guardado en Música/Películas
├── library/               Biblioteca y listas de reproducción
├── player/                Servicio de reproducción (Media3) y conexión con la interfaz
└── ui/                    Pantallas: inicio, formatos, descargas, biblioteca, listas, reproductor, vídeo, ajustes
```

## Diferencias con el documento de especificación

- **Inyección de dependencias:** se usa un contenedor manual (`AppContainer`) en lugar de Hilt para que el proyecto compile más rápido y sea más fácil de leer.
- **Efecto vidrio:** se logra con paneles translucidos sobre manchas de color difuminadas. Funciona igual en todas las versiones de Android sin librerías extra.
- **Reordenar listas:** con botones subir/bajar en lugar de arrastrar.
- **Gestos de brillo y volumen en vídeo:** no están en esta versión; el vídeo usa los controles estándar de Media3 y soporta imagen en imagen (PiP).
- **Portadas desde MusicBrainz:** quedan para una versión siguiente; se usa la miniatura del vídeo recortada en cuadrado.

## Uso responsable

Descarga solo contenido propio, de dominio público, con licencia libre o que tengas permiso de guardar.
Bruma no rompe protecciones DRM ni descarga de servicios de suscripción. Por la política de Google Play sobre
YouTube, esta app no se puede publicar en Play Store; se distribuye como APK.
