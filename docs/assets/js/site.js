(() => {
  const media = matchMedia('(prefers-color-scheme: dark)');
  const themeState = window.XylunePageTheme || { appTheme: null, colorVariables: [], queryKeys: ['theme'] };
  const supported = ['dark', 'light', 'system'];

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
    const preference = document.documentElement.dataset.themePreference;
    return preference === 'app' && themeState.appTheme ? 'app' : supported.includes(preference) ? preference : 'dark';
  }

  function applyTheme(preference, persist = true) {
    if (preference === 'app' && !themeState.appTheme) preference = 'dark';
    themeState.colorVariables.forEach((name) => document.documentElement.style.removeProperty(name));
    if (preference === 'app') {
      Object.entries(themeState.appTheme.colors).forEach(([name, value]) => {
        document.documentElement.style.setProperty(name, value);
      });
    }
    const resolved = preference === 'app'
      ? (themeState.appTheme.dark ? 'dark' : 'light')
      : preference === 'system' ? (media.matches ? 'dark' : 'light') : preference;
    document.documentElement.dataset.theme = resolved;
    document.documentElement.dataset.themePreference = preference;
    document.documentElement.style.colorScheme = resolved;
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content',
      getComputedStyle(document.documentElement).getPropertyValue('--background').trim(),
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
      dialog?.close();
    });
  });

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

  media.addEventListener('change', () => {
    if (currentPreference() === 'system') applyTheme('system', false);
  });
  applyTheme(currentPreference(), false);
  syncThemeLinks();
})();
