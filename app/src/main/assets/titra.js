(() => {
  if (window.__tsLoaded) return; window.__tsLoaded = true;

  // ---------- 1) Kap skedarët e titrave (.vtt / .srt) që shkarkon player-i ----------
  let fileCues = [];
  const toSec = t => { const p = t.trim().replace(',', '.').split(':').map(Number);
    return p.length === 3 ? p[0] * 3600 + p[1] * 60 + p[2] : p[0] * 60 + p[1]; };
  function parseSubs(txt) {
    if (!txt || txt.indexOf('-->') < 0) return;
    const cues = [];
    txt.replace(/\r/g, '').split(/\n\s*\n/).forEach(block => {
      const lines = block.split('\n');
      const i = lines.findIndex(l => l.includes('-->'));
      if (i < 0) return;
      const [a, b] = lines[i].split('-->');
      const text = lines.slice(i + 1).join('\n').replace(/<[^>]+>|\{[^}]+\}/g, '').trim();
      if (text) cues.push({ start: toSec(a), end: toSec(b.trim().split(' ')[0]), text });
    });
    if (cues.length > 3) fileCues = cues;
  }
  const isSub = u => /\.(vtt|srt)(\?|$)|subtitle|caption/i.test(String(u));
  try {
    const of = window.fetch;
    window.fetch = function (input) {
      const p = of.apply(this, arguments);
      const u = typeof input === 'string' ? input : (input && input.url);
      if (isSub(u)) p.then(r => r.clone().text()).then(parseSubs).catch(() => {});
      return p;
    };
    const oo = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (m, u) {
      if (isSub(u)) this.addEventListener('load', () => {
        try { parseSubs(typeof this.response === 'string' ? this.response : this.responseText); } catch (e) {}
      });
      return oo.apply(this, arguments);
    };
  } catch (e) {}

  // ---------- 2) Titrat që faqja i shfaq në HTML ----------
  const SELECTORS = ['.ytp-caption-segment', '.captions-text', '.player-timedtext-text-container',
    '.atvwebplayersdk-captions-text', '.dss-subtitle-renderer-line', '[data-testid="CueBoxContainer"]',
    '.vjs-text-track-display div div', '.jw-text-track-cue', '.plyr__caption', '.shaka-text-container span',
    '.art-subtitle', '.dplayer-subtitle', '.fp-captions', '.mejs-captions-text', '.clappr-subtitle'];
  const GENERIC = '[class*="subtitle" i],[class*="caption" i],[class*="-cue" i],[id*="subtitle" i],[class*="srt" i]';

  const css = '#ts-overlay{position:fixed;z-index:2147483647;pointer-events:none;text-align:center;' +
    'white-space:pre-line;line-height:1.3;color:#fff;font:600 34px Arial,sans-serif;' +
    'text-shadow:0 0 4px #000,0 0 4px #000,2px 2px 2px #000}' +
    '#ts-badge{position:fixed;z-index:2147483647;top:10px;right:10px;pointer-events:none;' +
    'background:rgba(0,0,0,.7);color:#fff;font:14px Arial;padding:4px 8px;border-radius:6px}' +
    '.ts-hidden{opacity:0!important}';

  const cache = new Map(), pending = new Map();
  let last = '', overlay, badge, nextId = 1, found = false, startedAt = Date.now();

  window.__tsDone = (id, text) => { const cb = pending.get(id); pending.delete(id); cb && cb(text); };
  const translate = t => cache.has(t) ? Promise.resolve(cache.get(t)) : new Promise(res => {
    const id = nextId++;
    pending.set(id, out => { cache.set(t, out); res(out); });
    try { TitraShqip.translate(t, id); } catch (e) { res(t); }
  });

  function video() {
    let best = null, a = 0;
    document.querySelectorAll('video').forEach(v => { const r = v.getBoundingClientRect();
      if (r.width * r.height > a) { a = r.width * r.height; best = v; } });
    return best;
  }
  const visible = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const ok = t => t && t.length < 300;

  function domCaptions(v) {
    for (const s of SELECTORS) {
      const els = [...document.querySelectorAll(s)].filter(visible);
      const t = els.map(e => e.innerText.trim()).filter(Boolean).join('\n');
      if (ok(t)) { els.forEach(e => e.classList.add('ts-hidden')); return t; }
    }
    // Kërkim i përgjithshëm: elemente me "subtitle/caption" në emër, mbi zonën e videos
    const vr = v.getBoundingClientRect();
    const els = [...document.querySelectorAll(GENERIC)].filter(e => {
      if (e.id === 'ts-overlay' || e.closest('button,[role="button"],[class*="menu" i],[class*="setting" i]')) return false;
      if (e.querySelector(GENERIC)) return false; // vetëm elementi më i brendshëm
      const r = e.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && r.top >= vr.top && r.bottom <= vr.bottom + 5 &&
        r.top > vr.top + vr.height * 0.4;
    });
    const t = els.map(e => e.innerText.trim()).filter(Boolean).join('\n');
    if (ok(t)) { els.forEach(e => e.classList.add('ts-hidden')); return t; }
    return '';
  }

  function trackCaptions(v) {
    const tracks = [...v.textTracks].filter(t => ['subtitles', 'captions'].includes(t.kind));
    // Nëse askush nuk i ka ndezur titrat, ndiz të parat vetë (të fshehura)
    if (tracks.length && tracks.every(t => t.mode === 'disabled')) tracks[0].mode = 'hidden';
    for (const tr of tracks) {
      if (tr.mode === 'disabled') continue;
      if (tr.mode === 'showing') tr.mode = 'hidden';
      const t = [...(tr.activeCues || [])].map(c => (c.text || '').replace(/<[^>]+>/g, '')).join('\n').trim();
      if (t) return t;
    }
    return '';
  }

  function fileCaptions(v) {
    if (!fileCues.length) return '';
    const now = v.currentTime;
    return fileCues.filter(c => now >= c.start && now <= c.end).map(c => c.text).join('\n');
  }

  function hasAnySource(v) {
    return fileCues.length > 0 || [...v.textTracks].some(t => ['subtitles', 'captions'].includes(t.kind));
  }

  setInterval(async () => {
    const v = video(); if (!v || v.getBoundingClientRect().width < 200) return;
    if (!document.getElementById('ts-style') && (document.head || document.documentElement)) {
      const st = document.createElement('style'); st.id = 'ts-style'; st.textContent = css;
      (document.head || document.documentElement).appendChild(st);
    }
    if (!overlay) { overlay = document.createElement('div'); overlay.id = 'ts-overlay'; }
    if (!badge) { badge = document.createElement('div'); badge.id = 'ts-badge'; }
    const fs = document.fullscreenElement || document.webkitFullscreenElement;
    const host = fs && fs.tagName !== 'VIDEO' ? fs : document.documentElement;
    if (overlay.parentNode !== host) host.appendChild(overlay);
    if (badge.parentNode !== host) host.appendChild(badge);

    const r = v.getBoundingClientRect();
    Object.assign(overlay.style, { left: r.left + 'px', width: r.width + 'px',
      bottom: (innerHeight - r.bottom + r.height * 0.1) + 'px' });

    const src = domCaptions(v) || trackCaptions(v) || fileCaptions(v);
    if (src || hasAnySource(v)) found = true;

    // Shenja e gjendjes (zhduket pas 15 sekondash kur gjen titra)
    if (found) {
      badge.textContent = '🇦🇱 Titra Shqip: aktiv ✓';
      badge.style.display = Date.now() - startedAt > 15000 && !src ? 'none' : (src ? 'none' : 'block');
    } else {
      badge.style.display = 'block';
      badge.textContent = '🇦🇱 Titra Shqip: ndiz titrat (CC) te player-i';
    }

    if (src === last) return; last = src;
    if (!src) { overlay.textContent = ''; return; }
    const out = await translate(src);
    if (src === last) overlay.textContent = out;
  }, 250);
})();
