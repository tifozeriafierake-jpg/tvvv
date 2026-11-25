(() => {
  if (window.__tsLoaded) return; window.__tsLoaded = true;
  const SELECTORS = ['.ytp-caption-segment', '.captions-text', '.player-timedtext-text-container',
    '.atvwebplayersdk-captions-text', '.dss-subtitle-renderer-line', '[data-testid="CueBoxContainer"]',
    '.vjs-text-track-display div div', '.jw-text-track-cue', '.plyr__caption', '.shaka-text-container span'];
  const HIDE = '.ytp-caption-window-container,.captions-text,.player-timedtext,.atvwebplayersdk-captions-overlay,' +
    '.dss-subtitle-renderer-wrapper,[data-testid="CueBoxContainer"],.vjs-text-track-display,.jw-captions,' +
    '.plyr__captions,.shaka-text-container{opacity:0!important}' +
    '#ts-overlay{position:fixed;z-index:2147483647;pointer-events:none;text-align:center;white-space:pre-line;' +
    'line-height:1.3;color:#fff;font:600 34px Arial,sans-serif;text-shadow:0 0 4px #000,0 0 4px #000,2px 2px 2px #000}';
  const cache = new Map(), pending = new Map();
  let last = '', overlay, nextId = 1;

  window.__tsDone = (id, text) => { const cb = pending.get(id); pending.delete(id); cb && cb(text); };
  const translate = t => cache.has(t) ? Promise.resolve(cache.get(t)) : new Promise(res => {
    const id = nextId++;
    pending.set(id, out => { cache.set(t, out); res(out); });
    TitraShqip.translate(t, id);
  });

  function setup() {
    if (document.getElementById('ts-style') || !document.head) return;
    const st = document.createElement('style'); st.id = 'ts-style'; st.textContent = HIDE;
    document.head.appendChild(st);
  }
  function video() {
    let best = null, a = 0;
    document.querySelectorAll('video').forEach(v => { const r = v.getBoundingClientRect();
      if (r.width * r.height > a) { a = r.width * r.height; best = v; } });
    return best;
  }
  function captions(v) {
    for (const s of SELECTORS) {
      const t = [...document.querySelectorAll(s)].map(e => e.innerText.trim()).filter(Boolean).join('\n');
      if (t) return t;
    }
    for (const tr of v.textTracks) {
      if (tr.mode === 'disabled' || !['subtitles', 'captions'].includes(tr.kind)) continue;
      if (tr.mode === 'showing') tr.mode = 'hidden';
      const t = [...(tr.activeCues || [])].map(c => (c.text || '').replace(/<[^>]+>/g, '')).join('\n').trim();
      if (t) return t;
    }
    return '';
  }
  setInterval(async () => {
    setup();
    const v = video(); if (!v) return;
    if (!overlay) { overlay = document.createElement('div'); overlay.id = 'ts-overlay'; }
    const fs = document.fullscreenElement || document.webkitFullscreenElement;
    const host = fs && fs.tagName !== 'VIDEO' ? fs : document.documentElement;
    if (overlay.parentNode !== host) host.appendChild(overlay);
    const r = v.getBoundingClientRect();
    Object.assign(overlay.style, { left: r.left + 'px', width: r.width + 'px',
      bottom: (innerHeight - r.bottom + r.height * 0.1) + 'px' });
    const src = captions(v);
    if (src === last) return; last = src;
    if (!src) { overlay.textContent = ''; return; }
    const out = await translate(src);
    if (src === last) overlay.textContent = out;
  }, 250);
})();
