(() => {
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const dialog = document.querySelector('dialog[data-theme-dialog]');
  if (!dialog) return;

  const closeDelay = () => reducedMotion.matches ? 0 : 280;
  let closeTimer = 0;
  let openingFrame = 0;

  const indexDialogItems = () => {
    dialog.querySelectorAll(':scope > .dialog-heading, :scope > .appearance-dialog__section')
      .forEach((item, index) => {
        item.style.setProperty('--xylune-popup-item-index', String(index));
      });
  };

  const setTransformOrigin = (trigger) => {
    if (!(trigger instanceof Element) || !dialog.open) return;
    const triggerRect = trigger.getBoundingClientRect();
    const dialogRect = dialog.getBoundingClientRect();
    const x = Math.min(
      dialogRect.width,
      Math.max(0, triggerRect.left + (triggerRect.width / 2) - dialogRect.left),
    );
    const y = Math.min(
      dialogRect.height,
      Math.max(0, triggerRect.top + (triggerRect.height / 2) - dialogRect.top),
    );
    dialog.style.setProperty('--xylune-popup-origin-x', `${x}px`);
    dialog.style.setProperty('--xylune-popup-origin-y', `${y}px`);
  };

  const reveal = (trigger = null) => {
    if (!dialog.open) return;
    clearTimeout(closeTimer);
    indexDialogItems();
    setTransformOrigin(trigger);
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
    const trigger = target?.closest('[data-theme-settings]');
    if (trigger) reveal(trigger);
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
  observer.observe(dialog, {
    attributes: true,
    childList: true,
    attributeFilter: ['open'],
  });

  indexDialogItems();
})();
