import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://vueconfort.fr',
  output: 'static',
  trailingSlash: 'always',
  integrations: [sitemap({
    i18n: {
      defaultLocale: 'fr',
      locales: { fr: 'fr-FR', en: 'en-US' }
    }
  })],
  build: { format: 'directory', inlineStylesheets: 'auto' },
  compressHTML: true
});
