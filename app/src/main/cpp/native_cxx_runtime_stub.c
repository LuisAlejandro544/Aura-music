/**
 * Aura Music - Implementación C17 ultra-ligera de soporte ABI C++ (libc++_shared.so ~4 KB)
 * Provee los símbolos mínimos de asignación y RTTI requeridos por libavcodec.so y el linker64
 * de Android 14 sin empaquetar el binario completo de 1.6 MB de libc++_shared.so.
 */
#include <stdlib.h>
#include <stdint.h>

void *_Znwm(size_t size) {
    return malloc(size ? size : 1);
}

void *_Znam(size_t size) {
    return malloc(size ? size : 1);
}

void *_Znaj(unsigned int size) {
    return malloc(size ? size : 1);
}

void *_Znwj(unsigned int size) {
    return malloc(size ? size : 1);
}

void _ZdlPv(void *ptr) {
    if (ptr) free(ptr);
}

void _ZdaPv(void *ptr) {
    if (ptr) free(ptr);
}

void _ZdlPvm(void *ptr, size_t size) {
    (void)size;
    if (ptr) free(ptr);
}

void _ZdlPvj(void *ptr, unsigned int size) {
    (void)size;
    if (ptr) free(ptr);
}

void __cxa_pure_virtual(void) {
    abort();
}

int __cxa_guard_acquire(uint64_t *guard_object) {
    if (!guard_object) return 0;
    uint8_t *initialized = (uint8_t *)guard_object;
    return (*initialized == 0) ? 1 : 0;
}

void __cxa_guard_release(uint64_t *guard_object) {
    if (guard_object) {
        uint8_t *initialized = (uint8_t *)guard_object;
        *initialized = 1;
    }
}

void __cxa_guard_abort(uint64_t *guard_object) {
    (void)guard_object;
}

/* Tablas RTTI mínimas referenciadas por libavcodec.so */
const void *_ZTVN10__cxxabiv117__class_type_infoE[8] = {0};
const void *_ZTVN10__cxxabiv120__si_class_type_infoE[8] = {0};
const void *_ZTVN10__cxxabiv121__vmi_class_type_infoE[8] = {0};
