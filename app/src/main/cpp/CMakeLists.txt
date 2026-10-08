# CMakeLists.txt para Aura Music - Motor Nativo de Audio DSP
# Estándar configurado: C++20 (Última versión recomendada para Android NDK con soporte de std::span y matemáticas modernas)
cmake_minimum_required(VERSION 3.22.1)

project("auramusic_dsp" LANGUAGES C CXX)

# Configurar C17 y C++20 como estándares del compilador NDK
set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Opciones de optimización de tamaño, inlining y eliminación de secciones muertas para procesamiento de señal de audio C++20
add_compile_options(
    -Os
    -flto
    -ffast-math
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wextra
)

add_link_options(
    -flto
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

# Definir la librería compartida nativa de audio
add_library(
    auramusic_dsp
    SHARED
    auramusic_dsp.cpp
)

# Enlazar con bibliotecas del sistema de Android (Log y OpenSLES / AAudio si se requieren a futuro)
find_library(log-lib log)
find_library(aaudio-lib aaudio)

target_link_libraries(
    auramusic_dsp
    PRIVATE
    ${log-lib}
    ${aaudio-lib}
)

target_include_directories(
    auramusic_dsp
    PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)
