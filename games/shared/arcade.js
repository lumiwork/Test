/* Pocket Arcade — shared runtime for every game page.
   Builds the top bar (back, title, HUD, mute), the stage, the overlay,
   and gives games: canvas fitting, a frame loop, pointer/touch input,
   keyboard, best scores (localStorage), tiny sound effects, haptics. */
(function () {
  'use strict';

  var BEST_PREFIX = 'arcade:best:';
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, String(v)); } catch (e) { /* storage unavailable */ } }
  };

  /* ---------- sound ---------- */
  var audio = null;
  var muted = store.get('arcade:mute') === '1';
  function ensureAudio() {
    if (audio) { if (audio.state === 'suspended') { audio.resume().catch(function () {}); } return; }
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (AC) { audio = new AC(); }
    } catch (e) { audio = null; }
  }
  document.addEventListener('pointerdown', ensureAudio, true);
  document.addEventListener('keydown', ensureAudio, true);

  function tone(freq, dur, type, vol, when, slideTo) {
    if (!audio) return;
    var t0 = audio.currentTime + (when || 0);
    var o = audio.createOscillator();
    var g = audio.createGain();
    o.type = type || 'square';
    o.frequency.setValueAtTime(freq, t0);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, t0 + dur);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(vol || 0.08, t0 + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g); g.connect(audio.destination);
    o.start(t0); o.stop(t0 + dur + 0.02);
  }
  var SFX = {
    tap: function () { tone(620, 0.06, 'square', 0.05); },
    jump: function () { tone(300, 0.12, 'square', 0.06, 0, 620); },
    score: function () { tone(880, 0.07, 'square', 0.06); tone(1320, 0.09, 'square', 0.06, 0.07); },
    hit: function () { tone(180, 0.18, 'sawtooth', 0.09, 0, 60); },
    over: function () { tone(440, 0.15, 'square', 0.07); tone(330, 0.15, 'square', 0.07, 0.15); tone(220, 0.3, 'square', 0.07, 0.3); },
    win: function () { tone(523, 0.1, 'square', 0.07); tone(659, 0.1, 'square', 0.07, 0.1); tone(784, 0.1, 'square', 0.07, 0.2); tone(1047, 0.3, 'square', 0.07, 0.3); },
    blip: function () { tone(1000, 0.04, 'sine', 0.05); },
    note: function (freq) { tone(freq, 0.35, 'triangle', 0.12); }
  };
  function sfx(name, arg) { if (muted || !audio) return; var f = SFX[name]; if (f) { try { f(arg); } catch (e) { /* ignore */ } } }

  function buzz(ms) { try { if (navigator.vibrate) navigator.vibrate(ms || 20); } catch (e) { /* ignore */ } }

  /* ---------- utils ---------- */
  function rand(a, b) { return a + Math.random() * (b - a); }
  function randInt(a, b) { return Math.floor(rand(a, b + 1)); }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
  function shuffle(arr) { for (var i = arr.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = arr[i]; arr[i] = arr[j]; arr[j] = t; } return arr; }
  function el(tag, cls) { var e = document.createElement(tag); if (cls) e.className = cls; return e; }

  var ICON_BACK = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7"/></svg>';
  var ICON_ON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9v6h4l5 4V5L8 9H4z"/><path d="M16 9a3.5 3.5 0 0 1 0 6"/><path d="M18.5 6.5a7 7 0 0 1 0 11"/></svg>';
  var ICON_OFF = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9v6h4l5 4V5L8 9H4z"/><path d="M16 9l5 6M21 9l-5 6"/></svg>';

  /* ---------- game shell ---------- */
  function create(opts) {
    opts = opts || {};
    var id = opts.id || 'game';
    var title = opts.title || document.title;
    var dim = opts.dim || '2d';
    document.body.dataset.dim = dim;

    var bar = el('header', 'bar');
    var back = el('a', 'back'); back.href = '../index.html'; back.setAttribute('aria-label', 'Back to arcade'); back.innerHTML = ICON_BACK;
    back.addEventListener('click', function (e) { if (window.history.length > 1 && document.referrer) { e.preventDefault(); window.history.back(); } });
    var h1 = el('h1'); h1.textContent = title;
    var hud = el('div', 'hud');
    var hudDef = opts.hud || { score: 'Score', best: 'Best' };
    var hudEls = {};
    Object.keys(hudDef).forEach(function (k) {
      var s = el('span'); var l = el('span', 'lbl'); l.textContent = hudDef[k]; var b = el('b'); b.textContent = '0';
      s.appendChild(l); s.appendChild(b); hud.appendChild(s); hudEls[k] = b;
    });
    var muteBtn = el('button', 'mute'); muteBtn.type = 'button';
    function paintMute() { muteBtn.innerHTML = muted ? ICON_OFF : ICON_ON; muteBtn.setAttribute('aria-label', muted ? 'Sound off' : 'Sound on'); }
    paintMute();
    muteBtn.addEventListener('click', function () { muted = !muted; store.set('arcade:mute', muted ? '1' : '0'); paintMute(); if (!muted) sfx('blip'); });
    bar.appendChild(back); bar.appendChild(h1); bar.appendChild(hud); bar.appendChild(muteBtn);

    var stage = el('main'); stage.id = 'stage';
    var canvas = null, ctx = null;
    if (opts.canvas !== false) { canvas = el('canvas'); stage.appendChild(canvas); ctx = canvas.getContext('2d'); }

    var overlay = el('div', 'overlay');
    var panel = el('div', 'panel');
    var ovTitle = el('h2'); var ovBig = el('div', 'big'); var ovText = el('p'); var ovBtn = el('button', 'btn'); ovBtn.type = 'button';
    panel.appendChild(ovTitle); panel.appendChild(ovBig); panel.appendChild(ovText); panel.appendChild(ovBtn);
    overlay.appendChild(panel); stage.appendChild(overlay);

    document.body.appendChild(bar);
    document.body.appendChild(stage);

    var api = {
      id: id, title: title, stage: stage, bar: bar, canvas: canvas, ctx: ctx, overlay: overlay,
      w: 1, h: 1, dpr: 1, score: 0, best: 0, keys: {}, store: store
    };
    var storedBest = parseFloat(store.get(BEST_PREFIX + id));
    api.best = isFinite(storedBest) ? storedBest : 0;
    if (hudEls.best) hudEls.best.textContent = api.best ? String(api.best) : '0';

    /* resize / canvas fit */
    var resizeFns = [];
    function fit() {
      var r = stage.getBoundingClientRect();
      api.w = Math.max(1, Math.round(r.width));
      api.h = Math.max(1, Math.round(r.height));
      api.dpr = Math.min(window.devicePixelRatio || 1, 2);
      if (canvas) {
        canvas.width = Math.round(api.w * api.dpr);
        canvas.height = Math.round(api.h * api.dpr);
        ctx.setTransform(api.dpr, 0, 0, api.dpr, 0, 0);
      }
      for (var i = 0; i < resizeFns.length; i++) resizeFns[i](api.w, api.h);
    }
    api.fit = fit;
    api.onResize = function (f) { resizeFns.push(f); f(api.w, api.h); };
    window.addEventListener('resize', fit);
    fit();

    /* HUD + scores */
    api.setHud = function (k, v) { if (hudEls[k]) hudEls[k].textContent = String(v); };
    api.setScore = function (n) {
      api.score = n;
      if (hudEls.score) hudEls.score.textContent = String(n);
    };
    api.flashHud = function (k) { var b = hudEls[k]; if (!b) return; b.classList.add('flash'); setTimeout(function () { b.classList.remove('flash'); }, 250); };
    api.updateBest = function (n) {
      var better = opts.lowerIsBetter ? (!api.best || n < api.best) : n > api.best;
      if (better) { api.best = n; store.set(BEST_PREFIX + id, n); }
      if (hudEls.best) hudEls.best.textContent = String(api.best);
      return better;
    };

    /* overlay */
    var pendingAction = null;
    api.show = function (o) {
      o = o || {};
      ovTitle.textContent = o.title || title;
      ovText.textContent = o.text || '';
      ovText.hidden = !o.text;
      ovBig.hidden = o.big == null;
      ovBig.textContent = o.big != null ? String(o.big) : '';
      ovBtn.textContent = o.button || 'Play';
      ovBtn.hidden = o.button === false;
      pendingAction = o.onButton || null;
      overlay.hidden = false;
      if (!ovBtn.hidden) { try { ovBtn.focus({ preventScroll: true }); } catch (e) { /* ignore */ } }
    };
    api.hide = function () { overlay.hidden = true; pendingAction = null; };
    api.visible = function () { return !overlay.hidden; };
    ovBtn.addEventListener('click', function () {
      var f = pendingAction; api.hide(); sfx('tap'); if (f) f();
    });

    var startFn = null;
    api.onStart = function (f) { startFn = f; };
    api.showStart = function (text) {
      api.show({ title: title, text: text || opts.hint || '', button: opts.startLabel || 'Play', onButton: function () { if (startFn) startFn(); } });
    };
    api.gameOver = function (o) {
      o = o || {};
      var s = o.score != null ? o.score : api.score;
      var isBest = api.updateBest(s);
      sfx(o.win ? 'win' : 'over');
      if (!o.win) buzz(60);
      var bestText = isBest && s ? 'New best!' : 'Best ' + (o.formatBest ? o.formatBest(api.best) : api.best);
      api.show({
        title: o.title || (o.win ? 'You win!' : 'Game over'),
        big: o.big != null ? o.big : s,
        text: (o.text ? o.text + ' ' : '') + bestText,
        button: o.button || 'Play again',
        onButton: function () { if (startFn) startFn(); }
      });
    };

    /* frame loop */
    var rafId = 0, last = 0, loopFn = null;
    function frame(t) {
      rafId = requestAnimationFrame(frame);
      var dt = last ? (t - last) / 1000 : 0;
      last = t;
      if (dt > 0.05) dt = 0.05;
      if (loopFn) loopFn(dt, t / 1000);
    }
    api.loop = function (f) { loopFn = f; if (!rafId) { last = 0; rafId = requestAnimationFrame(frame); } };
    api.stopLoop = function () { if (rafId) cancelAnimationFrame(rafId); rafId = 0; last = 0; };
    document.addEventListener('visibilitychange', function () { last = 0; });

    /* pointer input (single active pointer) */
    api.input = function (h) {
      var active = false, pid = null, sx = 0, sy = 0, lx = 0, ly = 0, st = 0;
      function pos(e) { var r = stage.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; }
      stage.addEventListener('pointerdown', function (e) {
        if (!overlay.hidden) return;
        if (active) return;
        if (e.pointerType === 'mouse' && e.button !== 0) return;
        active = true; pid = e.pointerId;
        try { stage.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ }
        var p = pos(e); sx = lx = p.x; sy = ly = p.y; st = performance.now();
        if (h.down) h.down(p.x, p.y, e);
        e.preventDefault();
      });
      stage.addEventListener('pointermove', function (e) {
        if (!active || e.pointerId !== pid) return;
        var p = pos(e);
        if (h.move) h.move(p.x, p.y, p.x - lx, p.y - ly, e);
        lx = p.x; ly = p.y;
        e.preventDefault();
      });
      function end(e) {
        if (!active || e.pointerId !== pid) return;
        active = false; pid = null;
        var p = pos(e), dx = p.x - sx, dy = p.y - sy, d = Math.hypot(dx, dy), ms = performance.now() - st;
        if (h.up) h.up(p.x, p.y, e);
        if (d < 12 && ms < 400) { if (h.tap) h.tap(p.x, p.y); }
        else if (d >= 24 && ms < 700) { if (h.swipe) h.swipe(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up'), dx, dy); }
      }
      stage.addEventListener('pointerup', end);
      stage.addEventListener('pointercancel', end);
      stage.addEventListener('lostpointercapture', function (e) { if (active && e.pointerId === pid) { active = false; pid = null; if (h.up) h.up(lx, ly, e); } });
    };

    /* keyboard */
    var keyFns = [];
    api.onKey = function (f) { keyFns.push(f); };
    window.addEventListener('keydown', function (e) {
      api.keys[e.key] = true;
      if (!overlay.hidden) { if ((e.key === ' ' || e.key === 'Enter') && !ovBtn.hidden) { e.preventDefault(); ovBtn.click(); } return; }
      if ([' ', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].indexOf(e.key) >= 0) e.preventDefault();
      if (e.repeat) return;
      for (var i = 0; i < keyFns.length; i++) keyFns[i](e.key, e);
    });
    window.addEventListener('keyup', function (e) { api.keys[e.key] = false; });
    window.addEventListener('blur', function () { api.keys = {}; });

    /* on-screen control row */
    api.controls = function (buttons, handler) {
      var row = el('footer', 'controls');
      buttons.forEach(function (b) {
        var btn = el('button'); btn.type = 'button'; btn.textContent = b.label; btn.setAttribute('aria-label', b.title || b.label);
        var downPid = null;
        btn.addEventListener('pointerdown', function (e) { if (downPid !== null) return; downPid = e.pointerId; try { btn.setPointerCapture(e.pointerId); } catch (err) { /* ignore */ } btn.classList.add('on'); handler(b.id, 'down'); e.preventDefault(); });
        function up(e) { if (downPid !== e.pointerId) return; downPid = null; btn.classList.remove('on'); handler(b.id, 'up'); }
        btn.addEventListener('pointerup', up); btn.addEventListener('pointercancel', up); btn.addEventListener('lostpointercapture', up);
        btn.addEventListener('contextmenu', function (e) { e.preventDefault(); });
        row.appendChild(btn);
      });
      document.body.appendChild(row);
      fit();
      return row;
    };

    /* a DOM board instead of (or on top of) the canvas */
    api.boardWrap = function () { var w = el('div', 'board-wrap'); stage.insertBefore(w, overlay); return w; };

    api.sfx = sfx; api.buzz = buzz;
    stage.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    return api;
  }

  /* ---------- Three.js helper ---------- */
  function three(api, o) {
    o = o || {};
    var T = window.THREE;
    var renderer = new T.WebGLRenderer({ antialias: o.antialias !== false, alpha: false, powerPreference: 'high-performance' });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    if (o.shadows) { renderer.shadowMap.enabled = true; renderer.shadowMap.type = T.PCFSoftShadowMap; }
    api.stage.insertBefore(renderer.domElement, api.stage.firstChild);
    var scene = new T.Scene();
    scene.background = new T.Color(o.bg != null ? o.bg : 0x120f22);
    if (o.fog) scene.fog = new T.Fog(o.bg != null ? o.bg : 0x120f22, o.fog[0], o.fog[1]);
    var camera = new T.PerspectiveCamera(o.fov || 60, 1, o.near || 0.1, o.far || 300);
    api.onResize(function (w, h) { renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); });
    api.fit();
    return { renderer: renderer, scene: scene, camera: camera };
  }

  window.Arcade = {
    create: create, three: three, sfx: sfx, buzz: buzz, store: store, BEST_PREFIX: BEST_PREFIX,
    rand: rand, randInt: randInt, clamp: clamp, lerp: lerp, pick: pick, shuffle: shuffle
  };
})();
