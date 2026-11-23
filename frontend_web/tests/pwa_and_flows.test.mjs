import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  DEFAULT_PUBLIC_API_BASE_URL,
  GpagoLogRedactor,
  GpagoWebApiClient,
  GpagoWebApiException,
  validateSecureBaseUri,
} from '../api-client.mjs';
import { GpagoWebController } from '../app.mjs';
import { WebSecureSessionStore } from '../session-store.mjs';
import { CACHE_NAME, SHELL_ASSETS, isCacheableShellRequest } from '../sw.js';
import { server } from '../server.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, '..');

function jsonRes(status, datos, codigo = 'EXITO') {
  return {
    status,
    async text() {
      return JSON.stringify({
        timestamp: '2026-11-21T16:30:00',
        status,
        codigo,
        mensaje: status >= 400 ? codigo : 'OK',
        path: '/api',
        datos,
        errores: [],
      });
    },
  };
}

function createFakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial));
  return {
    get length() {
      return map.size;
    },
    key(index) {
      return Array.from(map.keys())[index] ?? null;
    },
    getItem(k) {
      return map.has(k) ? map.get(k) : null;
    },
    setItem(k, v) {
      map.set(k, String(v));
    },
    removeItem(k) {
      map.delete(k);
    },
    dump() {
      return Object.fromEntries(map.entries());
    },
  };
}

