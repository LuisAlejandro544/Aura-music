// Aura Music · landing oficial (JS vanilla, sin build) v2
// Módulos: config → formato → descargas(Releases) → markdown → galería
//          → modal in-app → reveal → stickyCta → scrollProgress → tilt3D
(function () {
  "use strict";
  var OWNER = "LuisAlejandro544";
  var REPO  = "Aura-music";
  var API   = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases";
  var RAW   = "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/";

  // URLs de capturas reales (postimg u otro host). Vacío = galería oculta.
  var SCREENSHOTS = [];

  // Lo llena wireHashModal() en el INIT; applyRelease() lo usa si ya existe.
  var setShaMap = null;
  // Veredicto VirusTotal (web/data/vt.json, gratis y sin cuota).
  var lastVtMap = {};

  /* ── Formateo ─────────────────────────────────────────────── */
  function fmtInt(n) {
    if (!n && n !== 0) return "…";
    return n >= 1000
      ? (n / 1000).toFixed(1).replace(".", ",").replace(",0", "") + "K"
      : String(n);
  }
  function fmtMB(bytes) {
    if (!bytes) return "";
    return (bytes / 1048576).toFixed(1) + " MB";
  }

  /* ── fetch con timeout ────────────────────────────────────── */
  function fetchTimeout(url, ms, asJson, extraHeaders) {
    var ctrl = ("AbortController" in window) ? new AbortController() : null;
    var timer = null;
    if (ctrl) timer = setTimeout(function () { ctrl.abort(); }, ms || 10000);
    var opts = ctrl ? { signal: ctrl.signal, headers: extraHeaders || {} } : {};
    return fetch(url, opts).then(function (r) {
      if (timer) clearTimeout(timer);
      if (!r.ok) throw new Error("HTTP " + r.status);
      return asJson ? r.json() : r.text();
    }, function (err) {
      if (timer) clearTimeout(timer);
      throw err;
    });
  }

  /* ── 1. Descargas ─────────────────────────────────────────── */
  function wireDownloads() {
    var pill = document.getElementById("versionPill");
    var meta = document.getElementById("releaseMeta");
    function showPill(t) { if (pill) { pill.textContent = t; pill.hidden = false; } }

    var lastTotal = -1;
    function animateTotal(el, to) {
      if (to === lastTotal) return;
      var from = lastTotal < 0 ? to : lastTotal;
      lastTotal = to;
      if (!el) return;
      // Accesibilidad: sólo anunciamos el valor final
      el.setAttribute("aria-live", "off");
      el.classList.remove("tick");
      void el.offsetWidth;
      el.classList.add("tick");
      if (from === to || !("requestAnimationFrame" in window)) {
        el.textContent = "+" + fmtInt(to);
        el.setAttribute("aria-live", "polite");
        return;
      }
      var t0 = null;
      function step(ts) {
        if (!t0) t0 = ts;
        var k = Math.min(1, (ts - t0) / 600);
        el.textContent = "+" + fmtInt(Math.round(from + (to - from) * k));
        if (k < 1) {
          requestAnimationFrame(step);
        } else {
          el.setAttribute("aria-live", "polite");
        }
      }
      requestAnimationFrame(step);
    }

    function ago(iso) {
      var mins = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60000));
      if (!isFinite(mins)) return "";
      if (mins < 1)    return "ahora mismo";
      if (mins < 60)   return "hace " + mins + " min";
      if (mins < 2880) return "hace " + Math.round(mins / 60) + " h";
      return "hace " + Math.round(mins / 1440) + " días";
    }

    function applyRelease(rel) {
      showPill(rel.tag_name + " • " + (rel.name || "Aura Beta Nebula"));
      var statV = document.getElementById("statVersion");
      if (statV) statV.textContent = rel.tag_name;
      var statD = document.getElementById("statDownloads");
      var pub = "Publicado el " + new Date(rel.published_at).toLocaleDateString("es-ES")
              + " • " + (rel.assets || []).length + " archivos adjuntos";
      if (rel.generated_at) pub += " • actualizado " + ago(rel.generated_at);
      if (meta) meta.textContent = pub;
      var assets = rel.assets || [];
      if (statD) {
        var total = assets.reduce(function (s, a) { return s + (a.download_count || 0); }, 0);
        animateTotal(statD, total);
      }
      function findAsset(part) {
        return assets.find(function (a) { return (a.name || "").indexOf(part) !== -1; });
      }
      var map = {
        "arm64-v8a":   findAsset("arm64-v8a.apk"),
        "armeabi-v7a": findAsset("armeabi-v7a.apk"),
        "universal":   findAsset("universal.apk")
      };
      Object.keys(map).forEach(function (abi) {
        var a = map[abi];
        if (!a) return;
        var btn  = document.querySelector('.dl[data-abi="' + abi + '"]');
        var size = document.querySelector('[data-size="' + abi + '"]');
        if (btn)  btn.href = a.browser_download_url;
        if (size) size.textContent = a.name + " • " + fmtMB(a.size) + " • " + a.download_count + " descargas";
      });
      var sha     = findAsset("SHA256SUMS");
      var shaLink = document.getElementById("shaLink");
      var shaBadge = document.getElementById("shaBadge");
      if (sha) {
        // Verificación real: el badge solo enlaza si el SHA256SUMS existe en el release.
        if (shaLink) shaLink.href = sha.browser_download_url;
        if (shaBadge) { shaBadge.href = sha.browser_download_url; shaBadge.removeAttribute("aria-disabled"); }
      } else if (shaBadge) {
        shaBadge.setAttribute("aria-disabled", "true");
      }
      // Nota: los hashes vienen en stats.json (el Action los parsea del
      // SHA256SUMS). Descargar el SHA por navegador lo bloquea CORS.
      var label = document.getElementById("heroDownloadLabel");
      if (label) label.textContent = "Obtener APK Gratis";
      if (setShaMap) setShaMap(rel.sha256 || {}, lastVtMap);
    }

    function fail(first) {
      if (!first) return;
      showPill("Aura Beta • Nebula (ver Releases)");
      if (meta) meta.textContent = "No se pudo leer la API (límite de GitHub). Usa los botones: abren la página de Releases.";
    }

    function pickRel(list) {
      return (list || []).find(function (x) {
        return x.prerelease === true && String(x.tag_name || "").indexOf("-beta") !== -1;
      }) || (list || [])[0];
    }

    function saveCache(rel, etag) {
      try {
        localStorage.setItem("auraRel", JSON.stringify({ at: Date.now(), rel: rel, etag: etag || null }));
      } catch (e) { /* sin storage */ }
    }

    var relETag = null;
    try {
      var c0 = JSON.parse(localStorage.getItem("auraRel") || "null");
      if (c0 && c0.etag) relETag = c0.etag;
    } catch (e) { /* caché corrupta */ }

    var suspendedUntil = 0;
    var POLL_MS = 300000; // 5 min

    function claimPollSlot() {
      try {
        var last = parseInt(localStorage.getItem("auraPoll") || "0", 10);
        if (Date.now() - last < POLL_MS) return false;
        localStorage.setItem("auraPoll", String(Date.now()));
        return true;
      } catch (e) { return true; }
    }

    function fetchRelease() {
      var ctrl  = ("AbortController" in window) ? new AbortController() : null;
      var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 10000) : null;
      var hdrs  = relETag ? { "If-None-Match": relETag } : {};
      var opts  = ctrl ? { signal: ctrl.signal, headers: hdrs } : {};
      return fetch(API, opts).then(function (r) {
        if (timer) clearTimeout(timer);
        if (r.status === 304) { var e = new Error("not-modified"); e.notModified = true; throw e; }
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
      if (!first && !claimPollSlot()) return;
      if (Date.now() < suspendedUntil) return;
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

    var JSON_URL     = "./data/stats.json";
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
      try { return !!((JSON.parse(localStorage.getItem("auraRel") || "null") || {}).rel); }
      catch (e) { return false; }
    }

    // ── BUG FIX: siempre aplicamos el JSON en pantalla de inmediato (0ms)
    //    y luego refrescamos en background si es viejo.
    function tick(first) {
      if (!first && !claimPollSlot()) return;
      loadJson().then(function (j) {
        // Veredicto VT en paralelo (gratis); no bloquea el pintado.
        fetchTimeout("./data/vt.json", 10000, true).then(function (v) {
          if (v && v.tag_name === j.tag_name && v.files) lastVtMap = v.files;
        }).catch(function () { /* sin veredicto: el modal lo indica */ });
        applyRelease(j);          // ← siempre pinta; nunca bloquea la UI
        saveCache(j, relETag);
        if (!jsonFresh(j) && Date.now() >= suspendedUntil) {
          refresh(false);         // intento silencioso de actualizar
        }
      }).catch(function () {
        refresh(first && !hasCache());
      });
    }

    // Pintar al instante desde caché localStorage si existe
    try {
      var cached = JSON.parse(localStorage.getItem("auraRel") || "null");
      if (cached && cached.rel) applyRelease(cached.rel);
    } catch (e) { /* caché corrupta */ }

    tick(true);
    setInterval(function () { if (!document.hidden) tick(false); }, POLL_MS);
    document.addEventListener("visibilitychange", function () { if (!document.hidden) tick(false); });
  }

  /* ── 2. Markdown remoto ───────────────────────────────────── */
  function normalizeLinks(md) {
    // Convierte ../../releases → enlace GitHub y ./file.md → enlace raw GitHub
    var base = "https://github.com/" + OWNER + "/" + REPO;
    return md
      .replace(/\]\(\.\.\/\.\.\/releases([^)]*)\)/g, "](" + base + "/releases$1)")
      .replace(/\]\(\.\/([^)]+)\)/g, function (_, p1) {
        return "](" + base + "/blob/main/" + p1 + ")";
      });
  }

  function renderMd(url, elId, sliceLines) {
    var box = document.getElementById(elId);
    if (!box) return;
    fetchTimeout(url, 10000, false).then(function (md) {
      if (sliceLines) {
        // Recortamos en el sliceLines más cercano que no deje un encabezado huérfano
        var lines = md.split("\n");
        var cut   = Math.min(sliceLines, lines.length);
        // Retrocede hasta que la línea final no sea un encabezado vacío (##)
        while (cut > 1 && /^#{1,3}\s/.test(lines[cut - 1])) cut--;
        md = lines.slice(0, cut).join("\n");
      }
      md = normalizeLinks(md);
      if (window.marked) {
        box.innerHTML = window.marked.parse(md);
        // Abre todos los enlaces del md en nueva pestaña
        box.querySelectorAll("a[href]").forEach(function (a) {
          if (a.href && !a.href.startsWith("#")) {
            a.setAttribute("target", "_blank");
            a.setAttribute("rel", "noopener noreferrer");
          }
        });
      } else {
        box.textContent = md;
      }
    }).catch(function () {
      box.textContent = "Sin conexión a GitHub. Abre el archivo directamente desde el repositorio.";
    });
  }

  /* ── 3. Galería ───────────────────────────────────────────── */
  function renderGallery() {
    var sec = document.getElementById("galeria");
    var row = document.getElementById("galleryScroll");
    if (!sec || !row || !SCREENSHOTS.length) return;
    sec.hidden = false;
    SCREENSHOTS.forEach(function (src, i) {
      var img     = document.createElement("img");
      img.src     = src;
      img.alt     = "Captura de Aura Music " + (i + 1);
      img.className = "app-shot";
      img.loading = "lazy";
      row.appendChild(img);
    });
  }

  /* ── 4. Modal navegador in-app ────────────────────────────── */
  function wireBrowserModal() {
    var ua    = navigator.userAgent || navigator.vendor || window.opera || "";
    var inApp = ua.indexOf("TikTok")    > -1 ||
                ua.indexOf("FBAN")      > -1 ||
                ua.indexOf("FBAV")      > -1 ||
                ua.indexOf("Instagram") > -1;
    var modal = document.getElementById("browserModal");
    if (!modal) return;
    if (inApp) modal.classList.add("active");

    var stay = document.getElementById("btnStayHere");
    if (stay) stay.addEventListener("click", function () { modal.classList.remove("active"); });

    // BUG FIX: eliminar el fragmento hash para evitar dos '#' en el Intent URI
    var force = document.getElementById("btnForceChrome");
    if (force) force.addEventListener("click", function () {
      var url = (location.origin + location.pathname + location.search)
                  .replace(/^https?:\/\//, "");
      window.location.href = "intent://" + url + "#Intent;scheme=https;package=com.android.chrome;end;";
    });
  }

  /* ── 5. Reveal al hacer scroll (hero excluido) ────────────── */
  function wireReveal() {
    // El hero NO recibe .reveal → visible al instante sin parpadeo
    var els = document.querySelectorAll("main section:not(.hero), footer");
    if (!("IntersectionObserver" in window) || !els.length) return;
    els.forEach(function (el) { el.classList.add("reveal"); });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("active"); io.unobserve(e.target); }
      });
    }, { threshold: 0.07 });
    els.forEach(function (el) { io.observe(el); });
    // Seguridad: si algo falla, nada queda invisible
    setTimeout(function () { els.forEach(function (el) { el.classList.add("active"); }); }, 4000);
  }

  /* ── 6. Sticky CTA móvil ──────────────────────────────────── */
  function wireStickyCta() {
    var cta     = document.getElementById("stickyCta");
    var heroBtn = document.getElementById("heroDownloadBtn");
    var apks    = document.getElementById("apks");
    if (!cta || !heroBtn || !("IntersectionObserver" in window)) return;

    var pastHero = false;
    var atApks   = false;
    function paint() { cta.hidden = !(pastHero && !atApks); }

    // BUG FIX: pastHero = verdadero solo cuando el botón salió por ARRIBA
    new IntersectionObserver(function (e) {
      var rect = e[0].boundingClientRect;
      if (!e[0].isIntersecting && rect.top < 0) pastHero = true;
      if (e[0].isIntersecting) pastHero = false;
      paint();
    }).observe(heroBtn);

    if (apks) {
      new IntersectionObserver(function (e) {
        atApks = e[0].isIntersecting;
        paint();
      }, { threshold: 0.15 }).observe(apks);
    }
  }

  /* ── 7. Barra de progreso de scroll ─────────────────────────
     Actualiza --scroll-progress en el header via CSS custom property
  ──────────────────────────────────────────────────────────────── */
  function wireScrollProgress() {
    var header = document.querySelector(".site-header");
    if (!header) return;
    function update() {
      var docH    = document.documentElement.scrollHeight - window.innerHeight;
      var pct     = docH > 0 ? Math.min(100, (window.scrollY / docH) * 100) : 0;
      header.style.setProperty("--scroll-progress", pct.toFixed(1) + "%");
    }
    document.addEventListener("scroll", update, { passive: true });
    update();
  }

  /* ── 8. Tilt 3D suave en bento-cards y tarjetas APK ─────────
     Sigue el puntero dentro de la tarjeta, vuelve al soltar.
  ──────────────────────────────────────────────────────────────── */
  function wireTilt() {
    var cards = document.querySelectorAll(".bento-card, .card, .community-card");
    cards.forEach(function (card) {
      card.addEventListener("mousemove", function (e) {
        var r   = card.getBoundingClientRect();
        var cx  = r.left + r.width  / 2;
        var cy  = r.top  + r.height / 2;
        var dx  = (e.clientX - cx) / (r.width  / 2);   // -1..1
        var dy  = (e.clientY - cy) / (r.height / 2);   // -1..1
        var rot = 4; // grados máximos
        card.style.transform =
          "perspective(900px) rotateX(" + (-dy * rot) + "deg) rotateY(" + (dx * rot) + "deg) translateY(-4px)";
        card.style.transition = "transform .08s linear";
      });
      card.addEventListener("mouseleave", function () {
        card.style.transition = "transform .5s cubic-bezier(.2,.75,.25,1)";
        card.style.transform  = "";
      });
    });
  }

  /* ── 9. Efecto cursor spotlight en secciones ─────────────────
     Un gradiente sutil sigue el mouse dentro de cada sección.
  ──────────────────────────────────────────────────────────────── */
  function wireSpotlight() {
    var sections = document.querySelectorAll(".bento-section, .downloads-section, .communities-section");
    sections.forEach(function (sec) {
      sec.addEventListener("mousemove", function (e) {
        var r  = sec.getBoundingClientRect();
        var x  = ((e.clientX - r.left) / r.width  * 100).toFixed(1) + "%";
        var y  = ((e.clientY - r.top)  / r.height * 100).toFixed(1) + "%";
        sec.style.setProperty("--spot-x", x);
        sec.style.setProperty("--spot-y", y);
        sec.style.backgroundImage =
          "radial-gradient(320px circle at " + x + " " + y + ", rgba(139,92,246,.07) 0%, transparent 70%),"
          + sec._baseBg;
      });
      sec.addEventListener("mouseleave", function () {
        sec.style.backgroundImage = sec._baseBg || "";
      });
      // Guardar el fondo base calculado por CSS
      sec._baseBg = window.getComputedStyle(sec).backgroundImage;
    });
  }

  /* ── Modal de hashes SHA-256 + VirusTotal ──────────────────── */
  function wireHashModal() {
    var modal = document.getElementById("hashModal");
    var rows  = document.getElementById("hashRows");
    var badge = document.getElementById("shaBadge");
    var close = document.getElementById("btnCloseHash");
    var lastSha = {};
    var lastVt = {};
    function closeModal() { if (modal) modal.classList.remove("active"); }
    if (close) close.addEventListener("click", closeModal);
    if (modal) modal.addEventListener("click", function (e) { if (e.target === modal) closeModal(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeModal(); });
    function abiOf(n) {
      var m = String(n).match(/arm64-v8a|armeabi-v7a|universal/);
      return m ? m[0] : n;
    }
    function detail(i, names) {
      var n = names[i], h = lastSha[n];
      var row = document.createElement("div"); row.className = "hash-row";
      var ab = document.createElement("p"); ab.className = "hash-abi"; ab.textContent = abiOf(n);
      var fp = document.createElement("p"); fp.className = "hash-cap"; fp.textContent = n;
      var c = document.createElement("code"); c.className = "hash-code"; c.textContent = h;
      var info = lastVt[n] || {};
      var v = document.createElement("p");
      if (info.status === "clean" || info.status === "flagged") {
        v.className = "vt-status " + info.status;
        v.textContent = info.status === "clean"
          ? "✓ Limpio en VirusTotal · " + info.malicious + "/" + info.total
          : "⚠ " + info.malicious + " detecciones en VirusTotal · ver informe";
      } else {
        v.className = "vt-status pending";
        v.textContent = "No disponible por ahora.";
      }
      var btns = document.createElement("div"); btns.className = "hash-btns";
      var cp = document.createElement("button"); cp.type = "button"; cp.className = "mini-btn"; cp.textContent = "Copiar hash";
      cp.addEventListener("click", function () {
        function done() { cp.textContent = "¡Copiado!"; setTimeout(function () { cp.textContent = "Copiar hash"; }, 1500); }
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(h).then(done, done);
        else {
          var ta = document.createElement("textarea"); ta.value = h;
          document.body.appendChild(ta); ta.select();
          try { document.execCommand("copy"); } catch (err) { /* portapapeles no disponible */ }
          document.body.removeChild(ta); done();
        }
      });
      var vt = document.createElement("a"); vt.className = "mini-btn primary"; vt.target = "_blank"; vt.rel = "noopener";
      vt.href = (lastVt[n] && lastVt[n].permalink) || ("https://www.virustotal.com/gui/file/" + h);
      vt.textContent = "Abrir en VirusTotal";
      btns.appendChild(cp); btns.appendChild(vt);
      row.appendChild(ab); row.appendChild(fp); row.appendChild(c); row.appendChild(v); row.appendChild(btns);
      return row;
    }
    var railCur = 0, railNames = [];
    function railGo(i) {
      var rail = document.getElementById("hashRail");
      var dots = document.getElementById("hashDots");
      if (!rail || !railNames.length) return;
      railCur = (i + railNames.length) % railNames.length; // ruleta infinita
      if (rail.scrollTo) rail.scrollTo({ left: railCur * rail.clientWidth, behavior: "smooth" });
      else rail.scrollLeft = railCur * rail.clientWidth;
      if (dots) Array.from(dots.children).forEach(function (d, j) { d.classList.toggle("active", j === railCur); });
    }
    var prev = document.getElementById("hashPrev");
    var next = document.getElementById("hashNext");
    if (prev) prev.addEventListener("click", function () { railGo(railCur - 1); });
    if (next) next.addEventListener("click", function () { railGo(railCur + 1); });
    document.addEventListener("keydown", function (e) {
      if (!modal.classList.contains("active")) return;
      if (e.key === "ArrowLeft") railGo(railCur - 1);
      if (e.key === "ArrowRight") railGo(railCur + 1);
    });
    if (badge) badge.addEventListener("click", function (e) {
      e.preventDefault(); // el modal muestra el hash; el href queda como fallback
      var rail = document.getElementById("hashRail");
      var dots = document.getElementById("hashDots");
      if (!modal || !rail) return;
      rail.textContent = "";
      if (dots) dots.textContent = "";
      railNames = Object.keys(lastSha);
      if (!railNames.length) {
        var p = document.createElement("p");
        p.className = "dim";
        p.textContent = "Aún sin hashes del release actual.";
        rail.appendChild(p);
      }
      railNames.forEach(function (n, i) {
        var cell = document.createElement("div");
        cell.className = "hash-cell";
        cell.appendChild(detail(i, railNames));
        rail.appendChild(cell);
        if (dots) {
          var d = document.createElement("button");
          d.type = "button"; d.className = "hash-dot" + (i === 0 ? " active" : "");
          d.setAttribute("aria-label", abiOf(n));
          d.addEventListener("click", function () { railGo(i); });
          dots.appendChild(d);
        }
      });
      railCur = 0;
      if (rail.scrollTo) rail.scrollTo({ left: 0, behavior: "auto" });
      else rail.scrollLeft = 0;
      modal.classList.add("active");
    });
    // Sincroniza el punto activo al deslizar con el dedo.
    (function () {
      var rail = document.getElementById("hashRail");
      if (!rail) return;
      var tm = null;
      rail.addEventListener("scroll", function () {
        if (tm) clearTimeout(tm);
        tm = setTimeout(function () {
          var dots = document.getElementById("hashDots");
          railCur = Math.round(rail.scrollLeft / rail.clientWidth) || 0;
          if (dots) Array.from(dots.children).forEach(function (d, j) { d.classList.toggle("active", j === railCur); });
        }, 80);
      });
    })();
    return function (map, vt) { lastSha = map || {}; lastVt = (vt && vt.files) || vt || {}; };
  }

  /* ── INIT ──────────────────────────────────────────────────── */
  setShaMap = wireHashModal();
  wireDownloads();
  renderGallery();
  renderMd(RAW + "chanelog-beta.md", "changelogBox", 58);
  renderMd(RAW + "README.md",        "readmeBox",    53);
  wireBrowserModal();
  wireReveal();
  wireStickyCta();
  wireScrollProgress();

  // Diferimos el tilt y spotlight para no bloquear la carga inicial
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      wireTilt();
      wireSpotlight();
    });
  } else {
    wireTilt();
    wireSpotlight();
  }
})();
