export const CACHE_NAME = 'gpago-shell-v1';

export const SHELL_ASSETS = [
  '/',
  '/index.html',
  '/offline.html',
  '/styles.css',
  '/app.mjs',
  '/api-client.mjs',
  '/session-store.mjs',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

const SHELL_PATH_SET = new Set(SHELL_ASSETS);

const SENSITIVE_PREFIXES = [
  '/auth/',
  '/usuarios/',
  '/clientes/',
  '/cuentas/',
  '/api/',
  '/actuator/',
  '/v3/',
  '/swagger-ui',
];

function hasAuthorizationHeader(headers) {
  if (!headers) return false;
  if (typeof headers.has === 'function') {
    return headers.has('Authorization') || headers.has('authorization');
  }
  return Object.keys(headers).some((k) => k.toLowerCase() === 'authorization');
}

/**
 * Regla estricta de caché PWA:
 * - Solo permite métodos GET sin cabecera Authorization.
 * - Solo cachea rutas versionadas exactas del shell público dentro del mismo origen.
 * - Nunca cachea auth, datos personales, saldos ni pagos.
 */
export function isCacheableShellRequest(urlStr, method = 'GET', headers = null, baseOrigin = 'https://gpago.local') {
  if (String(method || 'GET').toUpperCase() !== 'GET') {
    return false;
  }
  if (hasAuthorizationHeader(headers)) {
    return false;
  }
  let parsed;
  try {
    parsed = new URL(urlStr, baseOrigin);
  } catch {
    return false;
  }
  if (parsed.origin !== baseOrigin) {
    return false;
  }
  if (parsed.search && parsed.pathname !== '/') {
    return false;
  }
  if (SENSITIVE_PREFIXES.some((prefix) => parsed.pathname.startsWith(prefix))) {
    return false;
  }
  return SHELL_PATH_SET.has(parsed.pathname);
}

if (typeof self !== 'undefined' && typeof self.addEventListener === 'function' && typeof self.caches !== 'undefined') {
  self.addEventListener('install', (event) => {
    event.waitUntil(
      self.caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS)).then(() => self.skipWaiting()),
    );
  });

  self.addEventListener('activate', (event) => {
    event.waitUntil(
      self.caches.keys()
        .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => self.caches.delete(k))))
        .then(() => self.clients.claim()),
    );
  });

  self.addEventListener('fetch', (event) => {
    const req = event.request;
    const baseOrigin = self.location?.origin || 'https://gpago.local';
    if (!isCacheableShellRequest(req.url, req.method, req.headers, baseOrigin)) {
      return;
    }

    event.respondWith(
      fetch(req)
        .then((networkRes) => {
          if (networkRes && networkRes.ok && networkRes.type === 'basic') {
            const copy = networkRes.clone();
            event.waitUntil(
              self.caches.open(CACHE_NAME).then((cache) => cache.put(req, copy)),
            );
          }
          return networkRes;
        })
        .catch(async () => {
          const cached = await self.caches.match(req);
          if (cached) return cached;
          if (req.mode === 'navigate') {
            const offline = await self.caches.match('/offline.html');
            if (offline) return offline;
          }
          return new Response('Sin conexión con GPago', {
            status: 503,
            headers: { 'Content-Type': 'text/plain; charset=utf-8' },
          });
        }),
    );
  });
}
