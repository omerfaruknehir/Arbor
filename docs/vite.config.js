import { defineConfig } from 'vite';

export default defineConfig({
  root: new URL('.', import.meta.url).pathname,
  server: {
    host: '0.0.0.0',
    allowedHosts: ['terminal.local'],
  },
});
