(() => {
  const container = document.querySelector('[data-release-list]');
  if (!container) return;

  const repository = container.dataset.repository || 'omerfaruknehir/Xylune';
  const MAX_RELEASES = 10;
  const endpoint = `https://api.github.com/repos/${repository}/releases?per_page=${MAX_RELEASES}`;
  const cacheKey = `xylune-release-list-v2:${repository}`;
  const cacheMaxAgeMs = 6 * 60 * 60 * 1000;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let renderedSignature = '';

  function parseSemanticVersion(value) {
    const match = String(value || '').trim().match(/^v?(\d+)\.(\d+)\.(\d+)(?:[-+]([^+]+))?$/i);
    if (!match) return null;
    return {
      numbers: [Number(match[1]), Number(match[2]), Number(match[3])],
      suffix: match[4] || null,
    };
  }

  function compareSemanticVersionsDescending(leftRelease, rightRelease) {
    const left = parseSemanticVersion(leftRelease.tag_name || leftRelease.name);
    const right = parseSemanticVersion(rightRelease.tag_name || rightRelease.name);
    if (left && right) {
      for (let index = 0; index < 3; index += 1) {
        const difference = right.numbers[index] - left.numbers[index];
        if (difference !== 0) return difference;
      }
      if (left.suffix === null && right.suffix !== null) return -1;
      if (left.suffix !== null && right.suffix === null) return 1;
      if (left.suffix !== right.suffix) return String(right.suffix).localeCompare(String(left.suffix));
    } else if (left) {
      return -1;
    } else if (right) {
      return 1;
    }

    const leftTime = Date.parse(leftRelease.published_at || leftRelease.created_at || '') || 0;
    const rightTime = Date.parse(rightRelease.published_at || rightRelease.created_at || '') || 0;
    return rightTime - leftTime;
  }

  function normalizeReleases(releases) {
    return (Array.isArray(releases) ? releases : [])
      .filter((release) => release && !release.draft)
      .sort(compareSemanticVersionsDescending)
      .slice(0, MAX_RELEASES);
  }

  function releaseSignature(releases) {
    return releases
      .map((release) => [
        release.id || release.tag_name || release.name,
        release.updated_at || release.published_at || '',
        Array.isArray(release.assets) ? release.assets.length : 0,
      ].join(':'))
      .join('|');
  }

  function readCachedReleases() {
    try {
      const cached = JSON.parse(localStorage.getItem(cacheKey) || 'null');
      if (!cached || !Array.isArray(cached.releases)) return null;
      if (!Number.isFinite(cached.storedAt)) return null;
      return {
        releases: normalizeReleases(cached.releases),
        fresh: Date.now() - cached.storedAt < cacheMaxAgeMs,
      };
    } catch (_) {
      return null;
    }
  }

  function writeCachedReleases(releases) {
    try {
      localStorage.setItem(cacheKey, JSON.stringify({
        storedAt: Date.now(),
        releases,
      }));
    } catch (_) {
      // Storage may be unavailable in private browsing or embedded web views.
    }
  }

  function releaseVersion(release) {
    return String(release.tag_name || release.name || 'Release').replace(/^v/i, '');
  }

  function releaseDate(release) {
    const value = release.published_at || release.created_at;
    if (!value) return '';
    return new Intl.DateTimeFormat(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(new Date(value));
  }

  function icon(name, className = '') {
    const element = document.createElement('span');
    element.className = `material-symbols-rounded ${className}`.trim();
    element.setAttribute('aria-hidden', 'true');
    element.textContent = name;
    return element;
  }

  function actionLink(label, href, { primary = false, leadingIcon = null, external = false } = {}) {
    const link = document.createElement('a');
    link.className = `button ${primary ? 'button-primary' : 'button-text'}`;
    link.href = href;
    if (external) {
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
    }
    if (leadingIcon) link.append(icon(leadingIcon));
    const text = document.createElement('span');
    text.textContent = label;
    link.append(text);
    if (external) link.append(icon('open_in_new', 'external-link-icon'));
    return link;
  }

  function appendInlineMarkup(parent, value) {
    const text = String(value || '');
    const pattern = /(`[^`]+`|\*\*[^*]+\*\*|\[([^\]]+)]\((https?:\/\/[^)]+)\))/g;
    let cursor = 0;
    for (const match of text.matchAll(pattern)) {
      if (match.index > cursor) parent.append(document.createTextNode(text.slice(cursor, match.index)));
      const token = match[0];
      if (token.startsWith('`')) {
        const code = document.createElement('code');
        code.textContent = token.slice(1, -1);
        parent.append(code);
      } else if (token.startsWith('**')) {
        const strong = document.createElement('strong');
        strong.textContent = token.slice(2, -2);
        parent.append(strong);
      } else {
        const link = document.createElement('a');
        link.href = match[3];
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.append(document.createTextNode(match[2]), icon('open_in_new', 'external-link-icon'));
        parent.append(link);
      }
      cursor = match.index + token.length;
    }
    if (cursor < text.length) parent.append(document.createTextNode(text.slice(cursor)));
  }

  function renderReleaseNotes(markdown) {
    const root = document.createElement('div');
    root.className = 'release-notes';
    const lines = String(markdown || '').replace(/\r\n?/g, '\n').split('\n');
    let paragraph = [];
    let list = null;
    let listType = '';
    let codeLines = null;

    const flushParagraph = () => {
      if (paragraph.length === 0) return;
      const node = document.createElement('p');
      appendInlineMarkup(node, paragraph.join(' '));
      root.append(node);
      paragraph = [];
    };
    const endList = () => {
      list = null;
      listType = '';
    };

    lines.forEach((line) => {
      if (/^```/.test(line.trim())) {
        flushParagraph();
        endList();
        if (codeLines === null) {
          codeLines = [];
        } else {
          const pre = document.createElement('pre');
          const code = document.createElement('code');
          code.textContent = codeLines.join('\n');
          pre.append(code);
          root.append(pre);
          codeLines = null;
        }
        return;
      }
      if (codeLines !== null) {
        codeLines.push(line);
        return;
      }
      if (line.trim() === '') {
        flushParagraph();
        endList();
        return;
      }

      const heading = line.match(/^(#{1,6})\s+(.+)$/);
      if (heading) {
        flushParagraph();
        endList();
        const node = document.createElement(heading[1].length <= 2 ? 'h3' : 'h4');
        appendInlineMarkup(node, heading[2]);
        root.append(node);
        return;
      }

      const unordered = line.match(/^\s*[-*+]\s+(.+)$/);
      const ordered = line.match(/^\s*\d+[.)]\s+(.+)$/);
      const item = unordered || ordered;
      if (item) {
        flushParagraph();
        const nextType = unordered ? 'ul' : 'ol';
        if (!list || listType !== nextType) {
          list = document.createElement(nextType);
          listType = nextType;
          root.append(list);
        }
        const node = document.createElement('li');
        appendInlineMarkup(node, item[1]);
        list.append(node);
        return;
      }

      endList();
      paragraph.push(line.trim());
    });

    if (codeLines !== null && codeLines.length > 0) {
      const pre = document.createElement('pre');
      const code = document.createElement('code');
      code.textContent = codeLines.join('\n');
      pre.append(code);
      root.append(pre);
    }
    flushParagraph();
    if (!root.hasChildNodes()) {
      const fallback = document.createElement('p');
      fallback.textContent = 'No release notes were provided for this build.';
      root.append(fallback);
    }
    return root;
  }

  function setReleaseOpen(card, shouldOpen) {
    const toggle = card.querySelector('.release-card__toggle');
    const content = card.querySelector('.release-card__content');
    if (!toggle || !content || card.classList.contains('is-animating')) return;

    const finish = () => {
      content.style.removeProperty('height');
      content.style.removeProperty('opacity');
      content.style.removeProperty('overflow');
      card.classList.remove('is-animating');
      toggle.removeAttribute('aria-disabled');
    };

    if (reducedMotion.matches || typeof content.animate !== 'function') {
      card.open = shouldOpen;
      toggle.setAttribute('aria-expanded', String(shouldOpen));
      return;
    }

    card.classList.add('is-animating');
    toggle.setAttribute('aria-disabled', 'true');
    toggle.setAttribute('aria-expanded', String(shouldOpen));
    content.style.overflow = 'hidden';

    if (shouldOpen) {
      card.open = true;
      content.style.height = '0px';
      content.style.opacity = '0';
      const targetHeight = content.scrollHeight;
      const animation = content.animate([
        { height: '0px', opacity: 0, transform: 'translateY(-6px)' },
        { height: `${targetHeight}px`, opacity: 1, transform: 'translateY(0)' },
      ], {
        duration: 260,
        easing: 'cubic-bezier(0.2, 0, 0, 1)',
      });
      animation.onfinish = finish;
      animation.oncancel = finish;
      return;
    }

    const currentHeight = content.getBoundingClientRect().height;
    content.style.height = `${currentHeight}px`;
    content.style.opacity = '1';
    const animation = content.animate([
      { height: `${currentHeight}px`, opacity: 1, transform: 'translateY(0)' },
      { height: '0px', opacity: 0, transform: 'translateY(-6px)' },
    ], {
      duration: 220,
      easing: 'cubic-bezier(0.4, 0, 1, 1)',
    });
    animation.onfinish = () => {
      card.open = false;
      finish();
    };
    animation.oncancel = finish;
  }

  function renderRelease(release, index) {
    const card = document.createElement('details');
    card.className = 'release-card';
    card.open = index === 0;

    const toggle = document.createElement('summary');
    toggle.className = 'release-card__toggle';
    toggle.setAttribute('aria-expanded', String(card.open));
    const heading = document.createElement('div');
    heading.className = 'release-card__heading';
    const titleRow = document.createElement('div');
    titleRow.className = 'release-card__title-row';
    const title = document.createElement('h2');
    title.textContent = `Xylune ${releaseVersion(release)}`;
    titleRow.append(title);
    if (index === 0) {
      const badge = document.createElement('span');
      badge.className = 'release-badge';
      badge.textContent = 'Latest';
      titleRow.append(badge);
    }
    const meta = document.createElement('p');
    meta.className = 'release-card__meta';
    meta.textContent = [releaseDate(release), release.prerelease ? 'Pre-release' : null]
      .filter(Boolean)
      .join(' · ');
    heading.append(titleRow, meta);
    toggle.append(heading, icon('expand_more', 'release-card__chevron'));

    const body = document.createElement('div');
    body.className = 'release-card__body';
    body.append(renderReleaseNotes(release.body));

    const actions = document.createElement('div');
    actions.className = 'release-card__actions';
    const assets = Array.isArray(release.assets) ? release.assets : [];
    const apk = assets.find((asset) => /-release\.apk$/i.test(asset.name))
      || assets.find((asset) => /\.apk$/i.test(asset.name));
    if (apk?.browser_download_url) {
      actions.append(actionLink('Download APK', apk.browser_download_url, {
        primary: true,
        leadingIcon: 'download',
      }));
    }
    if (release.html_url) {
      actions.append(actionLink('Open on GitHub', release.html_url, { external: true }));
    }
    if (actions.hasChildNodes()) body.append(actions);

    const content = document.createElement('div');
    content.className = 'release-card__content';
    content.append(body);

    toggle.addEventListener('click', (event) => {
      event.preventDefault();
      setReleaseOpen(card, !card.open);
    });

    card.append(toggle, content);
    return card;
  }

  function allReleasesFooter() {
    const footer = document.createElement('div');
    footer.className = 'release-list__footer';
    footer.append(actionLink(
      'Show all releases',
      `https://github.com/${repository}/releases`,
      { external: true },
    ));
    return footer;
  }

  function renderReleaseList(releases) {
    const sorted = normalizeReleases(releases);
    if (sorted.length === 0) return false;

    const signature = releaseSignature(sorted);
    if (signature === renderedSignature) return true;

    const fragment = document.createDocumentFragment();
    sorted.forEach((release, index) => fragment.append(renderRelease(release, index)));
    fragment.append(allReleasesFooter());
    container.replaceChildren(fragment);
    container.removeAttribute('aria-busy');
    renderedSignature = signature;
    return true;
  }

  function renderFailure() {
    const fallback = document.createElement('p');
    fallback.className = 'release-status';
    fallback.append('The live release list could not be loaded. ');
    fallback.append(actionLink(
      'Open releases on GitHub',
      `https://github.com/${repository}/releases`,
      { external: true },
    ));
    container.replaceChildren(fallback);
    container.removeAttribute('aria-busy');
  }

  async function loadLiveReleases(hasCachedContent) {
    try {
      const response = await fetch(endpoint, {
        mode: 'cors',
        credentials: 'omit',
        cache: 'default',
      });
      if (!response.ok) throw new Error(`GitHub returned HTTP ${response.status}`);
      const releases = normalizeReleases(await response.json());
      if (releases.length === 0) throw new Error('No published releases were returned');
      writeCachedReleases(releases);
      renderReleaseList(releases);
    } catch (_) {
      if (!hasCachedContent) renderFailure();
    }
  }

  const cached = readCachedReleases();
  const hasCachedContent = Boolean(cached && renderReleaseList(cached.releases));
  if (!hasCachedContent) container.setAttribute('aria-busy', 'true');

  const refresh = () => loadLiveReleases(hasCachedContent);
  if (hasCachedContent && 'requestIdleCallback' in window) {
    window.requestIdleCallback(refresh, { timeout: cached.fresh ? 1500 : 500 });
  } else if (hasCachedContent) {
    window.setTimeout(refresh, cached.fresh ? 250 : 0);
  } else {
    refresh();
  }

  window.XyluneReleaseSort = {
    parseSemanticVersion,
    compareSemanticVersionsDescending,
  };
})();
