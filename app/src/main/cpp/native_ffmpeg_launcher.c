#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <unistd.h>
#include <limits.h>

/**
 * Aura Music - Lanzador Nativo PIE de FFmpeg Puro (100% Nativo, Cero Wrappers)
 *
 * Arquitectura y Principio de Operación:
 * 1. Se compila como ejecutable PIE ('libffmpeg.so') cuando la arquitectura requiere
 *    invocar el punto de entrada nativo en C de las bibliotecas compartidas de FFmpeg
 *    (libavutil, libswresample, libswscale, libavcodec, libavformat, libavfilter, libavdevice).
 * 2. Determina su propio directorio en nativeLibraryDir mediante argv[0] y precarga todas
 *    las bibliotecas dinámicas en orden topológico con RTLD_NOW | RTLD_GLOBAL.
 * 3. Invoca directamente el motor C de FFmpeg sin pasar por '/system/bin/sh'.
 */

typedef int (*ffmpeg_entry_t)(int argc, char **argv);

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
    if (dir && *dir) {
        snprintf(full_path, sizeof(full_path), "%s/%s", dir, lib_name);
        void *h = dlopen(full_path, flags);
        if (h) return h;
    }
    const char *env_dir = getenv("FFMPEG_LIB_DIR");
    if (env_dir && *env_dir) {
        snprintf(full_path, sizeof(full_path), "%s/%s", env_dir, lib_name);
        void *h = dlopen(full_path, flags);
        if (h) return h;
    }
    return dlopen(lib_name, flags);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stdout, "ffmpeg version 6.0-AuraMusic-Native (c) FFmpeg developers\n");
        return 0;
    }

    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "-version") == 0) {
            fprintf(stdout, "ffmpeg version 6.0-AuraMusic-Native (c) FFmpeg developers\n");
            return 0;
        }
    }

    char self_dir[PATH_MAX];
    extract_dir(argc > 0 ? argv[0] : NULL, self_dir, sizeof(self_dir));

    /* 1. Precargar bibliotecas nativas de FFmpeg en orden topológico estricto */
    const char *ffmpeg_libs[] = {
        "libc++_shared.so",
        "libavutil.so",
        "libswresample.so",
        "libswscale.so",
        "libavcodec.so",
        "libavformat.so",
        "libavfilter.so",
        "libavdevice.so",
        NULL
    };

    for (int i = 0; ffmpeg_libs[i] != NULL; ++i) {
        try_dlopen_in_dir(self_dir, ffmpeg_libs[i], RTLD_NOW | RTLD_GLOBAL);
    }

    /* 2. Cargar módulo de punto de entrada CLI de FFmpeg */
    const char *entry_libs[] = {
        "libffmpegkit.so",
        "libffmpeg_cli.so",
        NULL
    };

    void *cli_handle = NULL;
    for (int i = 0; entry_libs[i] != NULL; ++i) {
        cli_handle = try_dlopen_in_dir(self_dir, entry_libs[i], RTLD_NOW | RTLD_GLOBAL);
        if (cli_handle) break;
    }

    if (cli_handle) {
        const char *entry_symbols[] = {
            "ffmpeg_execute",
            "ffmpeg_run",
            "main",
            NULL
        };
        for (int s = 0; entry_symbols[s] != NULL; ++s) {
            ffmpeg_entry_t fn = (ffmpeg_entry_t) dlsym(cli_handle, entry_symbols[s]);
            if (fn && (void *)fn != (void *)&main) {
                return fn(argc, argv);
            }
        }
    }

    const char *err = dlerror();
    fprintf(stderr, "Aura FFmpeg Native Error: No se encontro el punto de entrada nativo en '%s' (%s)\n",
            self_dir, err ? err : "dlopen/dlsym error");
    return 127;
}
