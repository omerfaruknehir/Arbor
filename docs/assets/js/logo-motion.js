(() => {
  const root = document.documentElement;
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const staticPaletteName = 'xylune';
  const animationDuration = 320;

  const palettes = {
    xylune: {
      backgroundStart: '#083a2c',
      backgroundEnd: '#0c684f',
      markStart: '#86dfb8',
      markEnd: '#ddfbea',
      leaf: '#f4c761',
      secondStroke: '#f1fff7',
    },
    system: {
      backgroundStart: '#293b52',
      backgroundEnd: '#67507e',
      markStart: '#a9d4ff',
      markEnd: '#e8ddff',
      leaf: '#ffb4a9',
      secondStroke: '#fff8ff',
    },
    graphite: {
      backgroundStart: '#162234',
      backgroundEnd: '#425f86',
      markStart: '#a9c7f8',
      markEnd: '#e7f0ff',
      leaf: '#e5bfa6',
      secondStroke: '#f7f9ff',
    },
    ocean: {
      backgroundStart: '#00363f',
      backgroundEnd: '#00677a',
      markStart: '#54d6f2',
      markEnd: '#d5f7ff',
      leaf: '#bec6ea',
      secondStroke: '#f2fdff',
    },
    violet: {
      backgroundStart: '#2e1d4f',
      backgroundEnd: '#67508f',
      markStart: '#d1bcff',
      markEnd: '#f0e8ff',
      leaf: '#efb8c8',
      secondStroke: '#fff8ff',
    },
    sunset: {
      backgroundStart: '#5c1a07',
      backgroundEnd: '#9b4425',
      markStart: '#ffb59c',
      markEnd: '#ffede7',
      leaf: '#d7c58d',
      secondStroke: '#fff8f6',
    },
  };

  const primaryToPalette = new Map([
    ['#286448', 'xylune'],
    ['#99d5b1', 'xylune'],
    ['#425f86', 'graphite'],
    ['#a9c7f8', 'graphite'],
    ['#00677a', 'ocean'],
    ['#54d6f2', 'ocean'],
    ['#67508f', 'violet'],
    ['#d1bcff', 'violet'],
    ['#9b4425', 'sunset'],
    ['#ffb59c', 'sunset'],
  ]);

  const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));

  function normalizeHex(value, fallback = '#000000') {
    const match = String(value || '').trim().match(/^#?([0-9a-f]{6})$/i);
    return match ? `#${match[1].toLowerCase()}` : fallback;
  }

  function hexToRgb(hex) {
    const value = Number.parseInt(normalizeHex(hex).slice(1), 16);
    return {
      r: (value >> 16) & 255,
      g: (value >> 8) & 255,
      b: value & 255,
    };
  }

  function rgbToHex({ r, g, b }) {
    const component = (value) => Math.round(clamp(value, 0, 255)).toString(16).padStart(2, '0');
    return `#${component(r)}${component(g)}${component(b)}`;
  }

  function mixColor(from, to, progress) {
    const start = hexToRgb(from);
    const end = hexToRgb(to);
    return rgbToHex({
      r: start.r + ((end.r - start.r) * progress),
      g: start.g + ((end.g - start.g) * progress),
      b: start.b + ((end.b - start.b) * progress),
    });
  }

  function mixPalette(from, to, progress) {
    const amount = clamp(progress);
    return Object.fromEntries(
      Object.keys(from).map((key) => [key, mixColor(from[key], to[key], amount)]),
    );
  }

  function paletteNameForScheme() {
    const scheme = root.dataset.schemePreference || 'xylune';
    if (scheme !== 'app') return palettes[scheme] ? scheme : staticPaletteName;
    const primary = normalizeHex(getComputedStyle(root).getPropertyValue('--primary'));
    return primaryToPalette.get(primary) || 'system';
  }

  function targetPalette() {
    return root.dataset.dynamicIcon === 'on'
      ? palettes[paletteNameForScheme()]
      : palettes[staticPaletteName];
  }

  function logoDataUrl(palette) {
    const svg = `<svg width="512" height="512" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bg" x1="15" y1="8" x2="96" y2="101" gradientUnits="userSpaceOnUse">
      <stop stop-color="${palette.backgroundStart}"/>
      <stop offset="1" stop-color="${palette.backgroundEnd}"/>
    </linearGradient>
    <linearGradient id="mark" x1="31.9912" y1="82.6202" x2="76.4301" y2="30.3824" gradientUnits="userSpaceOnUse">
      <stop stop-color="${palette.markStart}"/>
      <stop offset="1" stop-color="${palette.markEnd}"/>
    </linearGradient>
  </defs>
  <rect width="108" height="108" rx="24" fill="url(#bg)"/>
  <path d="M33.549193 80.863216C45.542258 64.507039 58.821502 47.408289 73.585895 32.881898" fill="none" stroke="url(#mark)" stroke-width="11.5517" stroke-linecap="round"/>
  <path d="M39.107895 30.166046C43.79571 20.768808 52.715523 17.003434 60.890902 20.847009C59.491039 30.710867 51.981892 36.353531 40.896179 34.109428Z" fill="${palette.leaf}"/>
  <path d="M33.99223 32.881898C48.756623 47.408289 62.035867 64.507039 74.028932 80.863216" fill="none" stroke="${palette.secondStroke}" stroke-width="11.5517" stroke-linecap="round"/>
</svg>`;
    return `data:image/svg+xml,${encodeURIComponent(svg)}`;
  }

  function installDialogLogoPreview() {
    const icon = document.querySelector('.appearance-switch-row__icon');
    if (!icon || document.querySelector('.appearance-switch-row__logo')) return;
    const source = document.querySelector('[data-xylune-logo]')?.getAttribute('src')
      || '/assets/images/xylune-logo.svg';
    const image = document.createElement('img');
    image.className = 'appearance-switch-row__logo';
    image.setAttribute('src', source);
    image.setAttribute('alt', '');
    image.setAttribute('aria-hidden', 'true');
    image.dataset.xyluneLogo = '';
    icon.replaceWith(image);
  }

  let currentPalette = targetPalette();
  let animationFrame = 0;
  let stateFrame = 0;
  let isPreviewing = false;

  function renderPalette(palette) {
    currentPalette = palette;
    const source = logoDataUrl(palette);
    document.querySelectorAll('[data-xylune-logo]').forEach((image) => {
      image.setAttribute('src', source);
    });
  }

  function animateTo(palette, duration = animationDuration) {
    cancelAnimationFrame(animationFrame);
    const start = currentPalette;
    if (reducedMotion.matches || duration <= 0) {
      renderPalette(palette);
      return;
    }

    const startedAt = performance.now();
    renderPalette(start);
    const tick = (now) => {
      const linear = clamp((now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - linear, 3);
      renderPalette(mixPalette(start, palette, eased));
      if (linear < 1) animationFrame = requestAnimationFrame(tick);
    };
    animationFrame = requestAnimationFrame(tick);
  }

  function scheduleStateSync() {
    if (isPreviewing || stateFrame) return;
    stateFrame = requestAnimationFrame(() => {
      stateFrame = 0;
      animateTo(targetPalette());
    });
  }

  installDialogLogoPreview();
  renderPalette(currentPalette);

  const observer = new MutationObserver(scheduleStateSync);
  observer.observe(root, {
    attributes: true,
    attributeFilter: ['data-dynamic-icon', 'data-scheme-preference', 'style'],
  });

  document.addEventListener('xylune-switch-preview', (event) => {
    const control = event.detail?.control;
    if (!(control instanceof Element) || !control.matches('[data-dynamic-icon-toggle]')) return;
    isPreviewing = true;
    cancelAnimationFrame(animationFrame);
    const progress = clamp(Number(event.detail.progress));
    renderPalette(mixPalette(palettes[staticPaletteName], palettes[paletteNameForScheme()], progress));
  });

  document.addEventListener('xylune-switch-preview-end', (event) => {
    const control = event.detail?.control;
    if (!(control instanceof Element) || !control.matches('[data-dynamic-icon-toggle]')) return;
    isPreviewing = false;
    animateTo(targetPalette(), 180);
  });
})();
