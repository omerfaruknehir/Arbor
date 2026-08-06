(() => {
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const dialog = document.querySelector('dialog[data-theme-dialog]');
  if (!dialog) return;

  const closeDelay = () => reducedMotion.matches ? 0 : 220;
  let closeTimer = 0;
  let openingFrame = 0;

  const reveal = () => {
    if (!dialog.open) return;
    clearTimeout(closeTimer);
    dialog.classList.remove('is-closing');
    dialog.classList.remove('is-visible');
    cancelAnimationFrame(openingFrame);
    openingFrame = requestAnimationFrame(() => {
      openingFrame = requestAnimationFrame(() => {
        if (dialog.open) dialog.classList.add('is-visible');
      });
    });
  };

  const closeAnimated = () => {
    if (!dialog.open || dialog.classList.contains('is-closing')) return;
    cancelAnimationFrame(openingFrame);
    dialog.classList.add('is-closing');
    dialog.classList.remove('is-visible');
    clearTimeout(closeTimer);
    closeTimer = window.setTimeout(() => {
      if (dialog.open) dialog.close();
      dialog.classList.remove('is-closing');
    }, closeDelay());
  };

  document.addEventListener('click', (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;

    if (target.closest('[data-theme-close]') || event.target === dialog) {
      event.preventDefault();
      event.stopImmediatePropagation();
      closeAnimated();
    }
  }, true);

  document.addEventListener('click', (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest('[data-theme-settings]')) reveal();
  });

  dialog.addEventListener('cancel', (event) => {
    event.preventDefault();
    closeAnimated();
  });

  const observer = new MutationObserver(() => {
    if (dialog.open && !dialog.classList.contains('is-visible') && !dialog.classList.contains('is-closing')) {
      reveal();
    }
  });
  observer.observe(dialog, { attributes: true, attributeFilter: ['open'] });
})();
