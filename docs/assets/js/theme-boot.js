(() => {
  const names = {
    primary: '--primary',
    onPrimary: '--on-primary',
    primaryContainer: '--primary-container',
    onPrimaryContainer: '--on-primary-container',
    background: '--background',
    surface: '--surface',
    surfaceLow: '--surface-low',
    surfaceContainer: '--surface-container',
    onSurface: '--on-surface',
    onSurfaceVariant: '--on-surface-variant',
    outline: '--outline',
    outlineVariant: '--outline-variant',
    rail: '--rail',
  };
  const params = new URLSearchParams(location.search);
  const isHex = (value) => /^[0-9a-f]{6}$/i.test(value || '');
  const colors = {};
  Object.entries(names).forEach(([parameter, variable]) => {
    const value = params.get(parameter);
    if (isHex(value)) colors[variable] = `#${value.toLowerCase()}`;
  });
  const required = ['--primary', '--background', '--on-surface'];
  const appTheme = required.every((name) => colors[name]) ? {
    colors,
    dark: params.get('dark') === '1',
  } : null;
  const urlPreference = params.get('theme');
  const stored = localStorage.getItem('xylune-theme');
  const supported = ['dark', 'light', 'system'];
  const preference = urlPreference === 'app' && appTheme
    ? 'app'
    : supported.includes(urlPreference)
      ? urlPreference
      : supported.includes(stored) ? stored : 'dark';
  const resolved = preference === 'app'
    ? (appTheme.dark ? 'dark' : 'light')
    : preference === 'system'
      ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : preference;
  if (preference === 'app') {
    Object.entries(appTheme.colors).forEach(([name, value]) => document.documentElement.style.setProperty(name, value));
  }
  document.documentElement.dataset.theme = resolved;
  document.documentElement.dataset.themePreference = preference;
  document.documentElement.style.colorScheme = resolved;
  window.XylunePageTheme = {
    appTheme,
    colorVariables: Object.values(names),
    queryKeys: ['theme', 'dark', ...Object.keys(names)],
  };
})();
