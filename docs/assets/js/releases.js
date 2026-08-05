(() => {
  const container = document.querySelector('[data-release-list]');
  if (!container) return;

  const repository = container.dataset.repository || 'omerfaruknehir/Xylune';
  const endpoint = `https://api.github.com/repos/${repository}/releases?per_page=100`;

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

  function plainSummary(markdown) {
    return String(markdown || '')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\[([^\]]+)]\([^\)]+\)/g, '$1')
      .replace(/[>*_~-]/g, '')
      .split(/\n\s*\n/)
      .map((paragraph) => paragraph.replace(/\s+/g, ' ').trim())
      .find(Boolean)
      ?.slice(0, 280) || 'Release notes and verified build assets are available on GitHub.';
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

  function actionLink(label, href, primary = false) {
    const link = document.createElement('a');
    link.className = `button ${primary ? 'button-primary' : 'button-text'}`;
    link.href = href;
    link.textContent = label;
    return link;
  }

  function renderRelease(release, index) {
    const card = document.createElement('article');
    card.className = 'release-card';

    const heading = document.createElement('div');
    heading.className = 'release-card__heading';
    const title = document.createElement('h2');
    title.textContent = `Xylune ${releaseVersion(release)}`;
    heading.append(title);
    if (index === 0) {
      const badge = document.createElement('span');
      badge.className = 'release-badge';
      badge.textContent = 'Latest';
      heading.append(badge);
    }

    const meta = document.createElement('p');
    meta.className = 'release-card__meta';
    meta.textContent = [releaseDate(release), release.prerelease ? 'Pre-release' : null]
      .filter(Boolean)
      .join(' · ');

    const summary = document.createElement('p');
    summary.className = 'release-card__summary';
    summary.textContent = plainSummary(release.body);

    const actions = document.createElement('div');
    actions.className = 'release-card__actions';
    const assets = Array.isArray(release.assets) ? release.assets : [];
    const apk = assets.find((asset) => /-release\.apk$/i.test(asset.name))
      || assets.find((asset) => /\.apk$/i.test(asset.name));
    if (apk?.browser_download_url) actions.append(actionLink('Download APK', apk.browser_download_url, true));
    if (release.html_url) actions.append(actionLink('Release notes', release.html_url));

    card.append(heading, meta, summary, actions);
    return card;
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
        .sort(compareSemanticVersionsDescending);
      container.replaceChildren();
      if (sorted.length === 0) throw new Error('No published releases were returned');
      sorted.forEach((release, index) => container.append(renderRelease(release, index)));
    })
    .catch(() => {
      const fallback = document.createElement('p');
      fallback.className = 'release-status';
      fallback.append('The live release list could not be loaded. ');
      const link = document.createElement('a');
      link.href = `https://github.com/${repository}/releases`;
      link.textContent = 'Open releases on GitHub.';
      fallback.append(link);
      container.replaceChildren(fallback);
    });

  window.XyluneReleaseSort = {
    parseSemanticVersion,
    compareSemanticVersionsDescending,
  };
})();
