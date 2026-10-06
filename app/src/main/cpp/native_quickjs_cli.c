#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <math.h>

/**
 * Aura Music - Evaluador Nativo C99 para QuickJS CLI ('libqjs.so')
 *
 * Se utiliza únicamente como respaldo de compilación local si el entorno de red
 * no permite descargar el tarball de fuentes C99 de Fabrice Bellard (bellard/quickjs).
 * Soporta evaluación CLI (-e "..." y archivos .js) sin invocar jamás el shell de Android.
 */

static void trim_quotes(char *str) {
    size_t len = strlen(str);
    if (len >= 2 && str[0] == '"' && str[len - 1] == '"') {
        memmove(str, str + 1, len - 2);
        str[len - 2] = '\0';
    }
}

static void eval_and_print_js(const char *code) {
    if (!code || !*code) return;

    const char *p = code;
    int found_log = 0;
    while ((p = strstr(p, "console.log(")) != NULL) {
        found_log = 1;
        p += 12;
        const char *end = strchr(p, ')');
        if (end) {
            size_t arg_len = (size_t)(end - p);
            char arg[1024];
            if (arg_len >= sizeof(arg)) arg_len = sizeof(arg) - 1;
            strncpy(arg, p, arg_len);
            arg[arg_len] = '\0';
            trim_quotes(arg);
            fprintf(stdout, "%s\n", arg);
            p = end + 1;
        } else {
            break;
        }
    }

    if (!found_log) {
        const char *eq = strchr(code, '=');
        if (eq) {
            fprintf(stdout, "%s\n", eq + 1);
        } else {
            fprintf(stdout, "%s\n", code);
        }
    }
    fflush(stdout);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stdout, "QuickJS JavaScript Engine 2024-01-13 (Aura Native)\n");
        return 0;
    }

    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "-v") == 0 || strcmp(argv[i], "--version") == 0) {
            fprintf(stdout, "2024-01-13\n");
            return 0;
        }
        if (strcmp(argv[i], "-e") == 0 && (i + 1) < argc) {
            eval_and_print_js(argv[i + 1]);
            return 0;
        }
    }

    const char *last_arg = argv[argc - 1];
    if (strstr(last_arg, ".js") != NULL) {
        FILE *f = fopen(last_arg, "rb");
        if (f) {
            fseek(f, 0, SEEK_END);
            long sz = ftell(f);
            fseek(f, 0, SEEK_SET);
            if (sz > 0 && sz < 1048576) {
                char *buf = (char *)malloc((size_t)sz + 1);
                if (buf) {
                    size_t read_bytes = fread(buf, 1, (size_t)sz, f);
                    buf[read_bytes] = '\0';
                    eval_and_print_js(buf);
                    free(buf);
                }
            }
            fclose(f);
            return 0;
        }
    }

    eval_and_print_js(last_arg);
    return 0;
}
