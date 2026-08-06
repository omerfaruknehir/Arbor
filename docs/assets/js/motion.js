(() => {
  const root = document.documentElement;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let themeFrame = 0;

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
    let active = tabs.find((item) => item.classList.contains('is-active'));
    if (!active) return;

    const markActive = (target) => {
      tabs.forEach((tab) => {
        const selected = tab === target;
        tab.classList.toggle('is-active', selected);
        if (selected) tab.setAttribute('aria-current', 'page');
        else tab.removeAttribute('aria-current');
      });
      active = target;
    };

    placeNavigationIndicator(nav, active);
    void nav.offsetWidth;
    nav.classList.add('is-ready');

    tabs.forEach((tab) => {
      tab.dataset.navTab = '';
      tab.addEventListener('click', (event) => {
        if (
          event.defaultPrevented
          || event.button !== 0
          || event.metaKey
          || event.ctrlKey
          || event.shiftKey
          || event.altKey
          || tab.target === '_blank'
          || tab === active
        ) {
          return;
        }

        if (reducedMotion.matches) return;

        event.preventDefault();
        event.stopPropagation();
        markActive(tab);
        placeNavigationIndicator(nav, tab);
        window.setTimeout(() => location.assign(tab.href), 220);
      }, { capture: true });
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
        void selector.offsetWidth;
        selector.classList.add('is-ready');
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
          target.matches('.theme-selector__choice')
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

  function setupDraggableSwitch(control) {
    let pointerId = null;
    let startX = 0;
    let startChecked = false;
    let dragX = 0;
    let moved = false;
    let suppressNativeClick = false;

    const travelFor = () => Math.max(1, control.getBoundingClientRect().width - 32);
    const checked = () => control.getAttribute('aria-checked') === 'true';

    const renderDrag = (position, travel) => {
      dragX = Math.min(travel, Math.max(0, position));
      control.style.setProperty('--xylune-switch-drag-x', `${dragX}px`);
      control.classList.add('is-dragging');
      control.classList.toggle('is-drag-preview-on', dragX >= travel / 2);
    };

    const clearDrag = () => {
      control.classList.remove('is-dragging', 'is-drag-preview-on');
      control.style.removeProperty('--xylune-switch-drag-x');
    };

    control.addEventListener('click', (event) => {
      if (!suppressNativeClick || event.detail === 0) return;
      suppressNativeClick = false;
      event.preventDefault();
      event.stopImmediatePropagation();
    }, true);

    control.addEventListener('pointerdown', (event) => {
      if (event.button !== 0 || pointerId !== null) return;
      pointerId = event.pointerId;
      startX = event.clientX;
      startChecked = checked();
      moved = false;
      control.setPointerCapture(pointerId);
      const travel = travelFor();
      renderDrag(startChecked ? travel : 0, travel);
    });

    control.addEventListener('pointermove', (event) => {
      if (event.pointerId !== pointerId) return;
      const travel = travelFor();
      const delta = event.clientX - startX;
      if (Math.abs(delta) > 3) moved = true;
      renderDrag((startChecked ? travel : 0) + delta, travel);
    });

    const finishPointer = (event, cancelled = false) => {
      if (event.pointerId !== pointerId) return;
      const travel = travelFor();
      const desired = dragX >= travel / 2;
      if (control.hasPointerCapture(pointerId)) control.releasePointerCapture(pointerId);
      pointerId = null;
      clearDrag();

      if (cancelled || !moved) return;

      suppressNativeClick = true;
      if (desired !== startChecked) control.click();
      window.setTimeout(() => {
        suppressNativeClick = false;
      }, 400);
    };

    control.addEventListener('pointerup', (event) => finishPointer(event));
    control.addEventListener('pointercancel', (event) => finishPointer(event, true));
    control.addEventListener('lostpointercapture', () => {
      if (pointerId === null) return;
      pointerId = null;
      clearDrag();
    });
  }

  function setupDraggableSwitches() {
    document.querySelectorAll('.material-switch[role="switch"]').forEach(setupDraggableSwitch);
  }

  setupNavigationTabs();
  setupThemeSelectionMotion();
  setupDraggableSwitches();
  requestAnimationFrame(() => {
    requestAnimationFrame(() => root.classList.add('xylune-motion-ready'));
  });
})();
