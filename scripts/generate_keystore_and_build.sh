#!/usr/bin/env bash
# ==============================================================================
# Aura Music - Script Autónomo de Generación de Firma y Compilación Debug
# ==============================================================================
# Este script:
# 1. Elimina cualquier firma previa y genera un 'debug.keystore' RSA 2048-bit desde cero.
# 2. Verifica la presencia de Java JDK y las herramientas del compilador.
# 3. Compila el APK Debug de Aura Music con el motor nativo DSP C++20 integrado.
# ==============================================================================

set -e

echo "🎵 ==========================================================="
echo "🎵  Aura Music - Generación de Firma Limpia y Compilación APK"
echo "🎵 ==========================================================="

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

echo "📂 Directorio del proyecto: $PROJECT_DIR"

# 1. Comprobar Java y keytool
if ! command -v keytool &> /dev/null; then
    echo "❌ Error: 'keytool' no está instalado o no se encuentra en el PATH."
    echo "   Por favor instala OpenJDK 17 o superior."
    exit 1
fi

# 2. Obligar a generar la firma debug.keystore desde cero
KEYSTORE_FILE="$PROJECT_DIR/debug.keystore"

echo "🔑 -----------------------------------------------------------"
echo "🔑 Obligando a generar una firma de depuración desde cero..."
echo "🔑 -----------------------------------------------------------"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️  Eliminando firma debug anterior ($KEYSTORE_FILE)..."
    rm -f "$KEYSTORE_FILE"
fi

keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=AuraMusicDebug, OU=Engineering, O=AuraSound, L=Local, ST=State, C=US"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "✅ Nueva firma creada exitosamente:"
    echo "   Ruta: $KEYSTORE_FILE"
    echo "   Algoritmo: RSA 2048 bits"
    echo "   Validez: 10.000 días"
    echo "   Contraseña: 'android'"
else
    echo "❌ Error al generar la firma debug.keystore."
    exit 1
fi

# 3. Determinar herramienta de compilación (gradlew o gradle)
BUILD_TOOL=""
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    BUILD_TOOL="./gradlew"
elif command -v gradle &> /dev/null; then
    BUILD_TOOL="gradle"
else
    echo "❌ No se encontró 'gradle' ni './gradlew' en el sistema."
    exit 1
fi

echo "⚙️  -----------------------------------------------------------"
echo "⚙️  Herramienta de compilación detectada: $BUILD_TOOL"
echo "⚙️  Iniciando compilación del APK Debug con motor DSP C++20..."
echo "⚙️  -----------------------------------------------------------"

$BUILD_TOOL :app:assembleDebug --stacktrace

# 4. Verificar salida del APK
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo "🎉 ==========================================================="
    echo "🎉  ¡Compilación Exitosa! APK Debug Generado y Firmado"
    echo "🎉 ==========================================================="
    echo "📦 Archivo: $APK_PATH"
    echo "📏 Tamaño: $APK_SIZE"
    echo "🚀 Listo para instalar directamente en dispositivos Android o distribuir."
else
    echo "⚠️  Compilación finalizada. Revisa app/build/outputs/apk/debug/ para verificar el archivo."
fi
