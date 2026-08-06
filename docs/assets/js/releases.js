(() => {
  const container = document.querySelector('[data-release-list]');
  if (!container) return;

  const repository = container.dataset.repository || 'omerfaruknehir/Xylune';
  const endpoint = `https://api.github.com/repos/${repository}/releases?per_page=100`;
  const MAX_RELEASES = 10;

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

  function renderRelease(release, index) {
    const card = document.createElement('details');
    card.className = 'release-card';
    card.open = index === 0;

    const toggle = document.createElement('summary');
    toggle.className = 'release-card__toggle';
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

    card.append(toggle, body);
    return card;
  }

  function appendAllReleasesLink() {
    const footer = document.createElement('div');
    footer.className = 'release-list__footer';
    footer.append(actionLink(
      'Show all releases',
      `https://github.com/${repository}/releases`,
      { external: true },
    ));
    container.append(footer);
  }

  fetch(endpoint, {
    headers: {
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
    },
  })
    .then((response) => {
      if (!response.ok) throw new Error(`GitHub returned HTTP ${response.status}`);
      return response.json();
    })
    .then((releases) => {
      const sorted = releases
        .filter((release) => !release.draft)
        .sort(compareSemanticVersionsDescending)
        .slice(0, MAX_RELEASES);
      container.replaceChildren();
      if (sorted.length === 0) throw new Error('No published releases were returned');
      sorted.forEach((release, index) => container.append(renderRelease(release, index)));
      appendAllReleasesLink();
    })
    .catch(() => {
      const fallback = document.createElement('p');
      fallback.className = 'release-status';
      fallback.append('The live release list could not be loaded. ');
      fallback.append(actionLink(
        'Open releases on GitHub',
        `https://github.com/${repository}/releases`,
        { external: true },
      ));
      container.replaceChildren(fallback);
    });

  window.XyluneReleaseSort = {
    parseSemanticVersion,
    compareSemanticVersionsDescending,
  };
})();
