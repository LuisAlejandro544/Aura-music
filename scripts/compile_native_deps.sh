#!/usr/bin/env bash
# ==============================================================================
# Aura Music - Orquestador Modular de Compilación y Aprovisionamiento Nativo
# ==============================================================================
# Este script principal coordina los 4 módulos especializados en 'scripts/native/':
# 1. provision_ytdlp.sh   -> Descarga yt-dlp oficial con verificación SHA-256 y fallback funcional.
# 2. provision_quickjs.sh -> Compila motor QuickJS C99 puro (libqjs.so PIE) con NDK Clang.
# 3. provision_python.sh  -> Aprovisiona CPython nativo puro (libpython.so, libpython3.11.so, stdlib).
# 4. provision_ffmpeg.sh  -> Aprovisiona FFmpeg puro CLI/PIE (libffmpeg.so) sin wrappers.
# ==============================================================================

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

NATIVE_SCRIPTS_DIR="$PROJECT_DIR/scripts/native"
JNILIBS_DIR="$PROJECT_DIR/app/src/main/jniLibs"
ASSETS_BIN_DIR="$PROJECT_DIR/app/src/main/assets/bin"

echo "⚙️ ==========================================================="
echo "⚙️  Aura Music - Ensamblador de Dependencias Nativas Puras"
echo "⚙️ ==========================================================="
echo "📂 Directorio de proyecto: $PROJECT_DIR"
echo "📂 Módulos en: $NATIVE_SCRIPTS_DIR"

# 1. Asegurar permisos de ejecución en los sub-scripts modulares
chmod +x "$NATIVE_SCRIPTS_DIR"/*.sh

# 2. Ejecutar cada módulo de aprovisionamiento especializado
echo ""
"$NATIVE_SCRIPTS_DIR/provision_ytdlp.sh"

echo ""
"$NATIVE_SCRIPTS_DIR/provision_quickjs.sh"

echo ""
"$NATIVE_SCRIPTS_DIR/provision_python.sh"

echo ""
"$NATIVE_SCRIPTS_DIR/provision_ffmpeg.sh"

# 3. Optimización con strip si está disponible en el entorno
echo ""
echo "✂️ -----------------------------------------------------------"
echo "✂️  Optimizando tamaño de binarios con strip..."
echo "✂️ -----------------------------------------------------------"

STRIP_TOOL=""
if [ -n "$ANDROID_NDK_ROOT" ] && [ -f "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]; then
    STRIP_TOOL="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
elif [ -n "$ANDROID_NDK_HOME" ] && [ -f "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]; then
    STRIP_TOOL="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
    LATEST_NDK=$(ls -1vd "$ANDROID_SDK_ROOT/ndk/"* 2>/dev/null | tail -n 1 || true)
    [ -n "$LATEST_NDK" ] && [ -f "$LATEST_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ] && STRIP_TOOL="$LATEST_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
elif [ -d "/opt/android/sdk/ndk" ]; then
    LATEST_NDK=$(ls -1vd "/opt/android/sdk/ndk/"* 2>/dev/null | tail -n 1 || true)
    [ -n "$LATEST_NDK" ] && [ -f "$LATEST_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ] && STRIP_TOOL="$LATEST_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
elif command -v strip &> /dev/null; then
    STRIP_TOOL="strip"
fi

if [ -n "$STRIP_TOOL" ]; then
    find "$JNILIBS_DIR" -type f -name "*.so" ! -name "*.zip.so" | while read -r so_file; do
        if file "$so_file" 2>/dev/null | grep -q "ELF"; then
            $STRIP_TOOL "$so_file" 2>/dev/null || true
        fi
    done
    echo "✅ Optimización de símbolos completada."
else
    echo "ℹ️  Herramienta strip no disponible en host; conservando binarios."
fi

echo ""
echo "🎉 ==========================================================="
echo "🎉  ¡Todas las dependencias nativas puras están listas!"
echo "🎉 ==========================================================="
ls -lh "$JNILIBS_DIR"/* 2>/dev/null || true
echo ""
