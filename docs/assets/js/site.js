(() => {
  const root = document.documentElement;
  const media = matchMedia('(prefers-color-scheme: dark)');
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const themeState = window.XylunePageTheme || {
    appTheme: null,
    colorVariables: [],
    fixedColors: () => ({}),
    supportedThemes: ['app', 'dark', 'light', 'system'],
    supportedSchemes: ['app', 'xylune'],
    queryKeys: ['theme', 'scheme', 'dynamicLogo'],
  };
  const storedDynamicIcon = localStorage.getItem('xylune-dynamic-icon');
  let dynamicIconEnabled = storedDynamicIcon !== null
    ? storedDynamicIcon === '1'
    : Boolean(themeState.appTheme?.dynamicLogo);

  const appIconPalettes = {
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

  const appPrimaryToIconPalette = new Map([
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

  function activeColor(variable, fallback) {
    return getComputedStyle(root).getPropertyValue(variable).trim() || fallback || '';
  }

  function normalizeHex(value, fallback = '') {
    const match = String(value || '').trim().match(/^#?([0-9a-f]{6})$/i);
    return match ? `#${match[1].toLowerCase()}` : fallback;
  }

  function iconPaletteFor(schemePreference) {
    if (schemePreference !== 'app') {
      return appIconPalettes[schemePreference] ? schemePreference : 'xylune';
    }
    const appPrimary = normalizeHex(themeState.appTheme?.colors?.['--primary']);
    return appPrimaryToIconPalette.get(appPrimary) || 'system';
  }

  function dynamicLogoDataUrl(schemePreference) {
    if (!dynamicIconEnabled) return null;

    const paletteName = iconPaletteFor(schemePreference);
    const palette = appIconPalettes[paletteName];
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

  function syncBrandLogo(schemePreference) {
    const dynamicSource = dynamicLogoDataUrl(schemePreference);
    root.dataset.brandLogo = dynamicSource ? iconPaletteFor(schemePreference) : 'static';
    document.querySelectorAll('[data-xylune-logo]').forEach((image) => {
      image.dataset.staticSrc ||= image.getAttribute('src') || '';
      image.setAttribute('src', dynamicSource || image.dataset.staticSrc);
    });
    document.querySelectorAll('link[data-xylune-favicon]').forEach((icon) => {
      icon.dataset.staticHref ||= icon.getAttribute('href') || '';
      const desired = dynamicSource || icon.dataset.staticHref;
      if (icon.getAttribute('href') === desired) return;
      const replacement = icon.cloneNode(true);
      replacement.setAttribute('href', desired);
      if (dynamicSource) replacement.setAttribute('type', 'image/svg+xml');
      icon.replaceWith(replacement);
    });
  }

  function syncDynamicIconControls() {
    root.dataset.dynamicIcon = dynamicIconEnabled ? 'on' : 'off';
    document.querySelectorAll('[data-dynamic-icon-toggle]').forEach((control) => {
      control.setAttribute('aria-checked', String(dynamicIconEnabled));
      control.classList.toggle('is-checked', dynamicIconEnabled);
    });
  }

  function syncAppearanceLinks() {
    document.querySelectorAll('a[href]').forEach((anchor) => {
      const target = new URL(anchor.getAttribute('href'), location.href);
      if (target.origin !== location.origin) return;
      themeState.queryKeys.forEach((key) => target.searchParams.delete(key));
      anchor.href = target.href;
    });
  }

  function storedFixedScheme() {
    const stored = localStorage.getItem('xylune-scheme');
    return themeState.supportedSchemes.includes(stored) && stored !== 'app'
      ? stored
      : 'xylune';
  }

  function resolvedTheme(themePreference) {
    if (themePreference === 'app' && themeState.appTheme) {
      return themeState.appTheme.dark ? 'dark' : 'light';
    }
    if (themePreference === 'system') return media.matches ? 'dark' : 'light';
    return themePreference === 'light' ? 'light' : 'dark';
  }

  function colorsFor(themePreference, schemePreference) {
    if (schemePreference === 'app' && themeState.appTheme) {
      return {
        ...themeState.fixedColors('xylune', themeState.appTheme.dark),
        ...themeState.appTheme.colors,
        '--focus': themeState.appTheme.colors['--primary'],
      };
    }
    return themeState.fixedColors(
      schemePreference,
      resolvedTheme(themePreference) === 'dark',
    );
  }

  function currentThemePreference() {
    const value = root.dataset.themePreference;
    return themeState.supportedThemes.includes(value) ? value : 'dark';
  }

  function currentSchemePreference() {
    const value = root.dataset.schemePreference;
    return themeState.supportedSchemes.includes(value) ? value : 'xylune';
  }

  function cleanAppearanceUrl() {
    const url = new URL(location.href);
    let changed = false;
    themeState.queryKeys.forEach((key) => {
      if (!url.searchParams.has(key)) return;
      url.searchParams.delete(key);
      changed = true;
    });
    if (changed) history.replaceState(null, '', url);
  }

  function applyAppearance(themePreference, schemePreference, persist = true) {
    if (themePreference === 'app' && !themeState.appTheme) themePreference = 'dark';
    if (schemePreference === 'app' && !themeState.appTheme) schemePreference = storedFixedScheme();
    if (schemePreference === 'app') themePreference = 'app';

    const resolved = resolvedTheme(themePreference);
    const colors = colorsFor(themePreference, schemePreference);
    themeState.colorVariables.forEach((name) => root.style.removeProperty(name));
    Object.entries(colors).forEach(([name, value]) => root.style.setProperty(name, value));

    root.dataset.theme = resolved;
    root.dataset.themePreference = themePreference;
    root.dataset.schemePreference = schemePreference;
    root.style.colorScheme = resolved;
    syncBrandLogo(schemePreference);
    syncDynamicIconControls();

    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content',
      activeColor('--background'),
    );
    document.querySelectorAll('[data-theme-choice]').forEach((button) => {
      const selected = button.dataset.themeChoice === themePreference;
      button.setAttribute('aria-checked', String(selected));
      button.classList.toggle('is-selected', selected);
    });
    document.querySelectorAll('[data-scheme-choice]').forEach((button) => {
      const selected = button.dataset.schemeChoice === schemePreference;
      button.setAttribute('aria-checked', String(selected));
      button.classList.toggle('is-selected', selected);
    });

    if (persist) {
      localStorage.setItem('xylune-theme', themePreference);
      localStorage.setItem('xylune-scheme', schemePreference);
    }

    cleanAppearanceUrl();
    syncAppearanceLinks();
  }

  function setTheme(themePreference) {
    let schemePreference = currentSchemePreference();
    if (themePreference !== 'app' && schemePreference === 'app') {
      schemePreference = storedFixedScheme();
    }
    applyAppearance(themePreference, schemePreference);
  }

  function setScheme(schemePreference) {
    const themePreference = schemePreference === 'app'
      ? 'app'
      : currentThemePreference();
    applyAppearance(themePreference, schemePreference);
  }

  function setDynamicIcon(enabled) {
    dynamicIconEnabled = Boolean(enabled);
    localStorage.setItem('xylune-dynamic-icon', dynamicIconEnabled ? '1' : '0');
    const themePreference = currentThemePreference();
    const schemePreference = currentSchemePreference();
    syncBrandLogo(schemePreference);
    syncDynamicIconControls();
    cleanAppearanceUrl();
    syncAppearanceLinks();
  }

  function renderAppearanceControls() {
    const rail = `
      <div class="appearance-launcher">
        <span class="appearance-launcher__label">Theme</span>
        <button class="icon-button" type="button" data-theme-settings aria-label="Open color scheme settings">
          <span class="material-symbols-rounded" aria-hidden="true">palette</span>
        </button>
      </div>
      <div class="theme-selector rail-theme-selector" role="radiogroup" aria-label="Theme">
        ${themeSegmentButton('app', 'phone_android', 'App', true)}
        ${themeSegmentButton('system', 'brightness_auto', 'Auto')}
        ${themeSegmentButton('light', 'light_mode', 'Light')}
        ${themeSegmentButton('dark', 'dark_mode', 'Dark')}
      </div>`;

    const dialog = `
      <div class="dialog-heading">
        <div>
          <h2 id="appearance-title">Appearance</h2>
          <p>Customize this site.</p>
        </div>
        <button class="icon-button" type="button" data-theme-close aria-label="Close appearance settings">
          <span class="material-symbols-rounded" aria-hidden="true">close</span>
        </button>
      </div>
      <section class="appearance-dialog__section" aria-labelledby="theme-section-title">
        <h3 class="appearance-dialog__section-title" id="theme-section-title">Theme</h3>
        <div class="theme-selector dialog-theme-selector" role="radiogroup" aria-label="Theme">
          ${themeSegmentButton('app', 'phone_android', 'App', true)}
          ${themeSegmentButton('system', 'brightness_auto', 'Auto')}
          ${themeSegmentButton('light', 'light_mode', 'Light')}
          ${themeSegmentButton('dark', 'dark_mode', 'Dark')}
        </div>
      </section>
      <section class="appearance-dialog__section" aria-labelledby="scheme-section-title">
        <h3 class="appearance-dialog__section-title" id="scheme-section-title">Color scheme</h3>
        <div class="dialog-scheme-grid" role="radiogroup" aria-label="Color scheme">
          ${schemeButton('app', 'App', true, true)}
          ${schemeButton('xylune', 'Xylune', false, true)}
          ${schemeButton('graphite', 'Graphite', false, true)}
          ${schemeButton('ocean', 'Ocean', false, true)}
          ${schemeButton('violet', 'Violet', false, true)}
          ${schemeButton('sunset', 'Sunset', false, true)}
        </div>
      </section>
      <section class="appearance-dialog__section appearance-dialog__switch-section" aria-labelledby="icon-section-title">
        <h3 class="appearance-dialog__section-title" id="icon-section-title">Brand icon</h3>
        <div class="appearance-switch-row">
          <span class="material-symbols-rounded appearance-switch-row__icon" aria-hidden="true">gradient</span>
          <span class="appearance-switch-row__copy">
            <strong>Dynamic icon</strong>
            <small>Use the same icon variant as Xylune for the selected color scheme.</small>
          </span>
          <button class="material-switch" type="button" role="switch" data-dynamic-icon-toggle aria-label="Use dynamic Xylune icon" aria-checked="false">
            <span class="material-switch__handle"></span>
          </button>
        </div>
      </section>`;

    document.querySelectorAll('.rail-appearance').forEach((container) => {
      container.innerHTML = rail;
    });
    document.querySelectorAll('[data-theme-dialog]').forEach((container) => {
      container.innerHTML = dialog;
    });
  }

  function themeSegmentButton(value, icon, label, hidden = false) {
    return `<button class="theme-selector__choice" type="button" data-theme-choice="${value}" role="radio"${hidden ? ' hidden' : ''}>
      <span class="material-symbols-rounded" aria-hidden="true">${icon}</span>
      <span class="theme-selector__label">${label}</span>
    </button>`;
  }

  function schemeButton(value, label, hidden = false, dialog = false) {
    return `<button class="palette-choice${dialog ? ' palette-choice--dialog' : ''}" type="button" data-scheme-choice="${value}" role="radio"${hidden ? ' hidden' : ''}>
      <span class="palette-choice__swatches" aria-hidden="true"><span></span><span></span><span></span></span>
      <span class="palette-choice__label">${label}</span>
      ${dialog ? '<span class="material-symbols-rounded palette-choice__check" aria-hidden="true">check</span>' : ''}
    </button>`;
  }

  renderAppearanceControls();

  const menuButton = document.querySelector('[data-menu-toggle]');
  const dismissMenu = () => {
    document.body.classList.remove('menu-open');
    menuButton?.setAttribute('aria-expanded', 'false');
  };
  menuButton?.addEventListener('click', () => {
    const open = document.body.classList.toggle('menu-open');
    menuButton.setAttribute('aria-expanded', String(open));
  });
  document.querySelector('[data-menu-dismiss]')?.addEventListener('click', dismissMenu);
  document.querySelectorAll('.site-rail a').forEach((link) => link.addEventListener('click', dismissMenu));
  addEventListener('keydown', (event) => {
    if (event.key === 'Escape') dismissMenu();
  });

  const dialog = document.querySelector('[data-theme-dialog]');
  document.querySelectorAll('[data-theme-settings]').forEach((button) => {
    button.addEventListener('click', () => {
      dismissMenu();
      dialog?.showModal();
    });
  });
  document.querySelector('[data-theme-close]')?.addEventListener('click', () => dialog?.close());
  dialog?.addEventListener('click', (event) => {
    if (event.target === dialog) dialog.close();
  });

  document.querySelectorAll('[data-theme-choice]').forEach((button) => {
    if (button.dataset.themeChoice === 'app') button.hidden = !themeState.appTheme;
    button.addEventListener('click', () => setTheme(button.dataset.themeChoice));
  });
  document.querySelectorAll('[data-scheme-choice]').forEach((button) => {
    if (button.dataset.schemeChoice === 'app') button.hidden = !themeState.appTheme;
    button.addEventListener('click', () => setScheme(button.dataset.schemeChoice));
  });
  document.querySelectorAll('[data-dynamic-icon-toggle]').forEach((control) => {
    control.addEventListener('click', () => setDynamicIcon(!dynamicIconEnabled));
  });

  window.addEventListener("load", () => {
    document.body.classList.remove("preload");
  });

  media.addEventListener('change', () => {
    if (currentThemePreference() === 'system') {
      applyAppearance('system', currentSchemePreference(), false);
    }
  });

  function setupTitleCollapse() {
    const scroller = document.querySelector('.page-with-app-bar');
    if (!scroller) return;
    const collapseDistance = Number.parseFloat(
      getComputedStyle(root).getPropertyValue('--xylune-app-bar-collapse-distance'),
    ) || 88;
    const expandedTitleShift = 58;
    const expandedTitleScale = Number.parseFloat(
      getComputedStyle(scroller).getPropertyValue('--xylune-title-expanded-scale'),
    ) || 1.18;
    const supportsScrollEnd = 'onscrollend' in scroller;
    let animationFrame = 0;
    let fallbackTimer = 0;
    let releaseTimer = 0;
    let settling = false;

    const applyProgress = () => {
      animationFrame = 0;
      const progress = Math.min(1, Math.max(0, scroller.scrollTop / collapseDistance));
      scroller.style.setProperty('--xylune-app-bar-row-shift', `${collapseDistance * progress}px`);
      scroller.style.setProperty('--xylune-title-shift', `${expandedTitleShift * (1 - progress)}px`);
      scroller.style.setProperty(
        '--xylune-title-scale',
        String(expandedTitleScale - ((expandedTitleScale - 1) * progress)),
      );
      scroller.style.setProperty('--xylune-bar-opacity', String(progress));
      scroller.style.setProperty('--xylune-bar-shadow-alpha', String(0.13 * progress));
    };

    const queueProgress = () => {
      if (!animationFrame) animationFrame = requestAnimationFrame(applyProgress);
    };

    const settlePartialTitle = () => {
      applyProgress();
      if (settling) return;
      const position = scroller.scrollTop;
      if (position <= 1 || position >= collapseDistance - 1) return;
      const target = position < collapseDistance / 2 ? 0 : collapseDistance;
      settling = true;
      scroller.scrollTo({
        top: target,
        behavior: reducedMotion.matches ? 'auto' : 'smooth',
      });
      clearTimeout(releaseTimer);
      releaseTimer = setTimeout(() => {
        settling = false;
        applyProgress();
      }, reducedMotion.matches ? 0 : 320);
    };

    scroller.addEventListener('scroll', () => {
      queueProgress();
      if (!supportsScrollEnd) {
        clearTimeout(fallbackTimer);
        fallbackTimer = setTimeout(settlePartialTitle, 140);
      }
    }, { passive: true });
    if (supportsScrollEnd) scroller.addEventListener('scrollend', settlePartialTitle);
    addEventListener('resize', queueProgress, { passive: true });
    applyProgress();
  }

  applyAppearance(currentThemePreference(), currentSchemePreference(), false);
  syncAppearanceLinks();
  setupTitleCollapse();
})();
