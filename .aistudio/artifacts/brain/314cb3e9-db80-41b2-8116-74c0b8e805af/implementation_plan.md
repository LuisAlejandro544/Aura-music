# Sincronización Automática de Código por Archivo ZIP (Mobile-First GitHub Sync)

Este sistema permite actualizar el código completo de cualquier rama del repositorio directamente desde un teléfono móvil subiendo un único archivo `.zip` dentro de la carpeta `zips/`, sin necesidad de usar una PC ni comandos `git` locales. Al detectar el archivo `.zip`, GitHub Actions descomprime el paquete, reemplaza el código del repositorio (creando archivos nuevos, actualizando modificados y eliminando los que ya no existan en el `.zip`), protege los workflows de `.github` y borra automáticamente el `.zip` para mantener el repositorio limpio.

## Revisión del Usuario y Decisiones Confirmadas

> [!IMPORTANT]
> Estas son las decisiones de diseño confirmadas para la automatización:

- **Rama de Destino Confirmada**: Los cambios se aplicarán **siempre en la misma rama** donde hayas subido el archivo `.zip` (ideal para trabajar en `main`, `beta` o cualquier rama experimental creada desde GitHub móvil).
- **Limpieza Automática del `.zip` Confirmada**: Una vez descomprimido y aplicado el nuevo código, el Action **eliminará automáticamente el archivo `.zip`** de la carpeta `zips/` en el mismo commit (dejando únicamente el marcador de la carpeta) para no inflar el peso del repositorio.
- **Protección Inteligente de `.github/workflows` Confirmada**: Si el `.zip` que subes no incluye la carpeta `.github/` (por ejemplo, si ciertas exportaciones omiten carpetas ocultas), el sistema conservará intacta tu carpeta `.github/workflows` actual para nunca borrar tus Actions por accidente. Si el `.zip` sí trae `.github/workflows`, actualizará los workflows y preservará el propio workflow de sincronización ZIP.

---

## 1. Visión General y Concepto Principal

- **Qué hace**: Crea una carpeta dedicada en la raíz del proyecto para recibir paquetes `.zip` con versiones nuevas o completas del código fuente. En cuanto GitHub detecta un `.zip` en esa carpeta en cualquier rama, se dispara automáticamente un flujo de trabajo que sincroniza todo el árbol del proyecto de forma exacta (`1:1`).
- **Para quién está diseñado**: Flujo 100% *Mobile-First* pensado para cuando descargas el `.zip` de tu proyecto desde el navegador de tu teléfono y quieres actualizar tu repositorio de GitHub o una rama específica usando únicamente el botón **"Upload files"** de la interfaz móvil de GitHub.
- **Valor clave**: Resuelve la limitación de GitHub en navegadores móviles (donde no se pueden subir carpetas enteras ni borrar decenas de archivos obsoletos a la vez), convirtiendo la subida de un solo `.zip` en una sincronización completa de rama.

---

## 2. Experiencia de Uso y Flujo Paso a Paso

- **Flujo del Usuario desde el Teléfono**:
  1. Descargas el archivo `.zip` del proyecto en tu celular.
  2. Entras a tu repositorio en GitHub desde el navegador del teléfono y seleccionas la rama que quieres actualizar (por ejemplo, `main` o una rama nueva).
  3. Abres la carpeta `zips/`, tocas **"Add file" -> "Upload files"**, seleccionas tu `.zip` y confirmas el commit.
  4. **GitHub Actions entra en acción automáticamente**:
     - Detecta el `.zip` recién subido.
     - Analiza su interior (incluso si el `.zip` viene envuelto dentro de una carpeta raíz contenedora o si trae los archivos directamente en la raíz).
     - Limpia los binarios pesados prohibidos (`.so`, `.apk`, `.gradle`, `build`) por si el `.zip` los traía por error.
     - Elimina del repositorio los archivos antiguos que ya no existan en el `.zip`, agrega los archivos nuevos y sobrescribe los modificados.
     - Elimina el archivo `.zip` subido para liberar espacio.
     - Hace commit y push automático en esa misma rama y genera un **Resumen Ejecutivo en Español** en la pestaña *Actions* indicando cuántos archivos se agregaron, modificaron y eliminaron.

