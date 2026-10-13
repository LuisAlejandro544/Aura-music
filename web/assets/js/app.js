// Aura Music · landing oficial (JS vanilla, sin build).
// Módulos: config → formato → descargas(Releases) → markdown → galería → modal in-app → reveal.
(function () {
  "use strict";
  var OWNER = "LuisAlejandro544";
  var REPO = "Aura-music";
  var API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases";
  var RAW = "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/";

  // URLs de capturas reales (postimg u otro host). Vacío = galería oculta.
  var SCREENSHOTS = [
  ];

  function fmtInt(n) {
    if (!n && n !== 0) return "…";
    return n >= 1000 ? (n / 1000).toFixed(1).replace(".", ",").replace(",0", "") + "K" : String(n);
  }

  function fmtMB(bytes) {
    if (!bytes) return "";
    return (bytes / 1048576).toFixed(1) + " MB";
  }

  function fetchTimeout(url, ms, asJson) {
    var ctrl = ("AbortController" in window) ? new AbortController() : null;
    var timer = null;
    if (ctrl) {
      timer = setTimeout(function () { ctrl.abort(); }, ms || 10000);
    }
    var opts = ctrl ? { signal: ctrl.signal } : {};
    return fetch(url, opts).then(function (r) {
      if (timer) clearTimeout(timer);
      if (!r.ok) throw new Error("HTTP " + r.status);
      return asJson ? r.json() : r.text();
    }, function (err) {
      if (timer) clearTimeout(timer);
      throw err;
    });
  }

  // 1. Último Pre-Release "-beta": enlaza los 3 APKs + SHA256SUMS.
  function wireDownloads() {
    var pill = document.getElementById("versionPill");
    var meta = document.getElementById("releaseMeta");
    fetchTimeout(API, 10000, true).then(function (list) {
      var rel = (list || []).find(function (x) {
        return x.prerelease === true && String(x.tag_name || "").indexOf("-beta") !== -1;
      }) || (list || [])[0];
      if (!rel) throw new Error("sin releases");
      if (pill) pill.textContent = rel.tag_name + " • " + (rel.name || "Aura Beta Nebula");
      var statV = document.getElementById("statVersion");
      if (statV) statV.textContent = rel.tag_name;
      var statD = document.getElementById("statDownloads");
      meta.textContent = "Publicado el " + new Date(rel.published_at).toLocaleDateString("es-ES") + " • " + (rel.assets || []).length + " archivos adjuntos";
      var assets = rel.assets || [];
      if (statD) {
        var total = assets.reduce(function (s, a) { return s + (a.download_count || 0); }, 0);
        statD.textContent = "+" + fmtInt(total);
      }
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
        if (size) size.textContent = a.name + " • " + fmtMB(a.size) + " • " + a.download_count + " descargas";
      });
      var sha = findAsset("SHA256SUMS");
      var shaLink = document.getElementById("shaLink");
      if (sha && shaLink) shaLink.href = sha.browser_download_url;
      // El botón del hero baja a la sección de APKs; no se sobrescribe su href.
      var label = document.getElementById("heroDownloadLabel");
      if (label) label.textContent = "Obtener APK Gratis";
    }).catch(function () {
      if (pill) pill.textContent = "Aura Beta • Nebula (ver Releases)";
      meta.textContent = "No se pudo leer la API (límite de GitHub). Usa los botones: abren la página de Releases.";
    });
  }

  // 2. Render de Markdown remoto con marked (CDN). Recorta el README a su resumen inicial.
  function renderMd(url, elId, sliceLines) {
    var box = document.getElementById(elId);
    if (!box) return;
    fetchTimeout(url, 10000, false).then(function (md) {
      if (sliceLines) md = md.split("\n").slice(0, sliceLines).join("\n");
      if (window.marked) box.innerHTML = window.marked.parse(md);
      else box.textContent = md;
    }).catch(function () {
      box.textContent = "Sin conexión a GitHub. Abre el archivo directamente desde el repositorio.";
    });
  }

  wireDownloads();
  renderGallery();
  renderMd(RAW + "chanelog-beta.md", "changelogBox", 60);
  renderMd(RAW + "README.md", "readmeBox", 55);
  wireBrowserModal();
  wireReveal();

  // Galería opcional: solo se muestra si hay URLs configuradas arriba.
  function renderGallery() {
    var sec = document.getElementById("galeria");
    var row = document.getElementById("galleryScroll");
    if (!sec || !row || !SCREENSHOTS.length) return;
    sec.hidden = false;
    SCREENSHOTS.forEach(function (src, i) {
      var img = document.createElement("img");
      img.src = src;
      img.alt = "Captura de Aura Music " + (i + 1);
      img.className = "app-shot";
      img.loading = "lazy";
      row.appendChild(img);
    });
  }

  // Aviso de navegador in-app (TikTok / redes): la descarga falla ahí dentro.
  function wireBrowserModal() {
    var ua = navigator.userAgent || navigator.vendor || window.opera || "";
    var inApp = ua.indexOf("TikTok") > -1 || ua.indexOf("FBAN") > -1 ||
      ua.indexOf("FBAV") > -1 || ua.indexOf("Instagram") > -1;
    var modal = document.getElementById("browserModal");
    if (!modal) return;
    if (inApp) modal.classList.add("active");
    var stay = document.getElementById("btnStayHere");
    if (stay) stay.addEventListener("click", function () { modal.classList.remove("active"); });
    var force = document.getElementById("btnForceChrome");
    if (force) force.addEventListener("click", function () {
      var url = window.location.href.replace(/^https?:\/\//, "");
      window.location.href = "intent://" + url + "#Intent;scheme=https;package=com.android.chrome;end;";
    });
  }

  // Aparición de secciones al hacer scroll (mejora progresiva: sin JS todo visible).
  function wireReveal() {
    var els = document.querySelectorAll("main section, footer");
    if (!("IntersectionObserver" in window) || !els.length) return;
    els.forEach(function (el) { el.classList.add("reveal"); });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("active"); io.unobserve(e.target); } });
    }, { threshold: 0.08 });
    els.forEach(function (el) { io.observe(el); });
    // Seguridad: si algo falla, nada queda invisible.
    setTimeout(function () {
      els.forEach(function (el) { el.classList.add("active"); });
    }, 4000);
  }
})();
