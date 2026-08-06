from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one patch target for {label}, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE | re.DOTALL)
    if count != 1:
        raise SystemExit(f"Expected one regex patch target for {label}, found {count}")
    return updated


# Persist the app-provided Material palette before removing its URL parameters.
boot_path = Path("docs/assets/js/theme-boot.js")
boot = boot_path.read_text()
boot = regex_once(
    boot,
    r"  const params = new URLSearchParams\(location\.search\);.*?\n\n  const supportedThemes =",
    '''  const params = new URLSearchParams(location.search);
  const APP_THEME_STORAGE = 'xylune-app-theme-v1';
  const isHex = (value) => /^[0-9a-f]{6}$/i.test(value || '');
  const required = ['--primary', '--background', '--on-surface'];
  const urlColors = {};
  Object.entries(names).forEach(([parameter, variable]) => {
    const value = params.get(parameter);
    if (isHex(value)) urlColors[variable] = `#${value.toLowerCase()}`;
  });

  const readStoredAppTheme = () => {
    try {
      const stored = JSON.parse(localStorage.getItem(APP_THEME_STORAGE) || 'null');
      if (!stored || typeof stored !== 'object' || typeof stored.colors !== 'object') return null;
      const colors = {};
      Object.values(names).forEach((variable) => {
        const value = stored.colors[variable];
        if (/^#[0-9a-f]{6}$/i.test(value || '')) colors[variable] = value.toLowerCase();
      });
      if (!required.every((name) => colors[name])) return null;
      return {
        colors,
        dark: Boolean(stored.dark),
        dynamicLogo: Boolean(stored.dynamicLogo),
      };
    } catch (_) {
      return null;
    }
  };

  const urlAppTheme = required.every((name) => urlColors[name]) ? {
    colors: urlColors,
    dark: params.get('dark') === '1',
    dynamicLogo: params.get('dynamicLogo') === '1',
  } : null;
  if (urlAppTheme) {
    localStorage.setItem(APP_THEME_STORAGE, JSON.stringify(urlAppTheme));
  }
  if (params.has('dynamicLogo')) {
    localStorage.setItem('xylune-dynamic-icon', params.get('dynamicLogo') === '1' ? '1' : '0');
  }
  const appTheme = urlAppTheme || readStoredAppTheme();

  const supportedThemes =''',
    "stored app palette bootstrap",
)
boot = replace_once(
    boot,
    '''  let themePreference = supportedThemes.includes(urlTheme)
    ? urlTheme
    : supportedThemes.includes(storedTheme) ? storedTheme : 'dark';
  let schemePreference = supportedSchemes.includes(urlScheme)
    ? urlScheme
    : urlTheme === 'app' && appTheme
      ? 'app'
      : supportedSchemes.includes(storedScheme) ? storedScheme : 'xylune';''',
    '''  let themePreference = supportedThemes.includes(urlTheme)
    ? urlTheme
    : urlAppTheme
      ? 'app'
      : supportedThemes.includes(storedTheme) ? storedTheme : 'dark';
  let schemePreference = supportedSchemes.includes(urlScheme)
    ? urlScheme
    : urlAppTheme
      ? 'app'
      : urlTheme === 'app' && appTheme
        ? 'app'
        : supportedSchemes.includes(storedScheme) ? storedScheme : 'xylune';''',
    "URL app preference selection",
)
boot = replace_once(
    boot,
    '''  if (schemePreference === 'app') themePreference = 'app';

  const resolvedTheme =''',
    '''  if (schemePreference === 'app') themePreference = 'app';
  if (params.has('theme') || urlAppTheme) localStorage.setItem('xylune-theme', themePreference);
  if (params.has('scheme') || urlAppTheme) localStorage.setItem('xylune-scheme', schemePreference);

  const resolvedTheme =''',
    "persist imported appearance preferences",
)
boot = replace_once(
    boot,
    '''  const colorVariables = [...new Set([
    ...Object.values(names),
    ...Object.keys(basePalettes.dark),
    '--focus',
  ])];

  window.XylunePageTheme = {
    appTheme,
    colorVariables,
    fixedColors,
    supportedThemes,
    supportedSchemes,
    queryKeys: ['theme', 'scheme', 'dark', 'dynamicLogo', ...Object.keys(names)],
  };''',
    '''  const colorVariables = [...new Set([
    ...Object.values(names),
    ...Object.keys(basePalettes.dark),
    '--focus',
  ])];
  const queryKeys = ['theme', 'scheme', 'dark', 'dynamicLogo', ...Object.keys(names)];
  const cleanUrl = new URL(location.href);
  let removedAppearanceParameter = false;
  queryKeys.forEach((key) => {
    if (!cleanUrl.searchParams.has(key)) return;
    cleanUrl.searchParams.delete(key);
    removedAppearanceParameter = true;
  });
  if (removedAppearanceParameter) history.replaceState(null, '', cleanUrl);

  window.XylunePageTheme = {
    appTheme,
    colorVariables,
    fixedColors,
    supportedThemes,
    supportedSchemes,
    queryKeys,
  };''',
    "clean imported appearance URL",
)
boot_path.write_text(boot)


