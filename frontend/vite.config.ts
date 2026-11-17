import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Forward API calls to Spring Boot so the browser sees one origin in dev.
    // Origin is stripped so the backend treats proxied calls as same-origin
    // (works whether you open localhost, 127.0.0.1, or a VS Code/forwarded URL).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        configure: (proxy) => {
          proxy.on('proxyReq', (req) => req.removeHeader('origin'));
        },
      },
    },
  },
});
