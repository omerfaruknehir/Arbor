(() => {
  const root = document.documentElement;
  const media = matchMedia('(prefers-color-scheme: dark)');
  const themeState = window.XylunePageTheme || { appTheme: null, colorVariables: [], queryKeys: ['theme'] };
  const supported = ['dark', 'light', 'system'];

  function activeColor(variable, fallback) {
    return getComputedStyle(root).getPropertyValue(variable).trim() || fallback || '';
  }

  function dynamicLogoDataUrl(preference) {
    if (preference === 'app' && !themeState.appTheme?.dynamicLogo) return null;
    const backgroundStart = activeColor('--primary-container', activeColor('--surface-container'));
    const backgroundEnd = activeColor('--primary');
    const firstStroke = activeColor('--on-primary-container', activeColor('--on-surface'));
    const secondStroke = activeColor('--on-primary', activeColor('--background'));
    const leaf = activeColor('--tertiary', activeColor('--secondary', activeColor('--primary')));
    if (![backgroundStart, backgroundEnd, firstStroke, secondStroke, leaf].every(Boolean)) return null;
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

  function syncBrandLogo(preference) {
    const dynamicSource = dynamicLogoDataUrl(preference);
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
      icon.replaceWith(replacement);
    });
  }

  function syncThemeLinks() {
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

  function currentPreference() {
    const preference = root.dataset.themePreference;
    return preference === 'app' && themeState.appTheme
      ? 'app'
      : supported.includes(preference) ? preference : 'dark';
  }

  function applyTheme(preference, persist = true) {
    if (preference === 'app' && !themeState.appTheme) preference = 'dark';
    themeState.colorVariables.forEach((name) => root.style.removeProperty(name));
    if (preference === 'app') {
      Object.entries(themeState.appTheme.colors).forEach(([name, value]) => {
        root.style.setProperty(name, value);
      });
    }
    const resolved = preference === 'app'
      ? (themeState.appTheme.dark ? 'dark' : 'light')
      : preference === 'system' ? (media.matches ? 'dark' : 'light') : preference;
    root.dataset.theme = resolved;
    root.dataset.themePreference = preference;
    root.style.colorScheme = resolved;
    syncBrandLogo(preference);
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content',
      activeColor('--background'),
    );
    document.querySelectorAll('[data-theme-choice]').forEach((button) => {
      const selected = button.dataset.themeChoice === preference;
      button.setAttribute('aria-checked', String(selected));
      button.classList.toggle('is-selected', selected);
    });
    if (persist && preference !== 'app') localStorage.setItem('xylune-theme', preference);
    const url = new URL(location.href);
    url.searchParams.set('theme', preference);
    history.replaceState(null, '', url);
    syncThemeLinks();
  }

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
    button.addEventListener('click', () => {
      applyTheme(button.dataset.themeChoice);
      if (button.closest('[data-theme-dialog]')) dialog?.close();
    });
  });

  media.addEventListener('change', () => {
    if (currentPreference() === 'system') applyTheme('system', false);
  });
  applyTheme(currentPreference(), false);
  syncThemeLinks();
})();
