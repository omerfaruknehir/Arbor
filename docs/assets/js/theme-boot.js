(() => {
  const bootScript = document.currentScript;
  if (bootScript?.src && !document.querySelector('link[data-xylune-appearance]')) {
    const appearance = document.createElement('link');
    appearance.rel = 'stylesheet';
    appearance.href = new URL('../css/appearance.css', bootScript.src).href;
    appearance.dataset.xyluneAppearance = '';
    document.head.append(appearance);
  }

  const names = {
    primary: '--primary',
    onPrimary: '--on-primary',
    primaryContainer: '--primary-container',
    onPrimaryContainer: '--on-primary-container',
    secondary: '--secondary',
    onSecondary: '--on-secondary',
    secondaryContainer: '--secondary-container',
    onSecondaryContainer: '--on-secondary-container',
    tertiary: '--tertiary',
    onTertiary: '--on-tertiary',
    tertiaryContainer: '--tertiary-container',
    onTertiaryContainer: '--on-tertiary-container',
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

  const basePalettes = {
    dark: {
      '--primary': '#99d5b1',
      '--on-primary': '#003921',
      '--primary-container': '#0d5033',
      '--on-primary-container': '#b5f1cc',
      '--secondary': '#b5ccbc',
      '--on-secondary': '#213529',
      '--secondary-container': '#374b3e',
      '--on-secondary-container': '#d1e8d7',
      '--tertiary': '#a5cddd',
      '--on-tertiary': '#073541',
      '--tertiary-container': '#254c59',
      '--on-tertiary-container': '#c1eafb',
      '--background': '#101411',
      '--surface': '#101411',
      '--surface-low': '#181c19',
      '--surface-container': '#1c201d',
      '--on-surface': '#dfe4df',
      '--on-surface-variant': '#c1c9c2',
      '--outline': '#8b938c',
      '--outline-variant': '#414942',
      '--rail': '#0b0f0c',
      '--scrim': 'rgb(0 0 0 / 52%)',
    },
    light: {
      '--primary': '#286448',
      '--on-primary': '#ffffff',
      '--primary-container': '#b5f1cc',
      '--on-primary-container': '#002112',
      '--secondary': '#4e6356',
      '--on-secondary': '#ffffff',
      '--secondary-container': '#d1e8d7',
      '--on-secondary-container': '#0b1f14',
      '--tertiary': '#3d6472',
      '--on-tertiary': '#ffffff',
      '--tertiary-container': '#c1eafb',
      '--on-tertiary-container': '#001f29',
      '--background': '#f7faf7',
      '--surface': '#f7faf7',
      '--surface-low': '#f1f4f1',
      '--surface-container': '#ebeeeb',
      '--on-surface': '#181d1a',
      '--on-surface-variant': '#414942',
      '--outline': '#717972',
      '--outline-variant': '#c1c9c2',
      '--rail': '#ffffff',
      '--scrim': 'rgb(0 0 0 / 32%)',
    },
  };

  const paletteAccents = {
    xylune: { dark: {}, light: {} },
    graphite: {
      dark: {
        '--primary': '#a9c7f8',
        '--on-primary': '#0d3058',
        '--primary-container': '#29486f',
        '--on-primary-container': '#d5e3ff',
        '--secondary': '#c3c6d0',
        '--secondary-container': '#41464f',
        '--on-secondary-container': '#dee2ec',
        '--tertiary': '#e5bfa6',
        '--tertiary-container': '#5a402d',
        '--on-tertiary-container': '#ffdcc4',
      },
      light: {
        '--primary': '#425f86',
        '--primary-container': '#d5e3ff',
        '--on-primary-container': '#001c3a',
        '--secondary': '#595e68',
        '--secondary-container': '#dee2ec',
        '--on-secondary-container': '#171b22',
        '--tertiary': '#745b46',
        '--tertiary-container': '#ffdcc4',
        '--on-tertiary-container': '#2b1608',
      },
    },
    ocean: {
      dark: {
        '--primary': '#54d6f2',
        '--on-primary': '#00363f',
        '--primary-container': '#004e5d',
        '--on-primary-container': '#aaedff',
        '--secondary': '#b1cbd2',
        '--secondary-container': '#324b52',
        '--on-secondary-container': '#cde7ee',
        '--tertiary': '#bec6ea',
        '--tertiary-container': '#3f4664',
        '--on-tertiary-container': '#dde1ff',
      },
      light: {
        '--primary': '#00677a',
        '--primary-container': '#aaedff',
        '--on-primary-container': '#001f26',
        '--secondary': '#49636a',
        '--secondary-container': '#cde7ee',
        '--on-secondary-container': '#041f25',
        '--tertiary': '#565e7d',
        '--tertiary-container': '#dde1ff',
        '--on-tertiary-container': '#121a37',
      },
    },
    violet: {
      dark: {
        '--primary': '#d1bcff',
        '--on-primary': '#38205f',
        '--primary-container': '#4f3776',
        '--on-primary-container': '#eaddff',
        '--secondary': '#cbc2db',
        '--secondary-container': '#4a4458',
        '--on-secondary-container': '#e8def8',
        '--tertiary': '#efb8c8',
        '--tertiary-container': '#633b48',
        '--on-tertiary-container': '#ffd9e3',
      },
      light: {
        '--primary': '#67508f',
        '--primary-container': '#eaddff',
        '--on-primary-container': '#22005d',
        '--secondary': '#625b70',
        '--secondary-container': '#e8def8',
        '--on-secondary-container': '#1e192b',
        '--tertiary': '#7e5260',
        '--tertiary-container': '#ffd9e3',
        '--on-tertiary-container': '#31101d',
      },
    },
    sunset: {
      dark: {
        '--primary': '#ffb59c',
        '--on-primary': '#5c1a07',
        '--primary-container': '#7c2d12',
        '--on-primary-container': '#ffdbcf',
        '--secondary': '#e7bdb0',
        '--secondary-container': '#5d4037',
        '--on-secondary-container': '#ffdbcf',
        '--tertiary': '#d7c58d',
        '--tertiary-container': '#514619',
        '--on-tertiary-container': '#f4e1a7',
      },
      light: {
        '--primary': '#9b4425',
        '--primary-container': '#ffdbcf',
        '--on-primary-container': '#390c00',
        '--secondary': '#77574d',
        '--secondary-container': '#ffdbcf',
        '--on-secondary-container': '#2c160f',
        '--tertiary': '#6b5d2f',
        '--tertiary-container': '#f4e1a7',
        '--on-tertiary-container': '#211b00',
      },
    },
  };

  const paletteSurfaces = {
    xylune: { dark: {}, light: {} },
    graphite: {
      dark: {
        '--background': '#111318', '--surface': '#111318', '--surface-low': '#191b20',
        '--surface-container': '#1d2025', '--on-surface': '#e2e2e9',
        '--on-surface-variant': '#c4c6d0', '--outline': '#8e9099',
        '--outline-variant': '#44474e', '--rail': '#0c0e13',
      },
      light: {
        '--background': '#f9f9ff', '--surface': '#f9f9ff', '--surface-low': '#f1f3fa',
        '--surface-container': '#ebedf4', '--on-surface': '#1a1b20',
        '--on-surface-variant': '#44474e', '--outline': '#74777f',
        '--outline-variant': '#c4c6d0', '--rail': '#ffffff',
      },
    },
    ocean: {
      dark: {
        '--background': '#0e1416', '--surface': '#0e1416', '--surface-low': '#161c1e',
        '--surface-container': '#1a2022', '--on-surface': '#dce4e6',
        '--on-surface-variant': '#bec8cb', '--outline': '#899295',
        '--outline-variant': '#3f484b', '--rail': '#091012',
      },
      light: {
        '--background': '#f4fafc', '--surface': '#f4fafc', '--surface-low': '#edf4f6',
        '--surface-container': '#e7eef0', '--on-surface': '#161d1f',
        '--on-surface-variant': '#3f484b', '--outline': '#6f797c',
        '--outline-variant': '#bec8cb', '--rail': '#ffffff',
      },
    },
    violet: {
      dark: {
        '--background': '#151218', '--surface': '#151218', '--surface-low': '#1d1a20',
        '--surface-container': '#211e24', '--on-surface': '#e7e0e8',
        '--on-surface-variant': '#cbc3cc', '--outline': '#958e96',
        '--outline-variant': '#49454d', '--rail': '#100d13',
      },
      light: {
        '--background': '#fcf8ff', '--surface': '#fcf8ff', '--surface-low': '#f5f0f7',
        '--surface-container': '#efeaf1', '--on-surface': '#1d1a20',
        '--on-surface-variant': '#49454d', '--outline': '#7a757d',
        '--outline-variant': '#cbc3cc', '--rail': '#ffffff',
      },
    },
    sunset: {
      dark: {
        '--background': '#181210', '--surface': '#181210', '--surface-low': '#211a18',
        '--surface-container': '#251e1c', '--on-surface': '#f1dfda',
        '--on-surface-variant': '#d5c2bc', '--outline': '#9e8c87',
        '--outline-variant': '#51443f', '--rail': '#120c0a',
      },
      light: {
        '--background': '#fff8f6', '--surface': '#fff8f6', '--surface-low': '#f9f1ee',
        '--surface-container': '#f3ebe8', '--on-surface': '#211a18',
        '--on-surface-variant': '#51443f', '--outline': '#83746f',
        '--outline-variant': '#d5c2bc', '--rail': '#ffffff',
      },
    },
  };

  const fixedColors = (scheme, dark) => {
    const mode = dark ? 'dark' : 'light';
    const colors = {
      ...basePalettes[mode],
      ...(paletteSurfaces[scheme]?.[mode] || paletteSurfaces.xylune[mode]),
      ...(paletteAccents[scheme]?.[mode] || paletteAccents.xylune[mode]),
    };
    colors['--focus'] = colors['--primary'];
    return colors;
  };

  const params = new URLSearchParams(location.search);
  const isHex = (value) => /^[0-9a-f]{6}$/i.test(value || '');
  const appColors = {};
  Object.entries(names).forEach(([parameter, variable]) => {
    const value = params.get(parameter);
    if (isHex(value)) appColors[variable] = `#${value.toLowerCase()}`;
  });

  const required = ['--primary', '--background', '--on-surface'];
  const appTheme = required.every((name) => appColors[name]) ? {
    colors: appColors,
    dark: params.get('dark') === '1',
    dynamicLogo: params.get('dynamicLogo') === '1',
  } : null;

  const supportedThemes = ['app', 'dark', 'light', 'system'];
  const supportedSchemes = ['app', 'xylune', 'graphite', 'ocean', 'violet', 'sunset'];
  const storedTheme = localStorage.getItem('xylune-theme');
  const storedScheme = localStorage.getItem('xylune-scheme');
  const urlTheme = params.get('theme');
  const urlScheme = params.get('scheme');

  let themePreference = supportedThemes.includes(urlTheme)
    ? urlTheme
    : supportedThemes.includes(storedTheme) ? storedTheme : 'dark';
  let schemePreference = supportedSchemes.includes(urlScheme)
    ? urlScheme
    : urlTheme === 'app' && appTheme
      ? 'app'
      : supportedSchemes.includes(storedScheme) ? storedScheme : 'xylune';

  if (!appTheme && themePreference === 'app') {
    themePreference = supportedThemes.includes(storedTheme) && storedTheme !== 'app'
      ? storedTheme
      : 'dark';
  }
  if (!appTheme && schemePreference === 'app') {
    schemePreference = supportedSchemes.includes(storedScheme) && storedScheme !== 'app'
      ? storedScheme
      : 'xylune';
  }
  if (schemePreference === 'app') themePreference = 'app';

  const resolvedTheme = themePreference === 'app'
    ? (appTheme.dark ? 'dark' : 'light')
    : themePreference === 'system'
      ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : themePreference;

  const resolvedColors = schemePreference === 'app' && appTheme
    ? {
        ...fixedColors('xylune', appTheme.dark),
        ...appTheme.colors,
        '--focus': appTheme.colors['--primary'],
      }
    : fixedColors(schemePreference, resolvedTheme === 'dark');

  Object.entries(resolvedColors).forEach(([name, value]) => {
    document.documentElement.style.setProperty(name, value);
  });
  document.documentElement.dataset.theme = resolvedTheme;
  document.documentElement.dataset.themePreference = themePreference;
  document.documentElement.dataset.schemePreference = schemePreference;
  document.documentElement.style.colorScheme = resolvedTheme;

  const colorVariables = [...new Set([
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
  };
})();
