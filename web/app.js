// Lógica de la landing: conecta con README.md, chanelog-beta.md y Releases (3 APKs).
// Sin build: JS vanilla + fetch a GitHub (raw + API pública, sin claves).
(function () {
  "use strict";
  var OWNER = "LuisAlejandro544";
  var REPO = "Aura-music";
  var API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases";
  var RAW = "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/";

  function fmtMB(bytes) {
    if (!bytes) return "";
    return (bytes / 1048576).toFixed(1) + " MB";
  }

  // 1. Último Pre-Release "-beta": enlaza los 3 APKs + SHA256SUMS.
  function wireDownloads() {
    var pill = document.getElementById("versionPill");
    var meta = document.getElementById("releaseMeta");
    var heroBtn = document.getElementById("heroDownloadBtn");
    fetch(API).then(function (r) {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.json();
    }).then(function (list) {
      var rel = (list || []).find(function (x) {
        return x.prerelease === true && String(x.tag_name || "").indexOf("-beta") !== -1;
      }) || (list || [])[0];
      if (!rel) throw new Error("sin releases");
      pill.textContent = "🌌 " + rel.tag_name + " • " + (rel.name || "Aura Beta Nebula");
      meta.textContent = "Publicado el " + new Date(rel.published_at).toLocaleDateString("es-ES") + " • " + (rel.assets || []).length + " archivos adjuntos";
      var assets = rel.assets || [];
      function findAsset(part) {
        return assets.find(function (a) { return (a.name || "").indexOf(part) !== -1; });
      }
      var map = {
        "arm64-v8a": findAsset("arm64-v8a.apk"),
        "armeabi-v7a": findAsset("armeabi-v7a.apk"),
        "universal": findAsset("universal.apk")
      };
      Object.keys(map).forEach(function (abi) {
        var a = map[abi];
        if (!a) return;
        var btn = document.querySelector('.dl[data-abi="' + abi + '"]');
        var size = document.querySelector('[data-size="' + abi + '"]');
        if (btn) btn.href = a.browser_download_url;
        if (size) size.textContent = "📦 " + a.name + " • " + fmtMB(a.size) + " • " + a.download_count + " descargas";
      });
      var sha = findAsset("SHA256SUMS");
      var shaLink = document.getElementById("shaLink");
      if (sha && shaLink) shaLink.href = sha.browser_download_url;
      var best = map["arm64-v8a"] || map.universal;
      if (best && heroBtn) {
        heroBtn.href = best.browser_download_url;
        heroBtn.textContent = "⬇️ Descargar " + best.name;
      }
    }).catch(function () {
      pill.textContent = "🌌 Aura Beta • Nebula (ver Releases)";
      meta.textContent = "No se pudo leer la API (límite de GitHub). Usa los botones: abren la página de Releases.";
    });
  }

  // 2. Render de Markdown remoto con marked (CDN). Recorta el README a su resumen inicial.
  function renderMd(url, elId, sliceLines) {
    var box = document.getElementById(elId);
    if (!box) return;
    fetch(url).then(function (r) {
      if (!r.ok) throw new Error("HTTP " + r.status);
      return r.text();
    }).then(function (md) {
      if (sliceLines) md = md.split("\n").slice(0, sliceLines).join("\n");
      if (window.marked) box.innerHTML = window.marked.parse(md);
      else box.textContent = md;
    }).catch(function () {
      box.textContent = "Sin conexión a GitHub. Abre el archivo directamente desde el repositorio.";
    });
  }

  wireDownloads();
  renderMd(RAW + "chanelog-beta.md", "changelogBox", 60);
  renderMd(RAW + "README.md", "readmeBox", 55);
})();
