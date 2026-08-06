(() => {
  const root = document.documentElement;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const navigationStorageKey = 'xylune-navigation-tab-from-v1';
  let themeFrame = 0;

  function normalizedPath(value) {
    try {
      const url = new URL(value, location.href);
      let path = url.pathname.replace(/\/+$/, '');
      return path || '/';
    } catch (_) {
      return '/';
    }
  }

  function ensureIndicator(parent, className) {
    let indicator = parent.querySelector(`:scope > .${className}`);
    if (indicator) return indicator;
    indicator = document.createElement('span');
    indicator.className = className;
    indicator.setAttribute('aria-hidden', 'true');
    parent.prepend(indicator);
    return indicator;
  }

  function placeNavigationIndicator(nav, tab) {
    if (!nav || !tab) return;
    ensureIndicator(nav, 'rail-nav__indicator');
    nav.style.setProperty('--xylune-nav-indicator-y', `${tab.offsetTop}px`);
    nav.style.setProperty('--xylune-nav-indicator-height', `${tab.offsetHeight}px`);
  }

  function setupNavigationTabs() {
    const nav = document.querySelector('.rail-nav');
    if (!nav) return;

    const tabs = Array.from(nav.querySelectorAll('.nav-item[href]')).filter((item) => {
      try {
        return new URL(item.href, location.href).origin === location.origin;
      } catch (_) {
        return false;
      }
    });
    const active = tabs.find((item) => item.classList.contains('is-active'));
    if (!active) return;

    tabs.forEach((tab) => {
      tab.dataset.navTab = '';
      tab.toggleAttribute('aria-current', tab === active);
      if (tab === active) tab.setAttribute('aria-current', 'page');
      tab.addEventListener('click', () => {
        try {
          sessionStorage.setItem(navigationStorageKey, normalizedPath(location.href));
        } catch (_) {
          // Session storage can be unavailable in restricted web views.
        }
      });
    });

    let previousPath = null;
    try {
      previousPath = sessionStorage.getItem(navigationStorageKey);
      sessionStorage.removeItem(navigationStorageKey);
    } catch (_) {
      previousPath = null;
    }
    const previous = previousPath
      ? tabs.find((tab) => normalizedPath(tab.href) === previousPath)
      : null;

    nav.classList.remove('is-ready');
    placeNavigationIndicator(nav, previous || active);
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        nav.classList.add('is-ready');
        placeNavigationIndicator(nav, active);
      });
    });

    const update = () => placeNavigationIndicator(nav, active);
    window.addEventListener('resize', update, { passive: true });
    if ('ResizeObserver' in window) {
      const observer = new ResizeObserver(update);
      observer.observe(nav);
    }
  }

  function placeThemeIndicator(selector, animate = true) {
    const selected = selector.querySelector('.theme-selector__choice.is-selected:not([hidden])');
    if (!selected) return;
    ensureIndicator(selector, 'theme-selector__indicator');
    selector.style.setProperty('--xylune-theme-indicator-x', `${selected.offsetLeft}px`);
    selector.style.setProperty('--xylune-theme-indicator-width', `${selected.offsetWidth}px`);
    if (animate) selector.classList.add('is-ready');
  }

  function syncThemeSelectors() {
    themeFrame = 0;
    document.querySelectorAll('.theme-selector').forEach((selector) => {
      const firstLayout = !selector.querySelector(':scope > .theme-selector__indicator');
      if (firstLayout) selector.classList.remove('is-ready');
      placeThemeIndicator(selector, !firstLayout);
      if (firstLayout) {
        requestAnimationFrame(() => selector.classList.add('is-ready'));
      }
    });
  }

  function scheduleThemeSync() {
    if (!themeFrame) themeFrame = requestAnimationFrame(syncThemeSelectors);
  }

  function setupThemeSelectionMotion() {
    syncThemeSelectors();
    document.addEventListener('click', (event) => {
      if (event.target.closest('[data-theme-choice]')) scheduleThemeSync();
    });

    const observer = new MutationObserver((mutations) => {
      if (mutations.some((mutation) => {
        if (mutation.type === 'childList') return true;
        const target = mutation.target;
        return target instanceof Element && (
          target.matches('.theme-selector, .theme-selector__choice')
          || target.closest('.theme-selector')
        );
      })) {
        scheduleThemeSync();
      }
    });
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['class', 'hidden'],
    });

    window.addEventListener('resize', scheduleThemeSync, { passive: true });
  }

  setupNavigationTabs();
  setupThemeSelectionMotion();
  requestAnimationFrame(() => {
    requestAnimationFrame(() => root.classList.add('xylune-motion-ready'));
  });
})();
