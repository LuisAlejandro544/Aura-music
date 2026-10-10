#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <unistd.h>
#include <limits.h>
#include <sys/stat.h>

/**
 * Aura Music - Lanzador Nativo PIE de CPython 3.11 (100% Puro, Cero Wrappers)
 *
 * Arquitectura y Principio de Operación:
 * 1. Se compila como ejecutable nativo PIE ('libpython.so') con '-pie -fPIE -Wl,-rpath,$ORIGIN'
 *    para las 4 arquitecturas soportadas (arm64-v8a, armeabi-v7a, x86_64, x86).
 * 2. Determina el directorio absoluto desde argv[0] (nativeLibraryDir en Android) para sortear
 *    las restricciones de namespace del enlazador dinámico Bionic en Android 10 a 15+.
 * 3. Precarga en orden topológico las bibliotecas compartidas reales de CPython:
 *    libcrypto, libssl, libsqlite3, libffi, libbz2, liblzma y libpython3.11.so.
 * 4. Invoca directamente Py_BytesMain(argc, argv) o Py_Main(argc, argv) en memoria nativa,
 *    sin invocar jamás '/system/bin/sh' ni 'system("python3")'.
 */

typedef int (*Py_BytesMain_t)(int argc, char **argv);

static int is_safe_shared_object_path(const char *path) {
    if (!path || path[0] != '/') return 0;
    if (strstr(path, "..") != NULL) return 0;

    struct stat st;
    if (stat(path, &st) != 0) return 0;
    if (!S_ISREG(st.st_mode)) return 0;
    if ((st.st_mode & S_IWOTH) != 0) return 0;
    uid_t my_uid = getuid();
    if (st.st_uid != 0 && st.st_uid != 1000 && st.st_uid != my_uid) return 0;
    return 1;
}

static void extract_dir(const char *argv0, char *out_dir, size_t max_len) {
    if (!argv0 || !*argv0) {
        strncpy(out_dir, ".", max_len - 1);
        out_dir[max_len - 1] = '\0';
        return;
    }
    const char *last_slash = strrchr(argv0, '/');
    if (!last_slash) {
        strncpy(out_dir, ".", max_len - 1);
        out_dir[max_len - 1] = '\0';
        return;
    }
    size_t len = (size_t)(last_slash - argv0);
    if (len >= max_len) {
        len = max_len - 1;
    }
    memcpy(out_dir, argv0, len);
    out_dir[len] = '\0';
}

static void *try_dlopen_in_dir(const char *dir, const char *lib_name, int flags) {
    char full_path[PATH_MAX];
    if (!lib_name || strchr(lib_name, '/') != NULL || strstr(lib_name, "..") != NULL) {
        return NULL;
    }
    if (dir && *dir) {
        snprintf(full_path, sizeof(full_path), "%s/%s", dir, lib_name);
        if (is_safe_shared_object_path(full_path)) {
            void *h = dlopen(full_path, flags);
            if (h) return h;
        }
    }
    return dlopen(lib_name, flags);
}

int main(int argc, char **argv) {
    char self_dir[PATH_MAX];
    extract_dir(argc > 0 ? argv[0] : NULL, self_dir, sizeof(self_dir));

    /* 1. Precargar dependencias nativas de CPython desde nativeLibraryDir */
    const char *support_libs[] = {
        "libc++_shared.so",
        "libffi.so",
        "libbz2.so",
        "liblzma.so",
        "libcrypto_chaquopy.so",
        "libcrypto.so",
        "libssl_chaquopy.so",
        "libssl.so",
        "libsqlite3_chaquopy.so",
        "libsqlite3.so",
        NULL
    };

    for (int i = 0; support_libs[i] != NULL; ++i) {
        try_dlopen_in_dir(self_dir, support_libs[i], RTLD_NOW | RTLD_GLOBAL);
    }

    /* 2. Cargar el motor principal de CPython desde nativeLibraryDir */
    const char *python_candidates[] = {
        "libpython3.11.so",
        "libpython3.12.so",
        "libpython3.13.so",
        "libpython3.10.so",
        "libpython3.so",
        NULL
    };

    void *py_handle = NULL;
    for (int i = 0; python_candidates[i] != NULL; ++i) {
        py_handle = try_dlopen_in_dir(self_dir, python_candidates[i], RTLD_NOW | RTLD_GLOBAL);
        if (py_handle) {
            break;
        }
    }

    if (!py_handle) {
        const char *err = dlerror();
        fprintf(stderr, "Aura CPython Launcher Error: No se pudo enlazar libpython3.11.so desde '%s' (%s)\n",
                self_dir, err ? err : "dlopen null");
        return 127;
    }

    /* 3. Resolver el punto de entrada oficial de CPython */
    Py_BytesMain_t py_entry = (Py_BytesMain_t) dlsym(py_handle, "Py_BytesMain");
    if (!py_entry) {
        py_entry = (Py_BytesMain_t) dlsym(py_handle, "Py_Main");
    }

    if (!py_entry) {
        const char *err = dlerror();
        fprintf(stderr, "Aura CPython Launcher Error: Simbolo Py_BytesMain/Py_Main no encontrado (%s)\n",
                err ? err : "dlsym null");
        return 126;
    }

    return py_entry(argc, argv);
}
