#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <unistd.h>
#include <limits.h>
#include <sys/stat.h>

/**
 * Aura Music - Lanzador y Motor CLI Nativo PIE de FFmpeg (100% C17 Nativo, Cero Dependencia de libffmpegkit_abidetect.so)
 *
 * Arquitectura y Principio de Operación:
 * 1. Se compila como ejecutable PIE ('libffmpeg.so') instalado por Android en nativeLibraryDir.
 * 2. Precarga en orden topológico estricto todas las bibliotecas compartidas requeridas por el linker64
 *    de Android 14 (libc++_shared.so, libavutil.so, libswresample.so, libswscale.so, libavcodec.so,
 *    libavformat.so, libavfilter.so, libavdevice.so y libffmpeg_cli.so) usando RTLD_NOW | RTLD_GLOBAL.
 * 3. Si cualquier biblioteca reporta un error en dlopen(), imprime el dlerror() real de esa biblioteca
 *    en lugar de perder el mensaje original.
 * 4. Invoca directamente el motor C de FFmpeg (ffmpeg_execute) pasando argc y argv sin depender
 *    de libffmpegkit_abidetect.so ni de wrappers Java/JNI obsoletos.
 */

typedef int (*ffmpeg_entry_t)(int argc, char **argv);
typedef void (*av_log_set_level_t)(int level);

static int is_safe_shared_object_path(const char *path) {
    if (!path || path[0] != '/') return 0;
    if (strstr(path, "..") != NULL) return 0;

    struct stat st;
    if (stat(path, &st) != 0) return 0;
    if (!S_ISREG(st.st_mode)) return 0;
    /* Bloquear cualquier archivo escribible por otros usuarios (world-writable) */
    if ((st.st_mode & S_IWOTH) != 0) return 0;
    /* El archivo debe pertenecer al sistema (root/system) o al propio UID del sandbox */
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

static void *try_dlopen_in_dir(const char *dir, const char *lib_name, int flags, char *err_buf, size_t err_buf_len) {
    char full_path[PATH_MAX];
    if (!lib_name || strchr(lib_name, '/') != NULL || strstr(lib_name, "..") != NULL) {
        return NULL;
    }
    dlerror(); /* Limpiar error previo */
    if (dir && *dir) {
        snprintf(full_path, sizeof(full_path), "%s/%s", dir, lib_name);
        if (is_safe_shared_object_path(full_path)) {
            void *h = dlopen(full_path, flags);
            if (h) return h;
            const char *e = dlerror();
            if (e && err_buf && err_buf_len > 0) {
                snprintf(err_buf, err_buf_len, "%s: %s", lib_name, e);
            }
        }
    }
    const char *env_dir = getenv("FFMPEG_LIB_DIR");
    if (env_dir && *env_dir && env_dir[0] == '/' && strstr(env_dir, "..") == NULL) {
        snprintf(full_path, sizeof(full_path), "%s/%s", env_dir, lib_name);
        if (is_safe_shared_object_path(full_path)) {
            void *h = dlopen(full_path, flags);
            if (h) return h;
            const char *e = dlerror();
            if (e && err_buf && err_buf_len > 0 && err_buf[0] == '\0') {
                snprintf(err_buf, err_buf_len, "%s: %s", lib_name, e);
            }
        }
    }
    void *h = dlopen(lib_name, flags);
    if (!h) {
        const char *e = dlerror();
        if (e && err_buf && err_buf_len > 0 && err_buf[0] == '\0') {
            snprintf(err_buf, err_buf_len, "%s: %s", lib_name, e);
        }
    }
    return h;
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

    /* 1. Precargar bibliotecas nativas de FFmpeg en orden topológico estricto (incluyendo variantes NEON de 32 bits) */
    const char *ffmpeg_libs[] = {
        "libc++_shared.so",
        "libavutil.so",
        "libavutil_neon.so",
        "libswresample.so",
        "libswresample_neon.so",
        "libswscale.so",
        "libswscale_neon.so",
        "libavcodec.so",
        "libavcodec_neon.so",
        "libavformat.so",
        "libavformat_neon.so",
        "libavfilter.so",
        "libavfilter_neon.so",
        "libavdevice.so",
        "libavdevice_neon.so",
        NULL
    };

    char first_load_err[512];
    first_load_err[0] = '\0';

    for (int i = 0; ffmpeg_libs[i] != NULL; ++i) {
        char step_err[256];
        step_err[0] = '\0';
        void *h = try_dlopen_in_dir(self_dir, ffmpeg_libs[i], RTLD_NOW | RTLD_GLOBAL, step_err, sizeof(step_err));
        if (!h && first_load_err[0] == '\0' && step_err[0] != '\0' && strstr(ffmpeg_libs[i], "_neon") == NULL) {
            strncpy(first_load_err, step_err, sizeof(first_load_err) - 1);
            first_load_err[sizeof(first_load_err) - 1] = '\0';
        }
    }

    /* 2. Cargar módulo de punto de entrada CLI de FFmpeg (libffmpeg_cli.so o libffmpeg_cli_neon.so) */
    const char *entry_libs[] = {
        "libffmpeg_cli.so",
        "libffmpeg_cli_neon.so",
        "libffmpegkit.so",
        NULL
    };

    void *cli_handle = NULL;
    char cli_load_err[512];
    cli_load_err[0] = '\0';

    for (int i = 0; entry_libs[i] != NULL; ++i) {
        char step_err[256];
        step_err[0] = '\0';
        cli_handle = try_dlopen_in_dir(self_dir, entry_libs[i], RTLD_NOW | RTLD_GLOBAL, step_err, sizeof(step_err));
        if (cli_handle) break;
        if (cli_load_err[0] == '\0' && step_err[0] != '\0') {
            strncpy(cli_load_err, step_err, sizeof(cli_load_err) - 1);
            cli_load_err[sizeof(cli_load_err) - 1] = '\0';
        }
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

    fprintf(stderr, "Aura FFmpeg Native Error: No se encontro el punto de entrada nativo en '%s' (dep_err='%s', cli_err='%s')\n",
            self_dir,
            first_load_err[0] ? first_load_err : "none",
            cli_load_err[0] ? cli_load_err : "none");
    return 127;
}
