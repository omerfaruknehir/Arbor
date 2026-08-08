(() => {
  const root = document.documentElement;
  const banners = [...document.querySelectorAll('[data-themed-banner]')];
  if (banners.length === 0) return;

  const BASE_BANNER_HUE = 164;
  const schemePrimary = {
    xylune: '#99d5b1',
    graphite: '#a9c7f8',
    ocean: '#54d6f2',
    violet: '#d1bcff',
    sunset: '#ffb59c',
  };

  function hexHue(value) {
    const match = String(value || '').trim().match(/^#?([0-9a-f]{6})$/i);
    if (!match) return null;
    const number = Number.parseInt(match[1], 16);
    const r = ((number >> 16) & 255) / 255;
    const g = ((number >> 8) & 255) / 255;
    const b = (number & 255) / 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const delta = max - min;
    if (delta === 0) return 0;
    let hue;
    if (max === r) hue = ((g - b) / delta) % 6;
    else if (max === g) hue = ((b - r) / delta) + 2;
    else hue = ((r - g) / delta) + 4;
    return ((hue * 60) + 360) % 360;
  }

  function shortestHueDelta(from, to) {
    return ((to - from + 540) % 360) - 180;
  }

  const basePrimaryHue = hexHue(schemePrimary.xylune);

  function activePrimaryHue() {
    const scheme = root.dataset.schemePreference || 'xylune';
    if (scheme !== 'app' && schemePrimary[scheme]) return hexHue(schemePrimary[scheme]);
    return hexHue(getComputedStyle(root).getPropertyValue('--primary')) ?? basePrimaryHue;
  }

  function syncBannerHue() {
    const targetPrimaryHue = activePrimaryHue();
    const shift = shortestHueDelta(basePrimaryHue, targetPrimaryHue);
    const targetBannerHue = (BASE_BANNER_HUE + shift + 360) % 360;
    root.style.setProperty('--xylune-banner-hue-shift', `${shift.toFixed(2)}deg`);
    root.style.setProperty('--xylune-banner-target-hue', `${targetBannerHue.toFixed(2)}deg`);
  }

  banners.forEach((banner) => {
    banner.addEventListener('error', () => {
      const fallback = banner.dataset.fallbackSrc;
      if (fallback && banner.src !== fallback) banner.src = fallback;
    }, { once: true });
  });

  const observer = new MutationObserver(syncBannerHue);
  observer.observe(root, {
    attributes: true,
    attributeFilter: ['style', 'data-scheme-preference', 'data-theme'],
  });
  syncBannerHue();
})();
