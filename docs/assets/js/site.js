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
    queryKeys: ['theme', 'scheme'],
  };

  function activeColor(variable, fallback) {
    return getComputedStyle(root).getPropertyValue(variable).trim() || fallback || '';
  }

  function normalizeHex(value, fallback) {
    const match = String(value || '').trim().match(/^#?([0-9a-f]{6})$/i);
    return match ? `#${match[1].toLowerCase()}` : fallback;
  }

  function mixHex(left, right, rightWeight) {
    const first = normalizeHex(left, '#000000').slice(1);
    const second = normalizeHex(right, '#000000').slice(1);
    const weight = Math.min(1, Math.max(0, Number(rightWeight) || 0));
    const channel = (offset) => Math.round(
      parseInt(first.slice(offset, offset + 2), 16) * (1 - weight)
      + parseInt(second.slice(offset, offset + 2), 16) * weight,
    ).toString(16).padStart(2, '0');
    return `#${channel(0)}${channel(2)}${channel(4)}`;
  }

  function appColor(variable, fallback) {
    return normalizeHex(themeState.appTheme?.colors?.[variable], fallback);
  }

  function dynamicLogoDataUrl(schemePreference) {
    if (schemePreference !== 'app' || !themeState.appTheme?.dynamicLogo) return null;

    const primary = appColor('--primary', '#0c684f');
    const secondary = appColor('--secondary', primary);
    const tertiary = appColor('--tertiary', '#f4c761');
    const backgroundStart = mixHex(primary, '#000000', 0.42);
    const backgroundEnd = mixHex(secondary, '#000000', 0.24);
    const firstStroke = mixHex(primary, '#ffffff', 0.68);
    const secondStroke = mixHex(primary, '#ffffff', 0.9);
    const leaf = mixHex(tertiary, '#ffffff', 0.08);

    const svg = `<svg width="512" height="512" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bg" x1="15" y1="8" x2="96" y2="101" gradientUnits="userSpaceOnUse"><stop stop-color="${backgroundStart}"/><stop offset="1" stop-color="${backgroundEnd}"/></linearGradient>
    <linearGradient id="mark" x1="27" y1="84" x2="55" y2="25" gradientUnits="userSpaceOnUse" gradientTransform="matrix(1.014377,0.27180148,-0.27180148,1.014377,27.43436,-9.9261354)"><stop stop-color="${firstStroke}"/><stop offset="1" stop-color="${secondStroke}"/></linearGradient>
  </defs>
  <rect width="108" height="108" rx="24" fill="url(#bg)"/>
  <path d="M 33.549193,80.863216 C 45.542258,64.507039 58.821502,47.408289 73.585895,32.881898" fill="none" stroke="url(#mark)" stroke-width="11.5517" stroke-linecap="round"/>
  <path d="M 39.107895,30.166046 C 43.79571,20.768808 52.715523,17.003434 60.890902,20.847009 59.491039,30.710867 51.981892,36.353531 40.896179,34.109428 Z" fill="${leaf}"/>
  <path d="M 33.99223,32.881898 C 48.756623,47.408289 62.035867,64.507039 74.028932,80.863216" fill="none" stroke="${secondStroke}" stroke-width="11.5517" stroke-linecap="round"/>
</svg>`;
    return `data:image/svg+xml,${encodeURIComponent(svg)}`;
  }

  function syncBrandLogo(schemePreference) {
    const dynamicSource = dynamicLogoDataUrl(schemePreference);
    root.dataset.brandLogo = dynamicSource ? 'app' : 'static';
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

  function syncAppearanceLinks() {
    const current = new URL(location.href);
    document.querySelectorAll('a[href]').forEach((anchor) => {
      const target = new URL(anchor.getAttribute('href'), location.href);
      if (target.origin !== location.origin) return;
      themeState.queryKeys.forEach((key) => {
        const value = current.searchParams.get(key);
        if (value !== null) target.searchParams.set(key, value);
      });
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

    if (persist && themePreference !== 'app') {
      localStorage.setItem('xylune-theme', themePreference);
    }
    if (persist && schemePreference !== 'app') {
      localStorage.setItem('xylune-scheme', schemePreference);
    }

    const url = new URL(location.href);
    url.searchParams.set('theme', themePreference);
    url.searchParams.set('scheme', schemePreference);
    history.replaceState(null, '', url);
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

  function renderAppearanceControls() {
    const rail = `
      <div class="appearance-control">
        <div class="appearance-control__heading">
          <span>Theme</span>
          <button class="icon-button" type="button" data-theme-settings aria-label="Open appearance settings">
            <span class="material-symbols-rounded" aria-hidden="true">tune</span>
          </button>
        </div>
        <div class="theme-selector" role="radiogroup" aria-label="Theme">
          <button class="theme-selector__choice" type="button" data-theme-choice="app" role="radio" title="Use app theme" aria-label="Use the theme passed by Xylune" hidden><span class="material-symbols-rounded" aria-hidden="true">phone_android</span><span class="theme-selector__label">App</span></button>
          <button class="theme-selector__choice" type="button" data-theme-choice="system" role="radio" title="Auto" aria-label="Follow system theme"><span class="material-symbols-rounded" aria-hidden="true">brightness_auto</span><span class="theme-selector__label">Auto</span></button>
          <button class="theme-selector__choice" type="button" data-theme-choice="light" role="radio" title="Light" aria-label="Use light theme"><span class="material-symbols-rounded" aria-hidden="true">light_mode</span><span class="theme-selector__label">Light</span></button>
          <button class="theme-selector__choice" type="button" data-theme-choice="dark" role="radio" title="Dark" aria-label="Use dark theme"><span class="material-symbols-rounded" aria-hidden="true">dark_mode</span><span class="theme-selector__label">Dark</span></button>
        </div>
      </div>
      <div class="appearance-control">
        <div class="appearance-control__heading"><span>Color scheme</span></div>
        <div class="color-scheme-selector" role="radiogroup" aria-label="Color scheme">
          ${schemeButton('app', 'App', true)}
          ${schemeButton('xylune', 'Xylune')}
          ${schemeButton('graphite', 'Graphite')}
          ${schemeButton('ocean', 'Ocean')}
          ${schemeButton('violet', 'Violet')}
          ${schemeButton('sunset', 'Sunset')}
        </div>
      </div>`;

    const dialog = `
      <div class="dialog-heading">
        <div>
          <h2 id="appearance-title">Appearance</h2>
          <p>Theme controls brightness. Color scheme controls the palette.</p>
        </div>
        <button class="icon-button" type="button" data-theme-close aria-label="Close appearance settings">
          <span class="material-symbols-rounded" aria-hidden="true">close</span>
        </button>
      </div>
      <section class="appearance-dialog__section" aria-labelledby="theme-section-title">
        <h3 class="appearance-dialog__section-title" id="theme-section-title">Theme</h3>
        <div class="appearance-options" role="radiogroup" aria-label="Theme">
          ${themeDialogButton('app', 'phone_android', 'App', 'Use the brightness passed by Xylune', true)}
          ${themeDialogButton('system', 'brightness_auto', 'Auto', 'Follow this device')}
          ${themeDialogButton('light', 'light_mode', 'Light', 'Always use light surfaces')}
          ${themeDialogButton('dark', 'dark_mode', 'Dark', 'Always use dark surfaces')}
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
      </section>`;

    document.querySelectorAll('.rail-appearance').forEach((container) => {
      container.innerHTML = rail;
    });
    document.querySelectorAll('[data-theme-dialog]').forEach((container) => {
      container.innerHTML = dialog;
    });
  }

  function schemeButton(value, label, hidden = false, dialog = false) {
    return `<button class="palette-choice${dialog ? ' palette-choice--dialog' : ''}" type="button" data-scheme-choice="${value}" role="radio"${hidden ? ' hidden' : ''}>
      <span class="palette-choice__swatches" aria-hidden="true"><span></span><span></span><span></span></span>
      <span class="palette-choice__label">${label}</span>
      ${dialog ? '<span class="material-symbols-rounded palette-choice__check" aria-hidden="true">check</span>' : ''}
    </button>`;
  }

  function themeDialogButton(value, icon, label, description, hidden = false) {
    return `<button class="appearance-option" type="button" data-theme-choice="${value}" role="radio"${hidden ? ' hidden' : ''}>
      <span class="material-symbols-rounded" aria-hidden="true">${icon}</span>
      <span><strong>${label}</strong><small>${description}</small></span>
      <span class="material-symbols-rounded option-check" aria-hidden="true">check</span>
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

  media.addEventListener('change', () => {
    if (currentThemePreference() === 'system') {
      applyAppearance('system', currentSchemePreference(), false);
    }
  });

  function setupTitleSettle() {
    const scroller = document.querySelector('.page-with-app-bar');
    if (!scroller) return;
    const collapseDistance = Number.parseFloat(
      getComputedStyle(root).getPropertyValue('--xylune-app-bar-collapse-distance'),
    ) || 88;
    let fallbackTimer = 0;
    let settling = false;

    const settle = () => {
      if (settling) return;
      const position = scroller.scrollTop;
      if (position <= 1 || position >= collapseDistance - 1) return;
      const target = position < collapseDistance / 2 ? 0 : collapseDistance;
      settling = true;
      scroller.scrollTo({
        top: target,
        behavior: reducedMotion.matches ? 'auto' : 'smooth',
      });
      setTimeout(() => {
        settling = false;
      }, reducedMotion.matches ? 0 : 260);
    };

    if ('onscrollend' in scroller) {
      scroller.addEventListener('scrollend', settle);
    } else {
      scroller.addEventListener('scroll', () => {
        clearTimeout(fallbackTimer);
        fallbackTimer = setTimeout(settle, 120);
      }, { passive: true });
    }
  }

  applyAppearance(currentThemePreference(), currentSchemePreference(), false);
  syncAppearanceLinks();
  setupTitleSettle();
})();