# Keep appearance state in localStorage and keep internal links parameter-free.
site_path = Path("docs/assets/js/site.js")
site = site_path.read_text()
site = replace_once(
    site,
    '''  const initialParams = new URLSearchParams(location.search);
  const storedDynamicIcon = localStorage.getItem('xylune-dynamic-icon');
  let dynamicIconEnabled = initialParams.has('dynamicLogo')
    ? initialParams.get('dynamicLogo') === '1'
    : storedDynamicIcon !== null
      ? storedDynamicIcon === '1'
      : Boolean(themeState.appTheme?.dynamicLogo);''',
    '''  const storedDynamicIcon = localStorage.getItem('xylune-dynamic-icon');
  let dynamicIconEnabled = storedDynamicIcon !== null
    ? storedDynamicIcon === '1'
    : Boolean(themeState.appTheme?.dynamicLogo);''',
    "local dynamic icon initialization",
)
site = replace_once(
    site,
    '''  function syncAppearanceLinks() {
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
  }''',
    '''  function syncAppearanceLinks() {
    document.querySelectorAll('a[href]').forEach((anchor) => {
      const target = new URL(anchor.getAttribute('href'), location.href);
      if (target.origin !== location.origin) return;
      themeState.queryKeys.forEach((key) => target.searchParams.delete(key));
      anchor.href = target.href;
    });
  }''',
    "parameter-free internal links",
)
site = replace_once(
    site,
    '''  function updateAppearanceUrl(themePreference, schemePreference) {
    const url = new URL(location.href);
    url.searchParams.set('theme', themePreference);
    url.searchParams.set('scheme', schemePreference);
    url.searchParams.set('dynamicLogo', dynamicIconEnabled ? '1' : '0');
    history.replaceState(null, '', url);
  }''',
    '''  function cleanAppearanceUrl() {
    const url = new URL(location.href);
    let changed = false;
    themeState.queryKeys.forEach((key) => {
      if (!url.searchParams.has(key)) return;
      url.searchParams.delete(key);
      changed = true;
    });
    if (changed) history.replaceState(null, '', url);
  }''',
    "appearance URL cleanup",
)
site = replace_once(
    site,
    '''    if (persist && themePreference !== 'app') {
      localStorage.setItem('xylune-theme', themePreference);
    }
    if (persist && schemePreference !== 'app') {
      localStorage.setItem('xylune-scheme', schemePreference);
    }

    updateAppearanceUrl(themePreference, schemePreference);
    syncAppearanceLinks();''',
    '''    if (persist) {
      localStorage.setItem('xylune-theme', themePreference);
      localStorage.setItem('xylune-scheme', schemePreference);
    }

    cleanAppearanceUrl();
    syncAppearanceLinks();''',
    "local appearance persistence",
)
site = replace_once(
    site,
    '''    syncBrandLogo(schemePreference);
    syncDynamicIconControls();
    updateAppearanceUrl(themePreference, schemePreference);
    syncAppearanceLinks();''',
    '''    syncBrandLogo(schemePreference);
    syncDynamicIconControls();
    cleanAppearanceUrl();
    syncAppearanceLinks();''',
    "dynamic icon URL cleanup",
)
site = replace_once(
    site,
    '''    const expandedTitleShift = 58;
    const expandedTitleScale = 1.18;''',
    '''    const expandedTitleShift = 58;
    const expandedTitleScale = Number.parseFloat(
      getComputedStyle(scroller).getPropertyValue('--xylune-title-expanded-scale'),
    ) || 1.18;''',
    "page-specific expanded title scale",
)
site_path.write_text(site)


# Add the banner to the home page.
home_path = Path("docs/index.html")
home = home_path.read_text()
home = replace_once(
    home,
    '''      <div class="home-body">
        <section class="home-content" aria-labelledby="home-headline">''',
    '''      <div class="home-body">
        <div class="home-banner" aria-label="Xylune banner">
          <img src="https://raw.githubusercontent.com/omerfaruknehir/Xylune/main/branding/xylune-banner.png" alt="Xylune">
        </div>
        <section class="home-content" aria-labelledby="home-headline">''',
    "home banner",
)
home_path.write_text(home)


