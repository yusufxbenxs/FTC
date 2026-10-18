// Automated checks for community packages. The same file runs in the panel
// (before you submit) and in the catalog repo's GitHub Action (before a
// maintainer reviews), so a package can't skip them by being edited by hand.
//
// Package formats:
//   { format: 'firetube-mod@1', id, name, desc, type, author, version, css, js? }
//   { format: 'firetube-automation@1', id, name, desc, author, trigger, conditions, actions }
(function (root) {
  const MOD_TYPES = ['visual', 'layout', 'animation', 'fun', 'button'];
  const MAX_CSS = 64 * 1024;

  // Selectors for ads and sponsorship disclosures. Hiding these breaks
  // YouTube's terms, so the catalog won't accept them.
  const AD_SELECTORS = [/ytd-ad-slot-renderer/i, /\.ytp-ad-/i, /#player-ads/i, /ytd-in-feed-ad-layout-renderer/i,
    /ytd-promoted-/i, /ytd-display-ad-renderer/i, /\.video-ads/i, /ytp-paid-content-overlay/i, /ad-showing/i];

  const JS_RISKS = [
    [/\bfetch\s*\(|XMLHttpRequest|WebSocket|sendBeacon|EventSource/, 'makes network requests'],
    [/\beval\s*\(|new\s+Function\s*\(|setTimeout\s*\(\s*['"`]/, 'evaluates strings as code'],
    [/document\.cookie|localStorage|sessionStorage|indexedDB/, 'reads site storage or cookies'],
    [/\bchrome\.|\bbrowser\./, 'touches extension APIs'],
    [/\.click\s*\(\s*\)|dispatchEvent/, 'clicks things automatically (must not like/subscribe/comment)'],
    [/innerHTML|outerHTML|insertAdjacentHTML|document\.write/, 'injects raw HTML'],
    [/postMessage/, 'talks to other frames']
  ];

  function stripComments(css) { return String(css).replace(/\/\*[\s\S]*?\*\//g, ''); }

  function bracesBalanced(css) {
    let depth = 0, quote = null;
    for (let i = 0; i < css.length; i++) {
      const ch = css[i];
      if (quote) { if (ch === '\\') i++; else if (ch === quote) quote = null; continue; }
      if (ch === '"' || ch === "'") quote = ch;
      else if (ch === '{') depth++;
      else if (ch === '}' && --depth < 0) return false;
    }
    return depth === 0 && !quote;
  }

  function checkCss(css, out) {
    const c = stripComments(css);
    if (c.length > MAX_CSS) out.push(['fail', `CSS is ${(c.length / 1024).toFixed(1)} KB (limit 64 KB)`]);
    if (!bracesBalanced(c)) out.push(['fail', 'CSS has unbalanced { } or an unclosed string']);
    if (/@import/i.test(c)) out.push(['fail', '@import is not allowed (loads code from elsewhere)']);
    if (/javascript:|expression\s*\(|-moz-binding|behavior\s*:/i.test(c)) out.push(['fail', 'Contains script-like CSS (javascript:, expression(), bindings)']);
    const urls = [...c.matchAll(/url\(\s*(['"]?)(.*?)\1\s*\)/gi)].map(m => m[2]);
    const external = urls.filter(u => !/^data:image\/(png|gif|jpeg|webp|svg\+xml)[;,]/i.test(u) && !u.startsWith('#'));
    if (external.length) out.push(['fail', `Loads external files (${external.slice(0, 2).join(', ')}). Only data:image URLs are allowed — outside requests can track viewers.`]);
    if (/input\[[^\]]*value/i.test(c)) out.push(['fail', 'Selectors on input values can leak what people type']);
    const ads = AD_SELECTORS.filter(r => r.test(c));
    if (ads.length) out.push(['fail', 'Touches ads or paid-promotion labels — ad-blocking mods are not accepted (YouTube Terms)']);
    const imp = (c.match(/!important/g) || []).length;
    if (imp > 300) out.push(['warn', `${imp} uses of !important — consider simplifying`]);
    if (!out.some(r => r[0] === 'fail')) out.push(['pass', 'CSS is self-contained and safe']);
  }

  function checkJs(js, out) {
    out.push(['review', 'Contains JavaScript. A maintainer must review it, and it only runs after it ships in an official FireTube update (Chrome bans extensions from running downloaded code).']);
    for (const [re, what] of JS_RISKS) if (re.test(js)) out.push(['warn', 'JavaScript ' + what]);
  }

  function verifyMod(p, out) {
    if (!/^[a-z0-9][a-z0-9-]{2,39}$/.test(p.id || '')) out.push(['fail', 'id must be 3–40 lowercase letters, numbers or dashes']);
    if (!MOD_TYPES.includes(p.type)) out.push(['fail', 'type must be one of: ' + MOD_TYPES.join(', ')]);
    if (!/^\d+\.\d+\.\d+$/.test(p.version || '')) out.push(['fail', 'version must look like 1.0.0']);
    const css = String(p.css || ''), js = String(p.js || '');
    if (!css.trim() && !js.trim()) out.push(['fail', 'Package has no CSS or JavaScript']);
    if (css.trim()) checkCss(css, out);
    if (js.trim()) checkJs(js, out);
  }

  function verifyAutomation(p, out, B) {
    if (!B) { out.push(['fail', 'Block definitions not loaded']); return; }
    const t = p.trigger || {};
    if (!B.TRIGGERS[t.type]) out.push(['fail', `Unknown trigger "${t.type}"`]);
    for (const c of p.conditions || []) if (!B.CONDITIONS[c.type]) out.push(['fail', `Unknown condition "${c.type}"`]);
    if (!Array.isArray(p.actions) || !p.actions.length) out.push(['fail', 'Automation has no actions']);
    for (const a of p.actions || []) {
      const def = B.ACTIONS[a.type];
      if (!def) { out.push(['fail', `Unknown action "${a.type}"`]); continue; }
      if (a.params && a.params.file) out.push(['fail', 'Shared automations cannot include video files']);
      if (a.type === 'open_url' && !/^https:\/\//.test((a.params || {}).url || '')) out.push(['fail', 'Open page must use an https:// link']);
      const tctx = B.TRIGGERS[t.type] && B.TRIGGERS[t.type].ctx;
      if (tctx && def.ctx !== 'any' && def.ctx !== tctx) out.push(['fail', `"${def.label}" can't run with trigger "${B.TRIGGERS[t.type].label}"`]);
    }
    if (!out.some(r => r[0] === 'fail')) out.push(['pass', 'Uses only built-in safe blocks']);
  }

  function verifyPackage(p, blocks) {
    const out = [];
    if (!p || typeof p !== 'object') return result([['fail', 'Not a valid package']]);
    const name = String(p.name || ''), desc = String(p.desc || '');
    if (name.trim().length < 3 || name.length > 50) out.push(['fail', 'Name must be 3–50 characters']);
    if (desc.trim().length < 10 || desc.length > 300) out.push(['fail', 'Description must be 10–300 characters']);
    if (!String(p.author || '').trim()) out.push(['fail', 'Author is required']);
    if (p.format === 'firetube-mod@1') verifyMod(p, out);
    else if (p.format === 'firetube-automation@1') verifyAutomation(p, out, blocks || root.FT_BLOCKS);
    else out.push(['fail', 'Unknown package format "' + p.format + '"']);
    return result(out);
  }

  function result(rows) {
    const checks = rows.map(([level, msg]) => ({ level, msg }));
    return {
      ok: !checks.some(c => c.level === 'fail'),
      needsHumanReview: checks.some(c => c.level === 'review'),
      checks
    };
  }

  const api = { verifyPackage, MOD_TYPES };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.FT_VERIFY = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
