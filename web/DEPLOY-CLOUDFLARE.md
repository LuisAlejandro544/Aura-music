# Despliegue en Cloudflare Pages (carpeta `web/`)

Guía en español para publicar la landing de descarga sin tocar workflows de GitHub.

## 1. Qué contiene `web/`

- `index.html`: landing con las 3 arquitecturas (arm64-v8a, armeabi-v7a, universal) y sus explicaciones.
- `assets/css/styles.css`: paleta Dark Luxury Neo-Glass OLED (fondo `#000000`, Nebula Violet `#8B5CF6`, Cyan `#00D2FF`, Rosa `#F472B6`).
- `assets/js/app.js`: JS vanilla que lee el último Pre-Release `-beta` vía API pública de GitHub y renderiza `README.md` + `chanelog-beta.md` en vivo (sin claves, sin build).
- `assets/img/`: logo (`logo.png`) y futuras capturas.
- `_headers`: caché de Cloudflare Pages.

## 2. Publicar (2 opciones, sin workflow)

### Opción A — Dashboard (recomendada, 3 minutos)

1. Entra a [dash.cloudflare.com](https://dash.cloudflare.com) → Workers y Pages → Create → Pages → Connect to Git.
2. Elige el repo `LuisAlejandro544/Aura-music`.
3. Configura SOLO esto:
   - **Project name:** `aura-music-descarga`
   - **Production branch:** `main`
   - **Framework preset:** `None`
   - **Root directory:** `web`
   - **Build command:** *(vacío)*
   - **Build output directory:** `.` (un punto)
4. Pulsa Save and Deploy. Listo: te da `https://aura-music-descarga.pages.dev`.

### Opción B — Subida directa (sin conectar Git)

1. En Pages → Create → Upload assets.
2. Comprime SOLO el contenido de `web/` en un `.zip` y súbelo.
3. Cada vez que cambie la Beta, repite la subida (los APKs siempre se leen desde GitHub Releases, no hace falta resubirlos).

## 3. Dominio propio (opcional)

Pages → Custom domains → Set up a custom domain → escribe `descarga.tudominio.com` y sigue el DNS que te indica Cloudflare.

## 4. Cómo se conecta con README y changelog

- `app.js` hace `fetch` a `https://raw.githubusercontent.com/.../main/README.md` y `chanelog-beta.md`: si editas esos archivos en `main`, la web los muestra solos sin redesplegar.
- Los 3 botones leen `https://api.github.com/repos/.../releases`, filtran el último `prerelease` con tag `-beta` y enlazan sus assets `*arm64-v8a.apk`, `*armeabi-v7a.apk`, `*universal.apk` + `SHA256SUMS.txt`.
- Si la API llega al límite (60 req/h sin login), los botones usan el fallback a la página de Releases: nunca quedan rotos.

## 5. Probar en local

```bash
cd web
python3 -m http.server 8080
# abre http://localhost:8080
```
