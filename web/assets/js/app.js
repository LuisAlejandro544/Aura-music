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

  // 1. Último Pre-Release "-beta": enlaza los 3 APKs + SHA256SUMS (caché 5 min).
  function wireDownloads() {
    var pill = document.getElementById("versionPill");
    var meta = document.getElementById("releaseMeta");
    function showPill(t) { if (pill) { pill.textContent = t; pill.hidden = false; } }
    var lastTotal = -1;
    // Anima el contador subiendo desde el valor anterior (sin recargar).
    function animateTotal(el, to) {
      if (to === lastTotal) return;
      var from = lastTotal < 0 ? to : lastTotal;
      lastTotal = to;
      if (!el) return;
      el.classList.remove("tick");
      void el.offsetWidth;
      el.classList.add("tick");
      if (from === to || !("requestAnimationFrame" in window)) { el.textContent = "+" + fmtInt(to); return; }
      var t0 = null;
      function step(ts) {
        if (!t0) t0 = ts;
        var k = Math.min(1, (ts - t0) / 600);
        el.textContent = "+" + fmtInt(Math.round(from + (to - from) * k));
        if (k < 1) requestAnimationFrame(step);
      }
      requestAnimationFrame(step);
    }
    function ago(iso) {
      var mins = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60000));
      if (!isFinite(mins)) return "";
      if (mins < 1) return "ahora mismo";
      if (mins < 60) return "hace " + mins + " min";
      if (mins < 2880) return "hace " + Math.round(mins / 60) + " h";
      return "hace " + Math.round(mins / 1440) + " días";
    }
    function applyRelease(rel) {
      showPill(rel.tag_name + " • " + (rel.name || "Aura Beta Nebula"));
      var statV = document.getElementById("statVersion");
      if (statV) statV.textContent = rel.tag_name;
      var statD = document.getElementById("statDownloads");
      var pub = "Publicado el " + new Date(rel.published_at).toLocaleDateString("es-ES") + " • " + (rel.assets || []).length + " archivos adjuntos";
      if (rel.generated_at) pub += " • actualizado " + ago(rel.generated_at);
      meta.textContent = pub;
      var assets = rel.assets || [];
      if (statD) {
        var total = assets.reduce(function (s, a) { return s + (a.download_count || 0); }, 0);
        animateTotal(statD, total);
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
    }
    function fail(first) {
      if (!first) return; // en refrescos de fondo se conservan los valores viejos
      showPill("Aura Beta • Nebula (ver Releases)");
      if (meta) meta.textContent = "No se pudo leer la API (límite de GitHub). Usa los botones: abren la página de Releases.";
    }
    function pickRel(list) {
      return (list || []).find(function (x) {
        return x.prerelease === true && String(x.tag_name || "").indexOf("-beta") !== -1;
      }) || (list || [])[0];
    }
    function saveCache(rel, etag) {
      try { localStorage.setItem("auraRel", JSON.stringify({ at: Date.now(), rel: rel, etag: etag || null })); } catch (e) { /* sin storage */ }
    }
    var relETag = null;
    try {
      var c0 = JSON.parse(localStorage.getItem("auraRel") || "null");
      if (c0 && c0.etag) relETag = c0.etag;
    } catch (e) { /* caché corrupta: se ignora */ }
    // Con ETag + 304 la re-consulta no consume cuota de la API (límite 60/h sin token).
    function fetchRelease() {
      var ctrl = ("AbortController" in window) ? new AbortController() : null;
      var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 10000) : null;
      var opts = ctrl ? { signal: ctrl.signal, headers: relETag ? { "If-None-Match": relETag } : {} } : {};
      return fetch(API, opts).then(function (r) {
        if (timer) clearTimeout(timer);
        if (r.status === 304) { var e304 = new Error("not-modified"); e304.notModified = true; throw e304; }
        if (!r.ok) {
          var eHttp = new Error("HTTP " + r.status);
          if (r.status === 403 || r.status === 429) {
            eHttp.rateLimited = true;
            var rst = parseInt(r.headers.get("x-ratelimit-reset") || "0", 10);
            eHttp.reset = rst ? rst * 1000 : 0;
          }
          throw eHttp;
        }
        relETag = r.headers.get("ETag") || relETag;
        return r.json().then(function (list) { return { list: list, etag: relETag }; });
      }, function (err) {
        if (timer) clearTimeout(timer);
        throw err;
      });
    }
    function refresh(first) {
      if (!first && !claimPollSlot()) return; // otra pestaña ya sondeó hace poco
      if (Date.now() < suspendedUntil) return; // en pausa hasta el reset de GitHub
      fetchRelease().then(function (res) {
        var rel = pickRel(res.list);
        if (!rel) throw new Error("sin releases");
        saveCache(rel, res.etag);
        applyRelease(rel);
      }).catch(function (err) {
        if (err && err.notModified) return;
        if (err && err.rateLimited) suspendedUntil = err.reset || (Date.now() + 3600000);
        fail(first);
      });
    }
    // Capas (en orden): 1) stats.json local (gratis, instantáneo),
    // 2) API en vivo si el JSON envejece, 3) caché anterior, 4) mensaje de error.
    var JSON_URL = "./data/stats.json";
    var JSON_MAX_AGE = 7200000; // 2 h
    function jsonFresh(j) {
      return !!(j && j.tag_name && j.assets && j.generated_at &&
        (Date.now() - Date.parse(j.generated_at) < JSON_MAX_AGE));
    }
    function loadJson() {
      return fetchTimeout(JSON_URL, 10000, true).then(function (j) {
        if (!j || !j.tag_name || !j.assets) throw new Error("json inválido");
        return j;
      });
    }
    function hasCache() {
      try { return !!(JSON.parse(localStorage.getItem("auraRel") || "null") || {}).rel; }
      catch (e) { return false; }
    }
    function tick(first) {
      if (!first && !claimPollSlot()) return;
      loadJson().then(function (j) {
        saveCache(j, relETag);
        if (jsonFresh(j) || Date.now() < suspendedUntil) { applyRelease(j); return; }
        refresh(false); // JSON viejo: verificar con la API
      }).catch(function () { refresh(first && !hasCache()); });
    }
    try {
      var cached = JSON.parse(localStorage.getItem("auraRel") || "null");
      if (cached && cached.rel) applyRelease(cached.rel); // pinta al instante
    } catch (e) { /* caché corrupta: se ignora */ }
    tick(true);
    // Anti-saturación (límite 60/h por visitante): JSON local primero (gratis),
    // API cada 5 min solo si el JSON envejece, turno entre pestañas por
    // localStorage y pausa hasta el reset que indique GitHub si hay 403/429.
    var POLL_MS = 300000;
    var suspendedUntil = 0;
    function claimPollSlot() {
      try {
        var last = parseInt(localStorage.getItem("auraPoll") || "0", 10);
        if (Date.now() - last < POLL_MS) return false;
        localStorage.setItem("auraPoll", String(Date.now()));
        return true;
      } catch (e) { return true; }
    }
    setInterval(function () { if (!document.hidden) tick(false); }, POLL_MS);
    document.addEventListener("visibilitychange", function () { if (!document.hidden) tick(false); });
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
  wireStickyCta();

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
  // CTA fijo en móvil: aparece al pasar el hero, se oculta en #apks.
  function wireStickyCta() {
    var cta = document.getElementById("stickyCta");
    var heroBtn = document.getElementById("heroDownloadBtn");
    var apks = document.getElementById("apks");
    if (!cta || !heroBtn || !("IntersectionObserver" in window)) return;
    var pastHero = false;
    var atApks = false;
    function paint() { cta.hidden = !(pastHero && !atApks); }
    new IntersectionObserver(function (e) { pastHero = !e[0].isIntersecting; paint(); }).observe(heroBtn);
    if (apks) new IntersectionObserver(function (e) { atApks = e[0].isIntersecting; paint(); }, { threshold: 0.15 }).observe(apks);
  }
})();
