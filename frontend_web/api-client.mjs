export const DEFAULT_PUBLIC_API_BASE_URL = 'https://ambientar-proyecto.onrender.com';

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);
const UNSUPPORTED_SYNC_ROUTES = new Set([
  '/api/gestopago/productos/sincronizar',
  '/api/gestopago/productos/forzar-sincronizacion',
  '/api/gestopago/productos/consultar-externo',
]);

/**
 * Redactor defensivo para trazas y mensajes de error del cliente Web.
 */
export const GpagoLogRedactor = {
  redact(input) {
    if (input === null || input === undefined) return '';
    let sanitized = String(input);
    if (!sanitized) return sanitized;
    sanitized = sanitized.replace(/Bearer\s+[A-Za-z0-9._~+/-]+=*/gi, 'Bearer [REDACTED]');
    sanitized = sanitized.replace(/eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}/g, '[JWT_REDACTED]');
    sanitized = sanitized.replace(
      /"(password|accessToken|codigoOtp|tokenConfirmacion|telefono|referencia|numeroCuenta)"\s*:\s*"[^"]*"/gi,
      (_, field) => `"${field}":"[REDACTED]"`,
    );
    sanitized = sanitized.replace(
      /\b(password|accessToken|codigoOtp|tokenConfirmacion|telefono|referencia|numeroCuenta)\s*[=:]\s*([^\s,;]+)/gi,
      (_, field) => `${field}=[REDACTED]`,
    );
    sanitized = sanitized.replace(/\b\d{10,20}\b/g, '[DIGITS_REDACTED]');
    return sanitized;
  },
};

export class GpagoWebApiException extends Error {
  constructor({
    statusCode = 0,
    codigo = 'ERROR_HTTP',
    mensaje = 'No fue posible completar la solicitud.',
    errores = [],
    conflictoSesion = null,
    isNetworkError = false,
  }) {
    super(GpagoLogRedactor.redact(mensaje));
    this.name = 'GpagoWebApiException';
    this.statusCode = statusCode;
    this.codigo = codigo;
    this.mensaje = mensaje;
    this.errores = Array.isArray(errores) ? errores : [];
    this.conflictoSesion = conflictoSesion;
    this.isNetworkError = Boolean(isNetworkError);
  }

  get esNoAutenticado() {
    return this.statusCode === 401 && !this.isNetworkError;
  }

  get esConflictoSesion() {
    return this.statusCode === 409 && this.codigo === 'CONFLICTO_SESION_ACTIVA';
  }

  get esOtpExpirado() {
    return this.codigo === 'OTP_EXPIRADO';
  }

  get esOtpBloqueado() {
    return this.codigo === 'OTP_BLOQUEADO';
  }

  toString() {
    return `GpagoWebApiException(${this.statusCode}, ${GpagoLogRedactor.redact(this.codigo)}, ${GpagoLogRedactor.redact(this.mensaje)})`;
  }
}

/**
 * Exige HTTPS fuera de loopback local de desarrollo; rechaza credenciales incrustadas.
 */
export function validateSecureBaseUri(rawBaseUrl, { allowLoopbackHttp = true } = {}) {
  const normalized = String(rawBaseUrl || '').trim().replace(/\/+$/, '');
  if (!normalized) {
    throw new TypeError('La URL del servidor GPago es obligatoria.');
  }
  let parsed;
  try {
    parsed = new URL(normalized);
  } catch {
    throw new TypeError('La URL del servidor GPago no tiene un formato válido.');
  }
  if (!parsed.hostname || parsed.username || parsed.password) {
    throw new TypeError('La URL base debe incluir host válido sin credenciales incrustadas.');
  }
  const protocol = parsed.protocol.toLowerCase();
  const isLoopback = LOOPBACK_HOSTS.has(parsed.hostname.toLowerCase());
  if (protocol === 'https:') {
    return parsed.toString().replace(/\/+$/, '');
  }
  if (protocol === 'http:' && allowLoopbackHttp && isLoopback) {
    return parsed.toString().replace(/\/+$/, '');
  }
  throw new TypeError('Por seguridad financiera solo se permite transporte HTTPS fuera de loopback local.');
}

