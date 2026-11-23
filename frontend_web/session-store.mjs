const SENSITIVE_KEY_PATTERN = /(gpago|jwt|token|auth|session|sesion)/i;

/**
 * Purga cualquier residuo de credenciales o tokens en localStorage/sessionStorage.
 * La aplicación Web mantiene el JWT únicamente en memoria volátil.
 */
export function purgeSensitiveBrowserStorage(win = globalThis) {
  for (const storageName of ['localStorage', 'sessionStorage']) {
    let storage;
    try {
      storage = win?.[storageName];
    } catch {
      storage = null;
    }
    if (!storage || typeof storage.length !== 'number') continue;
    const keysToDelete = [];
    for (let i = 0; i < storage.length; i++) {
      const k = storage.key(i);
      if (k && SENSITIVE_KEY_PATTERN.test(k)) {
        keysToDelete.push(k);
      }
    }
    for (const k of keysToDelete) {
      try {
        storage.removeItem(k);
      } catch {
        // Ignora errores de cuota o entorno bloqueado sin persistir nada.
      }
    }
  }
}

/**
 * Estrategia de sesión segura en Web/PWA:
 * - Jamás escribe el JWT ni identificadores de sesión en localStorage, IndexedDB o CacheStorage.
 * - Conserva el token únicamente en memoria privada de la pestaña activa y purga almacenamiento residual.
 */
export class WebSecureSessionStore {
  #session = null;
  #win;

  constructor(win = globalThis) {
    this.#win = win;
    purgeSensitiveBrowserStorage(this.#win);
  }

  get persistenceStrategy() {
    return 'VOLATILE_MEMORY_NO_LOCALSTORAGE';
  }

  read() {
    return this.#session ? { ...this.#session } : null;
  }

  save(session) {
    if (!session || !session.accessToken) {
      this.clear();
      return;
    }
    purgeSensitiveBrowserStorage(this.#win);
    this.#session = {
      accessToken: String(session.accessToken),
      tokenType: String(session.tokenType || 'Bearer'),
      expiresIn: Number(session.expiresIn || 0),
      expiresAt: String(session.expiresAt || ''),
      sesionId: String(session.sesionId || ''),
      plataforma: String(session.plataforma || 'WEB'),
    };
  }

  clear() {
    this.#session = null;
    purgeSensitiveBrowserStorage(this.#win);
  }
}
