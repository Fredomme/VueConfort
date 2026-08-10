import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://vueconfort.fr',
  output: 'static',
  trailingSlash: 'always',
  integrations: [sitemap({
    filter: (page) => ![
      'https://vueconfort.fr/',
      'https://vueconfort.fr/confidentialite/'
    ].includes(page)
  })],
  build: { format: 'directory', inlineStylesheets: 'auto' },
  compressHTML: true
});