---

## 3. Decisiones Técnicas y Ventajas

- **Detección Inteligente de Carpeta Contenedora (Root Folder Unwrapping)**:
  - *Enfoque elegido*: Al descomprimir en un directorio temporal, el motor verifica si todos los archivos están dentro de una única carpeta superior (ej. `Aura-Music-main/app/...`) o directamente en la raíz (`app/...`, `build.gradle.kts`). Si están dentro de una subcarpeta contenedora, aplana automáticamente ese nivel.
  - *Por qué*: Dependiendo de cómo se genere o comprima un `.zip` en Android o en plataformas en la nube, a veces se incluye una carpeta contenedora y a veces no. Esto garantiza que nunca se dupliquen carpetas por error.
- **Validación de Seguridad (Anti-Zip Slip y Filtro de Binarios)**:
  - *Enfoque elegido*: Antes de aplicar los archivos, se verifica que ninguna ruta del `.zip` intente escapar del directorio (`..`) y se excluyen automáticamente archivos temporales o binarios compilados (`.apk`, `.so`, `.zip.so`, `local.properties`) respetando las reglas del proyecto.
  - *Por qué*: Evita que un `.zip` sucio ensucie el historial de Git con binarios compilados que luego bloqueen la subida a GitHub.
- **Prevención de Bucles Infinitos en CI/CD**:
  - *Enfoque elegido*: El workflow se activa exclusivamente cuando se agrega o modifica un archivo `zips/*.zip`. Como el commit automático del propio Action borra el `.zip`, no vuelve a dispararse en bucle.

---

## 4. Arquitectura Técnica del Flujo de Sincronización

```
┌─────────────────────────────────────────────────────────────────────────┐
│             📱 Teléfono del Usuario (GitHub Web / Mobile)               │
│        Sube cualquier archivo .zip dentro de la carpeta zips/           │
│                  (en main o en cualquier otra rama)                     │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ Push detectado (zips/**/*.zip)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               ⚙️ GitHub Actions: Sync Repo from ZIP                     │
│  1. Checkout de la misma rama (${{ github.ref_name }}) con permisos RW  │
│  2. Localiza el archivo .zip más reciente dentro de zips/               │
│  3. Extrae en área segura temporal (/tmp/aura_zip_extract)              │
│  4. Detecta si hay carpeta raíz contenedora única y normaliza estructura│
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                🛡️ Reglas de Protección y Sincronización                 │
│  • Protege .git/ siempre intacto                                        │
│  • Protege .github/workflows/ si el .zip no trae carpeta .github        │
│  • Preserva zips/.gitkeep y elimina todos los zips/*.zip                │
│  • Filtra artefactos prohibidos (.apk, .so, .gradle/, build/)           │
│  • Sincroniza 1:1:                                                      │
│      - Borra archivos del repo que ya no existen en el .zip             │
│      - Crea archivos nuevos presentes en el .zip                        │
│      - Actualiza archivos modificados                                   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│         ✅ Commit Automático en la Rama + Reporte en Español            │
│  • Commit: "chore(sync): actualizar código desde paquete ZIP"           │
│  • Muestra tabla en $GITHUB_STEP_SUMMARY (Creados / Editados / Borrados)│
└─────────────────────────────────────────────────────────────────────────┘
```

### Componentes e Interacciones del Sistema
- **Carpeta Receptora (`zips/`)**: Incluye un archivo guía en español que mantiene la carpeta visible en GitHub para que siempre aparezca en el navegador del celular lista para recibir archivos `.zip`.
- **Reglas de `.gitignore`**: Se asegura que los archivos `.zip` dentro de `zips/` estén permitidos en Git para que puedas subirlos desde la web móvil sin bloqueos.
- **Motor de Sincronización en Python3 (integrado en el Workflow)**: Ejecuta la validación de integridad del archivo `.zip`, el cálculo exacto de diferencias (archivos añadidos, modificados y eliminados), la protección de `.github/workflows` y el borrado del `.zip` original en una sola operación atómica.