export function createUuidV4(cryptoObj = globalThis.crypto) {
  if (cryptoObj && typeof cryptoObj.randomUUID === 'function') {
    return cryptoObj.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (cryptoObj && typeof cryptoObj.getRandomValues === 'function') {
    cryptoObj.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'));
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`;
}

export function formatMxn(monto) {
  const value = Number(monto || 0);
  const negative = value < 0;
  const [intPart, decPart] = Math.abs(value).toFixed(2).split('.');
  const formatted = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negative ? '-' : ''}$${formatted}.${decPart} MXN`;
}

export function formatServerTimestamp(raw) {
  if (!raw) return null;
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) return null;
  return new Intl.DateTimeFormat('es-MX', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(parsed);
}

export function maskAccount(numero) {
  const clean = String(numero || '').trim();
  if (clean.length <= 4) return clean;
  return `**** ${clean.slice(-4)}`;
}

export function agruparCatalogoPorCategoria(productos) {
  const groups = new Map();
  for (const item of productos || []) {
    const key = item.idCatTipoServicio ?? 'SIN_CATEGORIA';
    if (!groups.has(key)) {
      groups.set(key, {
        idCatTipoServicio: item.idCatTipoServicio ?? null,
        etiqueta:
          item.idCatTipoServicio !== null && item.idCatTipoServicio !== undefined
            ? `Categoría #${item.idCatTipoServicio}`
            : 'Sin categoría',
        productos: [],
      });
    }
    groups.get(key).productos.push(item);
  }
  return Array.from(groups.values());
}

export class GpagoWebApiClient {
  constructor({
    baseUrl = DEFAULT_PUBLIC_API_BASE_URL,
    fetchImpl = globalThis.fetch?.bind(globalThis),
    plataforma = 'WEB',
    allowLoopbackHttp = true,
    timeoutMs = 15000,
  } = {}) {
    this.baseUrl = validateSecureBaseUri(baseUrl, { allowLoopbackHttp });
    this.fetchImpl = fetchImpl;
    this.plataforma = plataforma;
    this.timeoutMs = timeoutMs;
  }

  setBaseUrl(newBaseUrl, { allowLoopbackHttp = true } = {}) {
    this.baseUrl = validateSecureBaseUri(newBaseUrl, { allowLoopbackHttp });
  }

  async iniciarLogin({ correo, password }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: '/auth/login',
      body: {
        correo: String(correo || '').trim(),
        password: String(password || ''),
        plataforma: this.plataforma,
      },
    });
    return env.datos;
  }

  async verificarOtp({ desafioId, codigoOtp }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: '/auth/otp/verificar',
      body: {
        desafioId,
        codigoOtp: String(codigoOtp || '').trim(),
        plataforma: this.plataforma,
      },
    });
    return env.datos;
  }

  async reenviarOtp({ desafioId }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: '/auth/otp/reenviar',
      body: { desafioId },
    });
    return env.datos;
  }

  async reemplazarSesion({ desafioId, tokenConfirmacion, confirmarCierre = true }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: '/auth/sesion/reemplazar',
      body: {
        desafioId,
        tokenConfirmacion,
        confirmarCierre,
      },
    });
    return env.datos;
  }

  async logout({ accessToken }) {
    await this.#requestEnvelope({
      method: 'POST',
      path: '/auth/logout',
      accessToken,
    });
  }

  async consultarPerfilAuth({ accessToken }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: '/auth/me',
      accessToken,
    });
    return env.datos;
  }

  async consultarClientePropio({ accessToken }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: '/clientes/me',
      accessToken,
    });
    return env.datos?.cliente ?? env.datos;
  }

  async consultarCuentasPropias({ accessToken }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: '/cuentas/me',
      accessToken,
    });
    return Array.isArray(env.datos) ? env.datos : [];
  }

  async consultarCatalogoProductos({ accessToken }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: '/api/gestopago/productos',
      accessToken,
    });
    return Array.isArray(env.datos) ? env.datos : [];
  }

  async validarPagoLocal({ accessToken, pago }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: '/api/gestopago/pagos/validar',
      accessToken,
      body: pago,
    });
    return env.datos;
  }

  async crearPagoIdempotente({ accessToken, idempotencyKey, solicitud }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: '/api/gestopago/pagos',
      accessToken,
      extraHeaders: { 'Idempotency-Key': idempotencyKey },
      body: solicitud,
    });
    return {
      ...env.datos,
      serverTimestamp: env.timestamp || null,
      serverMessage: env.mensaje || null,
    };
  }

  async consultarPago({ accessToken, pagoId }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: `/api/gestopago/pagos/${encodeURIComponent(pagoId)}`,
      accessToken,
    });
    return {
      ...env.datos,
      serverTimestamp: env.timestamp || null,
      serverMessage: env.mensaje || null,
    };
  }

  async conciliarPago({ accessToken, pagoId }) {
    const env = await this.#requestEnvelope({
      method: 'POST',
      path: `/api/gestopago/pagos/${encodeURIComponent(pagoId)}/conciliar`,
      accessToken,
    });
    return {
      ...env.datos,
      serverTimestamp: env.timestamp || null,
      serverMessage: env.mensaje || null,
    };
  }

  async consultarSaldoFinanciero({ accessToken, cuentaId }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: `/api/gestopago/cuentas/${encodeURIComponent(cuentaId)}/saldo`,
      accessToken,
    });
    return env.datos;
  }

  async consultarHistorialPagos({ accessToken, page = 0, size = 10 }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: `/api/gestopago/pagos/historial?page=${Number(page)}&size=${Number(size)}`,
      accessToken,
    });
    const payload = env.datos || {};
    const contenidoRaw = Array.isArray(payload.contenido) ? payload.contenido : [];
    const capturadas = contenidoRaw
      .filter((item) => String(item?.estado || '').toUpperCase() === 'CAPTURED')
      .map((item) => ({
        ...item,
        folioExternoDisponible:
          typeof item.folioProveedorIdTx === 'string' && item.folioProveedorIdTx.trim().length > 0
            ? item.folioProveedorIdTx.trim()
            : null,
      }));
    return {
      contenido: capturadas,
      page: Number(payload.page ?? page),
      size: Number(payload.size ?? size),
      totalElementos: Number(payload.totalElementos ?? capturadas.length),
      totalPaginas: Number(payload.totalPaginas ?? 0),
    };
  }

  async consultarDetalleHistorialPago({ accessToken, pagoId }) {
    const env = await this.#requestEnvelope({
      method: 'GET',
      path: `/api/gestopago/pagos/historial/${encodeURIComponent(pagoId)}`,
      accessToken,
    });
    const item = env.datos;
    if (!item || String(item.estado || '').toUpperCase() !== 'CAPTURED') {
      throw new GpagoWebApiException({
        statusCode: 404,
        codigo: 'RECURSO_NO_ENCONTRADO',
        mensaje: 'El comprobante solicitado no corresponde a una operación capturada.',
      });
    }
    return {
      ...item,
      folioExternoDisponible:
        typeof item.folioProveedorIdTx === 'string' && item.folioProveedorIdTx.trim().length > 0
          ? item.folioProveedorIdTx.trim()
          : null,
    };
  }

  async #requestEnvelope({ method, path, body = null, accessToken = null, extraHeaders = {} }) {
    const pathOnly = path.split('?')[0];
    if (UNSUPPORTED_SYNC_ROUTES.has(pathOnly)) {
      throw new GpagoWebApiException({
        statusCode: 403,
        codigo: 'RUTA_OPERATIVA_BLOQUEADA',
        mensaje: 'Las rutas de sincronización sujetas a cuota están prohibidas en clientes.',
      });
    }
    if (typeof this.fetchImpl !== 'function') {
      throw new GpagoWebApiException({
        statusCode: 0,
        codigo: 'ERROR_COMUNICACION',
        mensaje: 'El entorno no dispone de cliente HTTP fetch.',
        isNetworkError: true,
      });
    }

    const headers = {
      Accept: 'application/json',
      'X-Client-Platform': this.plataforma,
      'X-Requested-With': 'GPago-Web-PWA',
      'Cache-Control': 'no-store',
      ...extraHeaders,
    };
    if (body !== null) {
      headers['Content-Type'] = 'application/json; charset=utf-8';
    }
    if (accessToken) {
      if (/[\r\n]/.test(accessToken)) {
        throw new GpagoWebApiException({
          statusCode: 401,
          codigo: 'NO_AUTENTICADO',
          mensaje: 'Token de sesión con formato inválido.',
        });
      }
      headers.Authorization = `Bearer ${accessToken}`;
    }

    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = controller ? setTimeout(() => controller.abort(), this.timeoutMs) : null;
    let response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: body !== null ? JSON.stringify(body) : undefined,
        cache: 'no-store',
        credentials: 'omit',
        signal: controller ? controller.signal : undefined,
      });
    } catch {
      throw new GpagoWebApiException({
        statusCode: 0,
        codigo: 'ERROR_COMUNICACION',
        mensaje: 'No se pudo conectar con el servidor GPago. Conserva tu referencia antes de reintentar.',
        isNetworkError: true,
      });
    } finally {
      if (timer) clearTimeout(timer);
    }

    if (response.status === 204) {
      return { statusCode: 204, datos: null, timestamp: null, mensaje: null };
    }

    const rawText = await response.text();
    let parsed;
    try {
      parsed = JSON.parse(rawText);
    } catch {
      throw new GpagoWebApiException({
        statusCode: response.status,
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: `El servidor devolvió una respuesta ilegible (HTTP ${response.status}).`,
        isNetworkError: response.status === 0 || response.status >= 500,
      });
    }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new GpagoWebApiException({
        statusCode: response.status,
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: `Estructura JSON inesperada (HTTP ${response.status}).`,
        isNetworkError: response.status === 0 || response.status >= 500,
      });
    }

    if (response.status >= 200 && response.status < 300) {
      return {
        statusCode: response.status,
        datos: Object.prototype.hasOwnProperty.call(parsed, 'datos') ? parsed.datos : parsed,
        timestamp: parsed.timestamp || null,
        mensaje: parsed.mensaje || null,
      };
    }

    const codigo = String(parsed.codigo || 'ERROR_HTTP');
    const mensaje = String(
      parsed.mensaje || `No fue posible completar la solicitud (HTTP ${response.status}).`,
    );
    const errores = Array.isArray(parsed.errores) ? parsed.errores : [];
    const conflictoSesion =
      response.status === 409 && codigo === 'CONFLICTO_SESION_ACTIVA' && parsed.datos && typeof parsed.datos === 'object'
        ? parsed.datos
        : null;

    throw new GpagoWebApiException({
      statusCode: response.status,
      codigo,
      mensaje,
      errores,
      conflictoSesion,
      isNetworkError: false,
    });
  }
}
