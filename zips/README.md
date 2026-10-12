# 📦 Carpeta de Sincronización Automática por ZIP (`zips/`)

Esta carpeta permite actualizar todo el código fuente de la rama actual directamente desde el teléfono celular sin necesitar PC ni usar comandos `git`.

## 📱 ¿Cómo funciona?

1. En GitHub desde tu navegador móvil, elige la rama que deseas actualizar (por ejemplo `main`, `beta` o cualquier otra rama).
2. Entra a esta carpeta **`zips/`** y toca **"Add file" -> "Upload files"**.
3. Sube tu archivo **`.zip`** con el proyecto actualizado y guarda los cambios.
4. **¡Listo!** El workflow **`Sincronizar Código desde ZIP`** (`.github/workflows/sync-repo-from-zip.yml`) se activará **únicamente cuando detecte un archivo `.zip` nuevo en esta carpeta**, y automáticamente:
   - Descomprimirá el archivo `.zip` de forma segura.
   - **Agregará** los archivos nuevos al repositorio.
   - **Actualizará** los archivos modificados.
   - **Eliminará** los archivos viejos que ya no existan en el `.zip`.
   - **Protegerá** la carpeta `.github/workflows` si el `.zip` no la incluye (y siempre preservará este workflow de sincronización).
   - **Borrará automáticamente el archivo `.zip`** de esta carpeta `zips/` tras aplicar los cambios para que el repositorio no pese de más.