test('PWA: manifest.json, iconos PNG 192/512 reales y tokens/accesibilidad HTML/CSS', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(webRoot, 'manifest.json'), 'utf8'));
  assert.equal(manifest.display, 'standalone');
  assert.equal(manifest.start_url, '/');
  assert.equal(manifest.scope, '/');
  assert.equal(manifest.theme_color, '#17449E');
  assert.equal(manifest.background_color, '#F4F7FB');

  for (const [file, expectedSize] of [
    ['icons/icon-192.png', 192],
    ['icons/icon-512.png', 512],
  ]) {
    const buf = fs.readFileSync(path.join(webRoot, file));
    assert.deepEqual(Array.from(buf.subarray(0, 8)), [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    assert.equal(buf.toString('ascii', 12, 16), 'IHDR');
    assert.equal(buf.readUInt32BE(16), expectedSize);
    assert.equal(buf.readUInt32BE(20), expectedSize);
  }

  const indexHtml = fs.readFileSync(path.join(webRoot, 'index.html'), 'utf8');
  assert.match(indexHtml, /<link rel="manifest" href="\/manifest\.json"/);
  assert.match(indexHtml, /<header class="app-header">/);
  assert.match(indexHtml, /<nav class="main-nav[^>]*aria-label="Navegación principal"/);
  assert.match(indexHtml, /<main id="main-content"/);
  assert.match(indexHtml, /<footer class="app-footer">/);
  assert.match(indexHtml, /Saltar al contenido principal/);

  const css = fs.readFileSync(path.join(webRoot, 'styles.css'), 'utf8');
  for (const token of [
    '#F4F7FB',
    '#FFFFFF',
    '#17449E',
    '#103378',
    '#14233B',
    '#45566E',
    '#62738C',
    '#166534',
    '#DCFCE7',
    '#991B1B',
    '#FEE2E2',
    '#854D0E',
    '#FEF3C7',
    '#5B21B6',
    '#EDE9FE',
    '--touch-min: 48px',
    ':focus-visible',
    '@media (min-width: 600px)',
    '@media (min-width: 1024px)',
    'max-width: 1200px',
    'max-width: 480px',
  ]) {
    assert.ok(css.includes(token), `Falta token o regla CSS esperada: ${token}`);
  }
});

test('PWA Service Worker: cachea únicamente shell público versionado y nunca auth/datos/pagos', () => {
  assert.equal(CACHE_NAME, 'gpago-shell-v1');
  for (const asset of SHELL_ASSETS) {
    assert.equal(isCacheableShellRequest(`https://gpago.local${asset}`, 'GET', null, 'https://gpago.local'), true);
  }

  const forbidden = [
    ['GET', 'https://gpago.local/auth/me', null],
    ['POST', 'https://gpago.local/auth/login', null],
    ['GET', 'https://gpago.local/clientes/me', null],
    ['GET', 'https://gpago.local/cuentas/me', null],
    ['GET', 'https://gpago.local/api/gestopago/productos', { Authorization: 'Bearer jwt-x' }],
    ['POST', 'https://gpago.local/api/gestopago/pagos', { Authorization: 'Bearer jwt-x' }],
    ['GET', 'https://gpago.local/api/gestopago/pagos/historial?page=0&size=10', { Authorization: 'Bearer jwt-x' }],
    ['GET', 'https://gpago.local/index.html', { Authorization: 'Bearer no-cache' }],
    ['GET', 'https://otro-origen.example/styles.css', null],
  ];
  for (const [method, url, headers] of forbidden) {
    assert.equal(
      isCacheableShellRequest(url, method, headers, 'https://gpago.local'),
      false,
      `Nunca debe cachear ${method} ${url}`,
    );
  }
});

test('Seguridad Web: sin secretos en bundle, jamás persiste JWT en localStorage y exige HTTPS', () => {
  const localStorage = createFakeStorage({ gpago_legacy_token: 'viejo', tema_ui: 'claro' });
  const sessionStorage = createFakeStorage({ jwt_debug: 'secreto' });
  const fakeWin = { localStorage, sessionStorage };

  const store = new WebSecureSessionStore(fakeWin);
  assert.equal(store.persistenceStrategy, 'VOLATILE_MEMORY_NO_LOCALSTORAGE');
  assert.equal(localStorage.getItem('gpago_legacy_token'), null);
  assert.equal(localStorage.getItem('tema_ui'), 'claro');
  assert.equal(sessionStorage.getItem('jwt_debug'), null);

  store.save({
    accessToken: 'jwt-en-memoria-solamente',
    tokenType: 'Bearer',
    expiresIn: 300,
    expiresAt: '2026-11-21T16:35:00Z',
    sesionId: 'web-1',
    plataforma: 'WEB',
  });
  assert.equal(store.read().accessToken, 'jwt-en-memoria-solamente');
  assert.equal(JSON.stringify(localStorage.dump()).includes('jwt-en-memoria-solamente'), false);
  assert.equal(JSON.stringify(sessionStorage.dump()).includes('jwt-en-memoria-solamente'), false);

  assert.equal(validateSecureBaseUri(DEFAULT_PUBLIC_API_BASE_URL), DEFAULT_PUBLIC_API_BASE_URL);
  assert.equal(validateSecureBaseUri('http://localhost:8080/'), 'http://localhost:8080');
  assert.throws(() => validateSecureBaseUri('http://api.gpago.com'), TypeError);
  assert.throws(() => validateSecureBaseUri('https://user:pass@api.gpago.com'), TypeError);

  const raw = 'Bearer eyJhbGciOi.eyJzdWIi.sig password=Secreto1! telefono=5512345678';
  const sanitized = GpagoLogRedactor.redact(raw);
  assert.equal(sanitized.includes('Secreto1!'), false);
  assert.equal(sanitized.includes('5512345678'), false);
  const ex = new GpagoWebApiException({
    statusCode: 401,
    codigo: 'NO_AUTENTICADO',
    mensaje: 'Fallo con Bearer eyJhbGciOi.eyJzdWIi.sig',
  });
  assert.equal(ex.toString().includes('eyJhbGciOi'), false);

  for (const name of ['api-client.mjs', 'app.mjs', 'session-store.mjs', 'sw.js', 'index.html']) {
    const content = fs.readFileSync(path.join(webRoot, name), 'utf8');
    assert.equal(content.includes('GESTOPAGO_PASSWORD'), false);
    assert.equal(content.includes('portalventas'), false);
  }
});

test('Flujo cruzado Android <-> Web, M2 catálogo, M3 idempotencia/offline, M4 historial CAPTURED y M5 expiración', async () => {
  let nowMs = Date.UTC(2026, 10, 21, 16, 0, 0);
  const fetchCalls = [];
  const paymentKeys = [];
  let paymentAttempt = 0;
  let webSessionRevokedByAndroid = false;

  const mockFetch = async (url, opts) => {
    const u = new URL(url);
    const route = `${opts.method} ${u.pathname}${u.search}`;
    fetchCalls.push({ route, headers: opts.headers, body: opts.body ? JSON.parse(opts.body) : null });

    assert.equal(opts.headers['X-Client-Platform'], 'WEB');
    assert.equal(opts.headers['Cache-Control'], 'no-store');
    assert.equal(opts.credentials, 'omit');

    if (route === 'POST /auth/login') {
      assert.equal(JSON.parse(opts.body).plataforma, 'WEB');
      return jsonRes(200, {
        desafioId: 'des-web-1',
        correoEnmascarado: 'u***o@example.com',
        plataforma: 'WEB',
        requiereOtp: true,
        expiresIn: 180,
        expiresAt: new Date(nowMs + 180000).toISOString(),
        intentosRestantes: 3,
        reenviosRestantes: 3,
      });
    }
    if (route === 'POST /auth/otp/verificar') {
      return {
        status: 409,
        async text() {
          return JSON.stringify({
            timestamp: '2026-11-21T16:30:00',
            status: 409,
            codigo: 'CONFLICTO_SESION_ACTIVA',
            mensaje: 'Sesión activa en ANDROID',
            datos: {
              conflictoSesion: true,
              desafioId: 'des-web-1',
              tokenConfirmacion: 'tok-conf-web-1',
              plataformaActiva: 'ANDROID',
              plataformaNueva: 'WEB',
              sesionActivaDesde: new Date(nowMs - 60000).toISOString(),
              expiraConfirmacionEn: new Date(nowMs + 120000).toISOString(),
            },
            errores: [],
          });
        },
      };
    }
    if (route === 'POST /auth/sesion/reemplazar') {
      assert.equal(JSON.parse(opts.body).confirmarCierre, true);
      return jsonRes(200, {
        accessToken: 'jwt-web-unico',
        tokenType: 'Bearer',
        expiresIn: 300,
        expiresAt: new Date(nowMs + 300000).toISOString(),
        sesionId: 'ses-web-1',
        plataforma: 'WEB',
      });
    }
    if (webSessionRevokedByAndroid && opts.headers.Authorization === 'Bearer jwt-web-unico') {
      return jsonRes(401, null, 'NO_AUTENTICADO');
    }
    if (route === 'GET /auth/me') {
      return jsonRes(200, { usuarioId: 7, clienteId: 12, correo: 'usuario@example.com', activo: true, rol: 'CLIENTE' });
    }
    if (route === 'GET /clientes/me') {
      return jsonRes(200, { cliente: { clienteId: 12, nombre: 'Ana', apellidoPaterno: 'Pérez', estatus: 'ACTIVO' } });
    }
    if (route === 'GET /cuentas/me') {
      return jsonRes(200, [{ cuentaId: 45, numeroCuenta: '12345678901234567890', clienteId: 12, saldo: 500, estatus: 'ACTIVA' }]);
    }
    if (route === 'GET /api/gestopago/productos') {
      return jsonRes(200, [
        { id: 1, idProducto: 219, idServicio: 78, servicio: 'Oui', producto: 'Recarga 40', idCatTipoServicio: 13, precio: 40, activo: true },
        { id: 2, idProducto: 300, idServicio: 56, servicio: 'Agua', producto: 'Inactivo', idCatTipoServicio: 15, precio: 100, activo: false },
      ]);
    }
    if (route === 'GET /api/gestopago/cuentas/45/saldo') {
      return jsonRes(200, { cuentaId: 45, contabilizado: 500, reservado: 0, disponible: 500 });
    }
    if (route === 'POST /api/gestopago/pagos/validar') {
      return jsonRes(200, { contratoLocalValido: true, idServicio: 78, idProducto: 219, montoPago: 40, pagoEnviado: false });
    }
    if (route === 'POST /api/gestopago/pagos') {
      paymentKeys.push(opts.headers['Idempotency-Key']);
      paymentAttempt += 1;
      if (paymentAttempt === 1) {
        throw new Error('ECONNRESET');
      }
      return jsonRes(202, { id: 'pago-uuid-web-1', cuentaId: 45, monto: 40, estado: 'RESERVED', revisionManual: false });
    }
    if (route === 'GET /api/gestopago/pagos/pago-uuid-web-1') {
      return jsonRes(200, { id: 'pago-uuid-web-1', cuentaId: 45, monto: 40, estado: 'CAPTURED', revisionManual: false });
    }
    if (route === 'GET /api/gestopago/pagos/historial?page=0&size=10') {
      return jsonRes(200, {
        contenido: [
          {
            id: 'pago-uuid-web-1',
            cuentaId: 45,
            idServicio: 78,
            servicio: 'Oui',
            idProducto: 219,
            producto: 'Recarga 40',
            monto: 40,
            estado: 'CAPTURED',
            referenciaInternaUpc: '100000000000001',
            folioProveedorIdTx: null,
            capturadoEn: '2026-11-21T16:31:00',
          },
          {
            id: 'pago-failed-ignorado',
            cuentaId: 45,
            idServicio: 78,
            idProducto: 219,
            monto: 40,
            estado: 'FAILED',
            referenciaInternaUpc: '999999999999999',
            folioProveedorIdTx: null,
          },
        ],
        page: 0,
        size: 10,
        totalElementos: 1,
        totalPaginas: 1,
      });
    }
    throw new Error(`Ruta inesperada: ${route}`);
  };

  const storage = createFakeStorage();
  const controller = new GpagoWebController({
    apiClient: new GpagoWebApiClient({ baseUrl: 'https://api.gpago.local', fetchImpl: mockFetch }),
    sessionStore: new WebSecureSessionStore({ localStorage: storage, sessionStorage: createFakeStorage() }),
    clock: () => nowMs,
  });

  // 1. Login -> OTP -> Conflicto ANDROID <-> WEB -> Reemplazo explícito
  await controller.iniciarLogin({ correo: 'usuario@example.com', password: 'Password123!' });
  assert.equal(controller.stage, 'otp');
  await controller.verificarOtp('123456');
  assert.equal(controller.stage, 'conflict');
  assert.equal(controller.conflicto.plataformaActiva, 'ANDROID');

  await controller.confirmarReemplazoSesion();
  assert.equal(controller.stage, 'home');
  assert.equal(controller.sessionStore.read().accessToken, 'jwt-web-unico');
  assert.equal(storage.length, 0);

  // 2. Catálogo M2 real sin llamadas de sincronización
  await controller.seleccionarTab('catalog');
  assert.equal(controller.catalog.productos.length, 2);
  assert.equal(fetchCalls.some((c) => c.route.includes('sincronizar')), false);

  // 3. Compra M3 + offline guard + incertidumbre con misma Idempotency-Key -> RESERVED -> CAPTURED
  await controller.iniciarCompra(controller.catalog.productos[0]);
  await controller.prevalidarYRevisar({ montoRaw: '40.00', destinoModo: 'telefono', destinoRaw: '5512345678' });
  assert.equal(controller.purchase.stage, 'review');

  controller.setOnline(false);
  await controller.confirmarYEnviarPago();
  assert.equal(paymentAttempt, 0);
  assert.equal(controller.feedback.tone, 'network');

  controller.setOnline(true);
  await controller.confirmarYEnviarPago();
  assert.equal(controller.purchase.outcomeKind, 'uncertain');
  await controller.consultarEstadoORecuperarCompra();
  assert.equal(paymentKeys.length, 2);
  assert.equal(paymentKeys[1], paymentKeys[0]);
  assert.equal(controller.purchase.outcomeKind, 'pending');

  await controller.consultarEstadoORecuperarCompra();
  assert.equal(controller.purchase.outcomeKind, 'captured');

  // 4. Historial M4: filtra FAILED defensivamente y no fabrica folio cuando id_tx es null
  await controller.seleccionarTab('history');
  assert.equal(controller.history.items.length, 1);
  assert.equal(controller.history.items[0].id, 'pago-uuid-web-1');
  assert.equal(controller.history.items[0].folioExternoDisponible, null);

  // 5. Revocación cruzada desde Android (401) limpia sesión, almacenamiento y rutas
  webSessionRevokedByAndroid = true;
  await controller.refrescarPerfilYSesion();
  assert.equal(controller.stage, 'login');
  assert.equal(controller.sessionExpiredNotice, true);
  assert.equal(controller.sessionStore.read(), null);
  assert.equal(controller.catalog.productos.length, 0);
  assert.equal(controller.history.items.length, 0);

  // Re-validar expiración por tiempo
  webSessionRevokedByAndroid = false;
  nowMs += 400000;
  assert.equal(await controller.verificarVigenciaLocal(), false);
  assert.equal(controller.stage, 'login');
});

test('Servidor Web PWA sirve manifest, sw.js con Service-Worker-Allowed, offline.html e iconos bloqueando rutas de prueba', async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const { port } = server.address();
    const base = `http://127.0.0.1:${port}`;

    const rootRes = await fetch(`${base}/`);
    assert.equal(rootRes.status, 200);
    assert.match(rootRes.headers.get('content-type') || '', /text\/html/);
    assert.equal(rootRes.headers.get('x-content-type-options'), 'nosniff');
    assert.equal(rootRes.headers.get('x-frame-options'), 'DENY');

    const manifestRes = await fetch(`${base}/manifest.json`);
    assert.equal(manifestRes.status, 200);
    assert.match(manifestRes.headers.get('content-type') || '', /application\/manifest\+json/);

    const swRes = await fetch(`${base}/sw.js`);
    assert.equal(swRes.status, 200);
    assert.equal(swRes.headers.get('service-worker-allowed'), '/');

    const offlineRes = await fetch(`${base}/offline.html`);
    assert.equal(offlineRes.status, 200);

    const iconRes = await fetch(`${base}/icons/icon-512.png`);
    assert.equal(iconRes.status, 200);
    assert.equal(iconRes.headers.get('content-type'), 'image/png');

    const testFileRes = await fetch(`${base}/tests/pwa_and_flows.test.mjs`);
    assert.equal(testFileRes.status, 404);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
