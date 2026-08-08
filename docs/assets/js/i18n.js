(() => {
  const locale = window.XyluneLocale || {};
  const ui = locale.ui || {};
  const release = locale.release || {};
  const isTurkish = document.documentElement.lang.toLowerCase().startsWith('tr');

  function text(selector, value) {
    if (!value) return;
    document.querySelectorAll(selector).forEach((node) => {
      node.textContent = value;
    });
  }

  function aria(selector, name, value) {
    if (!value) return;
    document.querySelectorAll(selector).forEach((node) => node.setAttribute(name, value));
  }

  function localizeAppearance() {
    text('.appearance-launcher__label', ui.theme);
    aria('[data-theme-settings]', 'aria-label', ui.open_appearance);
    aria('[data-theme-close]', 'aria-label', ui.close_appearance);
    text('.dialog-heading h2', ui.appearance);
    text('.dialog-heading p', ui.customize_site);
    text('#theme-section-title', ui.theme);
    text('#scheme-section-title', ui.color_scheme);
    text('#icon-section-title', ui.brand_icon);

    const themeLabels = {
      app: ui.app,
      system: ui.auto,
      light: ui.light,
      dark: ui.dark,
    };
    Object.entries(themeLabels).forEach(([key, value]) => {
      text(`[data-theme-choice="${key}"] .theme-selector__label`, value);
    });

    const schemeLabels = {
      app: ui.app,
      xylune: 'Xylune',
      graphite: ui.graphite,
      ocean: ui.ocean,
      violet: ui.violet,
      sunset: ui.sunset,
    };
    Object.entries(schemeLabels).forEach(([key, value]) => {
      text(`[data-scheme-choice="${key}"] .palette-choice__label`, value);
    });

    text('.appearance-switch-row__copy strong', ui.dynamic_icon);
    text('.appearance-switch-row__copy small', ui.dynamic_icon_description);
    aria('[data-dynamic-icon-toggle]', 'aria-label', ui.use_dynamic_icon);
  }

  function localizeReleaseList() {
    if (!isTurkish) return;
    const container = document.querySelector('[data-release-list]');
    if (!container) return;

    container.querySelectorAll('.release-badge').forEach((node) => {
      if (node.textContent.trim() === 'Latest') node.textContent = release.latest || 'En yeni';
    });
    container.querySelectorAll('.release-card__meta').forEach((node) => {
      node.textContent = node.textContent.replace('Pre-release', release.pre_release || 'Ön sürüm');
    });
    container.querySelectorAll('.release-card__actions .button span, .release-list__footer .button span').forEach((node) => {
      const value = node.textContent.trim();
      if (value === 'Download APK') node.textContent = release.download_apk || "APK'yı indir";
      if (value === 'Open on GitHub') node.textContent = release.open_github || "GitHub'da aç";
      if (value === 'Show all releases') node.textContent = release.show_all || 'Tüm sürümleri göster';
      if (value === 'Open releases on GitHub') node.textContent = release.open_releases || "Sürümleri GitHub'da aç";
    });
    container.querySelectorAll('.release-notes p').forEach((node) => {
      if (node.textContent.trim() === 'No release notes were provided for this build.') {
        node.textContent = release.no_notes || 'Bu derleme için sürüm notu sunulmadı.';
      }
    });
    container.querySelectorAll('.release-status').forEach((node) => {
      const first = node.firstChild;
      if (first?.nodeType === Node.TEXT_NODE && first.textContent.includes('The live release list could not be loaded.')) {
        first.textContent = `${release.load_failed || 'Canlı sürüm listesi yüklenemedi.'} `;
      }
    });
  }

  function setupReleaseObserver() {
    const container = document.querySelector('[data-release-list]');
    if (!container || !isTurkish) return;
    const observer = new MutationObserver(localizeReleaseList);
    observer.observe(container, { childList: true, subtree: true, characterData: true });
    localizeReleaseList();
  }

  function setupLanguagePickers() {
    const pickers = [...document.querySelectorAll('.language-picker')];
    document.addEventListener('click', (event) => {
      pickers.forEach((picker) => {
        if (picker.open && !picker.contains(event.target)) picker.open = false;
      });
    });
    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      pickers.forEach((picker) => { picker.open = false; });
    });
  }

  localizeAppearance();
  setupReleaseObserver();
  setupLanguagePickers();
})();
