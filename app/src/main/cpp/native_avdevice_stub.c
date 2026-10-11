/**
 * Aura Music - Implementación Nativa Ligera de libavdevice_60 y libc++_shared
 * Reemplaza los binarios pesados (>2 MB) por implementaciones C17 puras (~4 KB)
 * con tabla de versiones ELF LIBAVDEVICE_60 exacta para que el linker de Android 14
 * resuelva todas las dependencias DT_NEEDED de las bibliotecas FFmpeg a 0ms.
 */
#include <stddef.h>

unsigned avdevice_version(void) {
    /* LIBAVDEVICE_VERSION_INT(60, 1, 100) */
    return (60U << 16) | (1U << 8) | 100U;
}

const char *avdevice_configuration(void) {
    return "--disable-devices --enable-pic";
}

const char *avdevice_license(void) {
    return "LGPL version 2.1 or later";
}

void avdevice_register_all(void) {
    /* Dispositivos de captura de hardware deshabilitados por privacidad y ligereza */
}

int avdevice_list_input_sources(const void *device, const char *device_name, void *device_options, void **device_list) {
    (void)device;
    (void)device_name;
    (void)device_options;
    if (device_list) *device_list = NULL;
    return -38; /* AVERROR(ENOSYS) */
}

int avdevice_list_output_sinks(const void *device, const char *device_name, void *device_options, void **device_list) {
    (void)device;
    (void)device_name;
    (void)device_options;
    if (device_list) *device_list = NULL;
    return -38; /* AVERROR(ENOSYS) */
}

void avdevice_free_list_devices(void **device_list) {
    if (device_list) *device_list = NULL;
}