# Make only the home title substantially larger while expanded and style the banner.
app_bar_path = Path("docs/assets/css/app-bar.css")
app_bar = app_bar_path.read_text()
app_bar = replace_once(
    app_bar,
    '''  --xylune-title-shift: 58px;
  --xylune-title-scale: 1.18;''',
    '''  --xylune-title-shift: 58px;
  --xylune-title-expanded-scale: 1.18;
  --xylune-title-scale: var(--xylune-title-expanded-scale);''',
    "expanded title scale variable",
)
app_bar = replace_once(
    app_bar,
    '''  scroll-padding-top: var(--xylune-app-bar-compact-height);
}

.document-app-bar {''',
    '''  scroll-padding-top: var(--xylune-app-bar-compact-height);
}

.home-shell.page-with-app-bar {
  --xylune-title-expanded-scale: 1.82;
}

.document-app-bar {''',
    "larger home title",
)
release_css = '''.release-list {
  display: grid;
  gap: 14px;
  margin-top: 24px;
}

.release-status {
  margin: 0;
  padding: 18px;
  border: 1px solid var(--outline-variant);
  border-radius: 14px;
  background: var(--surface-low);
  color: var(--on-surface-variant);
}

.release-card {
  overflow: hidden;
  border: 1px solid var(--outline-variant);
  border-radius: 16px;
  background: var(--surface-low);
}

.release-card[open] {
  background: var(--surface-container);
}

.release-card__toggle {
  display: grid;
  min-height: 86px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 17px 20px;
  cursor: pointer;
  list-style: none;
}

.release-card__toggle::-webkit-details-marker {
  display: none;
}

.release-card__toggle:hover {
  background: color-mix(in srgb, var(--primary-container) 22%, transparent);
}

.release-card__heading {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.release-card__title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.release-card__title-row h2 {
  min-width: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  font-size: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.release-card__meta {
  margin: 0;
  color: var(--on-surface-variant);
  font-size: 14px;
}

.release-card__chevron {
  color: var(--on-surface-variant);
  transition: transform 180ms ease;
}

.release-card[open] .release-card__chevron {
  transform: rotate(180deg);
}

.release-card__body {
  display: grid;
  gap: 18px;
  padding: 18px 20px 20px;
  border-top: 1px solid var(--outline-variant);
}

.release-notes {
  color: var(--on-surface-variant);
}

.release-notes > :first-child {
  margin-top: 0;
}

.release-notes > :last-child {
  margin-bottom: 0;
}

.release-notes h3,
.release-notes h4 {
  margin: 1.35em 0 0.45em;
  padding: 0;
  border: 0;
  color: var(--on-surface);
}

.release-notes h3 {
  font-size: 19px;
}

.release-notes h4 {
  font-size: 17px;
}

.release-notes p,
.release-notes ul,
.release-notes ol {
  margin: 0.65em 0;
}

.release-notes ul,
.release-notes ol {
  padding-left: 1.35em;
}

.release-notes code {
  padding: 0.12em 0.34em;
  border-radius: 5px;
  background: var(--surface-low);
  color: var(--on-surface);
}

.release-notes pre {
  overflow-x: auto;
  padding: 14px;
  border-radius: 10px;
  background: var(--surface-low);
}

.release-notes pre code {
  padding: 0;
  background: transparent;
}

.release-notes a {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}

.release-notes .material-symbols-rounded,
.external-link-icon {
  font-size: 17px;
}

.release-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.release-card__actions .button,
.release-list__footer .button {
  min-height: 44px;
  padding-inline: 18px;
  font-size: 15px;
}

.release-badge {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--primary-container);
  color: var(--on-primary-container);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.release-list__footer {
  display: flex;
  justify-content: center;
  padding-top: 6px;
}

'''
app_bar = regex_once(
    app_bar,
    r"\.release-intro \{.*?\n\n@media \(max-width: 820px\) \{",
    release_css + "@media (max-width: 820px) {",
    "expandable release card styles",
)
app_bar_path.write_text(app_bar)

site_css_path = Path("docs/assets/css/site.css")
site_css = site_css_path.read_text()
site_css = replace_once(
    site_css,
    '''.home-content {
  width: min(670px, 100%);
  margin-left: clamp(28px, 8vw, 100px);
}
''',
    '''.home-banner {
  width: min(980px, 100%);
  margin: 0 auto 30px;
  overflow: hidden;
  border-radius: 22px;
  background: var(--surface-low);
}

.home-banner img {
  display: block;
  width: 100%;
  height: auto;
}

.home-content {
  width: min(670px, 100%);
  margin-left: clamp(28px, 8vw, 100px);
}
''',
    "home banner styles",
)
site_css = replace_once(
    site_css,
    '''  .home-content {
    width: 100%;
    margin: 0;
  }
''',
    '''  .home-banner {
    margin-bottom: 22px;
    border-radius: 16px;
  }

  .home-content {
    width: 100%;
    margin: 0;
  }
''',
    "mobile home banner styles",
)
site_css_path.write_text(site_css)


# Render notes in-page, keep only ten releases, and mark GitHub links as external.
releases_path = Path("docs/assets/js/releases.js")
releases_path.write_text(r'''(() => {
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
''')


# Add regression coverage for the requested website behavior.
test_path = Path("app/src/test/java/app/xylune/chat/ui/LegalWebsiteIntegrationTest.kt")
test = test_path.read_text()
test = replace_once(
    test,
    '''        assertTrue(boot.contains("queryKeys: ['theme', 'scheme'"))''',
    '''        assertTrue(boot.contains("const queryKeys = ['theme', 'scheme'"))''',
    "query key assertion",
)
test = replace_once(
    test,
    '''        assertTrue(boot.contains("dynamicLogo: params.get('dynamicLogo') === '1'"))''',
    '''        assertTrue(boot.contains("dynamicLogo: params.get('dynamicLogo') === '1'"))
        assertTrue(boot.contains("const APP_THEME_STORAGE = 'xylune-app-theme-v1'"))
        assertTrue(boot.contains("localStorage.setItem(APP_THEME_STORAGE, JSON.stringify(urlAppTheme))"))
        assertTrue(boot.contains("cleanUrl.searchParams.delete(key)"))
        assertTrue(boot.contains("history.replaceState(null, '', cleanUrl)"))''',
    "stored app theme assertions",
)
test = replace_once(
    test,
    '''        assertTrue(site.contains("url.searchParams.set('dynamicLogo', dynamicIconEnabled ? '1' : '0')"))''',
    '''        assertTrue(site.contains("localStorage.setItem('xylune-dynamic-icon', dynamicIconEnabled ? '1' : '0')"))
        assertTrue(site.contains("themeState.queryKeys.forEach((key) => target.searchParams.delete(key))"))
        assertTrue(!site.contains("url.searchParams.set('dynamicLogo'"))''',
    "parameter-free dynamic icon assertions",
)
test = replace_once(
    test,
    '''        assertTrue(site.contains("--xylune-title-scale"))''',
    '''        assertTrue(site.contains("--xylune-title-scale"))
        assertTrue(site.contains("getPropertyValue('--xylune-title-expanded-scale')"))
        assertTrue(css.contains(".home-shell.page-with-app-bar"))
        assertTrue(css.contains("--xylune-title-expanded-scale: 1.82"))''',
    "larger home title assertions",
)
test = regex_once(
    test,
    r'''    @Test
    fun `release page orders versions without exposing implementation notes`\(\) \{.*?\n    \}
\}''',
    '''    @Test
    fun `home uses banner and release notes expand in page`\(\) {
        val releases = repositoryFile("docs/assets/js/releases.js").readText()
        val page = repositoryFile("docs/releases/index.html").readText()
        val home = repositoryFile("docs/index.html").readText()
        val css = repositoryFile("docs/assets/css/app-bar.css").readText()
        assertTrue(releases.contains("function parseSemanticVersion(value)"))
        assertTrue(releases.contains("right.numbers[index] - left.numbers[index]"))
        assertTrue(releases.contains("const MAX_RELEASES = 10"))
        assertTrue(releases.contains(".slice(0, MAX_RELEASES)"))
        assertTrue(releases.contains("card.open = index === 0"))
        assertTrue(releases.contains("renderReleaseNotes(release.body)"))
        assertTrue(releases.contains("'Show all releases'"))
        assertTrue(releases.contains("'open_in_new'"))
        assertTrue(!releases.contains("actionLink('Release notes'"))
        assertTrue(page.contains("data-release-list"))
        assertTrue(!page.contains("sorted numerically"))
        assertTrue(!page.contains("regardless of GitHub publication timestamps"))
        assertTrue(home.contains("class=\"home-banner\""))
        assertTrue(home.contains("branding/xylune-banner.png"))
        assertTrue(home.contains("href=\"releases/\""))
        assertTrue(css.contains(".release-card__toggle"))
        assertTrue(css.contains(".release-list__footer"))
    }
}''',
    "release and home integration test",
)
test_path.write_text(test)


# Mention the final Pages changes in the release notes.
notes_path = Path("docs/releases/RELEASE_NOTES_0.24.5.md")
notes = notes_path.read_text()
notes = replace_once(
    notes,
    '''- Keeps theme controls compact and matches website branding to Xylune's launcher-icon variants.
''',
    '''- Keeps theme controls compact and matches website branding to Xylune's launcher-icon variants.
- Adds the Xylune banner and a larger expanded home title.
- Shows release notes directly on the Releases page, keeps the latest notes expanded, lists the ten newest versions, and clearly marks GitHub links as external.
- Stores app-provided appearance parameters locally and removes them from the visible URL after the theme is applied.
''',
    "website release notes",
)
notes_path.write_text(notes)
