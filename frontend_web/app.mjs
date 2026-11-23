import {
  DEFAULT_PUBLIC_API_BASE_URL,
  GpagoWebApiClient,
  GpagoWebApiException,
  agruparCatalogoPorCategoria,
  createUuidV4,
  formatMxn,
  formatServerTimestamp,
  maskAccount,
} from './api-client.mjs';
import { WebSecureSessionStore } from './session-store.mjs';
import { CACHE_NAME } from './sw.js';

export class GpagoWebController {
  #listeners = new Set();
  #clock;
  #uuidGen;

  constructor({
    apiClient = null,
    sessionStore = null,
    clock = () => Date.now(),
    uuidGen = () => createUuidV4(),
    isOnline = true,
  } = {}) {
    this.api = apiClient || new GpagoWebApiClient({ baseUrl: DEFAULT_PUBLIC_API_BASE_URL });
    this.sessionStore = sessionStore || new WebSecureSessionStore();
    this.#clock = clock;
    this.#uuidGen = uuidGen;

    this.isOnline = isOnline;
    this.stage = 'login'; // 'login' | 'otp' | 'conflict' | 'home'
    this.activeTab = 'profile'; // 'profile' | 'catalog' | 'purchase' | 'history'
    this.isBusy = false;
    this.feedback = null; // { tone: 'success'|'rejected'|'pending'|'network', title, description }
    this.sessionExpiredNotice = false;

    this.desafio = null;
    this.conflicto = null;
    this.session = null;
    this.perfil = null;
    this.cliente = null;
    this.cuentas = [];

    // Catálogo M2
    this.catalog = {
      isLoading: false,
      hasLoaded: false,
      error: null,
      productos: [],
      searchQuery: '',
      selectedCategoryId: null,
      filterAll: true,
      selectedProduct: null,
    };

    // Compra M3
    this.purchase = {
      stage: 'capture', // 'capture' | 'review' | 'result'
      producto: null,
      selectedCuentaId: null,
      saldoFinanciero: null,
      isLoadingSaldo: false,
      isSubmitting: false,
      localValidationError: null,
      error: null,
      validacion: null,
      solicitudConfirmada: null,
      idempotencyKey: null,
      operacion: null,
      outcomeKind: null, // 'captured' | 'rejected' | 'pending' | 'uncertain'
      pollAttempts: 0,
      maxPolls: 5,
    };

    // Historial M4
    this.history = {
      isLoading: false,
      hasLoaded: false,
      error: null,
      items: [],
      page: 0,
      size: 10,
      totalElementos: 0,
      totalPaginas: 0,
      isLoadingDetail: false,
      detailError: null,
      selectedDetail: null,
    };

    this.modal = null; // { kind: 'catalog-detail'|'history-detail' }
  }

  subscribe(fn) {
    this.#listeners.add(fn);
    return () => this.#listeners.delete(fn);
  }

  #notify() {
    for (const fn of this.#listeners) {
      fn(this);
    }
  }

  setOnline(online) {
    const next = Boolean(online);
    if (this.isOnline !== next) {
      this.isOnline = next;
      this.#notify();
    }
  }

  descartarAvisoSesionExpirada() {
    if (!this.sessionExpiredNotice) return;
    this.sessionExpiredNotice = false;
    this.#notify();
  }

  #requireOnline(actionLabel = 'realizar esta operación') {
    if (this.isOnline) return true;
    this.feedback = {
      tone: 'network',
      title: 'Sin conexión a Internet',
      description: `No es posible ${actionLabel} sin conectividad HTTPS con el servidor GPago.`,
    };
    this.#notify();
    return false;
  }

  async verificarVigenciaLocal() {
    const s = this.session || this.sessionStore.read();
    if (s && s.accessToken) {
      const expMs = Date.parse(s.expiresAt);
      if (!Number.isNaN(expMs) && expMs > this.#clock()) {
        return true;
      }
    }
    this.#expulsarLocalmente({
      tone: 'pending',
      title: 'Sesión expirada',
      description: 'Tu sesión Web venció por tiempo de seguridad. Inicia sesión nuevamente con tu correo y OTP.',
    });
    return false;
  }

  #expulsarLocalmente(feedback) {
    this.sessionStore.clear();
    this.session = null;
    this.perfil = null;
    this.cliente = null;
    this.cuentas = [];
    this.desafio = null;
    this.conflicto = null;
    this.modal = null;
    this.#resetProtectedModules();
    this.sessionExpiredNotice = true;
    this.feedback = feedback;
    this.stage = 'login';
    this.activeTab = 'profile';
    this.#notify();
  }

  #resetProtectedModules() {
    this.catalog = {
      isLoading: false,
      hasLoaded: false,
      error: null,
      productos: [],
      searchQuery: '',
      selectedCategoryId: null,
      filterAll: true,
      selectedProduct: null,
    };
    this.purchase = {
      stage: 'capture',
      producto: null,
      selectedCuentaId: null,
      saldoFinanciero: null,
      isLoadingSaldo: false,
      isSubmitting: false,
      localValidationError: null,
      error: null,
      validacion: null,
      solicitudConfirmada: null,
      idempotencyKey: null,
      operacion: null,
      outcomeKind: null,
      pollAttempts: 0,
      maxPolls: 5,
    };
    this.history = {
      isLoading: false,
      hasLoaded: false,
      error: null,
      items: [],
      page: 0,
      size: 10,
      totalElementos: 0,
      totalPaginas: 0,
      isLoadingDetail: false,
      detailError: null,
      selectedDetail: null,
    };
  }

  async #handleApiError(err, fallbackTitle) {
    if (err instanceof GpagoWebApiException && (err.esNoAutenticado || err.statusCode === 403)) {
      this.#expulsarLocalmente({
        tone: 'pending',
        title: 'Sesión revocada o expirada',
        description:
          'Tu sesión dejó de ser válida (pudo cerrarse desde Android u otra pestaña, o expirar). Vuelve a iniciar sesión.',
      });
      return true;
    }
    this.feedback = this.#feedbackFromError(err, fallbackTitle);
    this.#notify();
    return false;
  }

  #feedbackFromError(err, fallbackTitle) {
    if (err?.isNetworkError) {
      return {
        tone: 'network',
        title: 'Sin conexión con GPago',
        description: err.mensaje || 'Error de comunicación con el servidor.',
      };
    }
    if (err?.esOtpExpirado) {
      return { tone: 'pending', title: 'Código OTP expirado', description: err.mensaje };
    }
    if (err?.statusCode === 429) {
      return { tone: 'pending', title: 'Límite de solicitudes alcanzado', description: err.mensaje };
    }
    const detail = err?.errores?.length
      ? err.errores.map((e) => e.mensaje).join(' ')
      : err?.mensaje || 'Error inesperado del servidor.';
    return { tone: 'rejected', title: fallbackTitle, description: detail };
  }

  async iniciarLogin({ correo, password, serverUrl = null }) {
    if (this.isBusy || !this.#requireOnline('iniciar sesión')) return;
    if (serverUrl && serverUrl.trim()) {
      try {
        this.api.setBaseUrl(serverUrl.trim());
      } catch (err) {
        this.feedback = { tone: 'rejected', title: 'URL de servidor inválida', description: err.message };
        this.#notify();
        return;
      }
    }
    this.isBusy = true;
    this.feedback = null;
    this.sessionExpiredNotice = false;
    this.#notify();
    try {
      this.desafio = await this.api.iniciarLogin({ correo, password });
      this.conflicto = null;
      this.stage = 'otp';
    } catch (err) {
      this.feedback = this.#feedbackFromError(err, 'No se pudo iniciar sesión');
    } finally {
      this.isBusy = false;
      this.#notify();
    }
  }

  async verificarOtp(codigoOtp) {
    if (this.isBusy || !this.desafio || !this.#requireOnline('verificar OTP')) return;
    this.isBusy = true;
    this.feedback = null;
    this.#notify();
    try {
      const tokens = await this.api.verificarOtp({
        desafioId: this.desafio.desafioId,
        codigoOtp,
      });
      await this.#finalizarAutenticacion(tokens);
    } catch (err) {
      if (err instanceof GpagoWebApiException && err.esConflictoSesion && err.conflictoSesion) {
        this.conflicto = err.conflictoSesion;
        this.stage = 'conflict';
      } else {
        if (err?.esOtpBloqueado || err?.esOtpExpirado) {
          this.desafio = {
            ...this.desafio,
            intentosRestantes: err.esOtpBloqueado
              ? 0
              : Math.max(0, Number(this.desafio.intentosRestantes || 0) - 1),
          };
        } else if (err?.codigo === 'OTP_INVALIDO' && this.desafio.intentosRestantes > 0) {
          this.desafio = {
            ...this.desafio,
            intentosRestantes: this.desafio.intentosRestantes - 1,
          };
        }
        this.feedback = this.#feedbackFromError(err, 'No se pudo verificar el código');
      }
    } finally {
      this.isBusy = false;
      this.#notify();
    }
  }

  async reenviarOtp() {
    if (this.isBusy || !this.desafio || !this.#requireOnline('reenviar OTP')) return;
    this.isBusy = true;
    this.feedback = null;
    this.#notify();
    try {
      this.desafio = await this.api.reenviarOtp({ desafioId: this.desafio.desafioId });
      this.feedback = {
        tone: 'success',
        title: 'Nuevo código enviado',
        description: 'Enviamos un nuevo código OTP a tu correo registrado. El anterior quedó invalidado.',
      };
    } catch (err) {
      this.feedback = this.#feedbackFromError(err, 'No fue posible reenviar el código');
    } finally {
      this.isBusy = false;
      this.#notify();
    }
  }

  async confirmarReemplazoSesion() {
    if (this.isBusy || !this.conflicto || !this.#requireOnline('reemplazar la sesión anterior')) return;
    this.isBusy = true;
    this.feedback = null;
    this.#notify();
    try {
      const tokens = await this.api.reemplazarSesion({
        desafioId: this.conflicto.desafioId,
        tokenConfirmacion: this.conflicto.tokenConfirmacion,
        confirmarCierre: true,
      });
      await this.#finalizarAutenticacion(tokens);
    } catch (err) {
      this.feedback = this.#feedbackFromError(err, 'No se pudo reemplazar la sesión anterior');
      if (err?.codigo === 'CONFIRMACION_SESION_INVALIDA') {
        this.conflicto = null;
        this.desafio = null;
        this.stage = 'login';
      }
    } finally {
      this.isBusy = false;
      this.#notify();
    }
  }

  cancelarConflicto() {
    this.conflicto = null;
    this.desafio = null;
    this.feedback = {
      tone: 'pending',
      title: 'Inicio cancelado',
      description: 'Se conservó la sesión activa anterior sin cambios.',
    };
    this.stage = 'login';
    this.#notify();
  }

  volverALogin() {
    this.conflicto = null;
    this.desafio = null;
    this.feedback = null;
    this.stage = 'login';
    this.#notify();
  }

  async #finalizarAutenticacion(tokens) {
    this.sessionStore.save(tokens);
    this.session = this.sessionStore.read();
    this.sessionExpiredNotice = false;
    this.desafio = null;
    this.conflicto = null;
    try {
      await this.#cargarPerfilYCuentas(this.session.accessToken);
      this.stage = 'home';
      this.activeTab = 'profile';
    } catch (err) {
      await this.#handleApiError(err, 'No se pudo cargar tu perfil');
    }
  }

  async #cargarPerfilYCuentas(accessToken) {
    const [perfil, cliente, cuentas] = await Promise.all([
      this.api.consultarPerfilAuth({ accessToken }),
      this.api.consultarClientePropio({ accessToken }),
      this.api.consultarCuentasPropias({ accessToken }),
    ]);
    this.perfil = perfil;
    this.cliente = cliente;
    this.cuentas = cuentas;
  }

  async refrescarPerfilYSesion() {
    if (this.isBusy || !this.#requireOnline('verificar tu sesión')) return;
    if (!(await this.verificarVigenciaLocal())) return;
    this.isBusy = true;
    this.feedback = null;
    this.#notify();
    try {
      await this.#cargarPerfilYCuentas(this.session.accessToken);
      this.feedback = {
        tone: 'success',
        title: 'Sesión verificada',
        description: 'Tu sesión única en Web y el inventario de cuentas propias están vigentes.',
      };
    } catch (err) {
      await this.#handleApiError(err, 'No se pudo verificar la sesión');
    } finally {
      this.isBusy = false;
      this.#notify();
    }
  }

  async cerrarSesion() {
    if (this.isBusy) return;
    this.isBusy = true;
    const token = this.session?.accessToken;
    this.#notify();
    try {
      if (token && this.isOnline) {
        await this.api.logout({ accessToken: token });
      }
    } catch {
      // Aunque falle la red, se revoca localmente por seguridad.
    } finally {
      this.sessionStore.clear();
      this.session = null;
      this.perfil = null;
      this.cliente = null;
      this.cuentas = [];
      this.desafio = null;
      this.conflicto = null;
      this.modal = null;
      this.#resetProtectedModules();
      this.sessionExpiredNotice = false;
      this.feedback = {
        tone: 'success',
        title: 'Sesión cerrada',
        description: 'Tu sesión Web fue cerrada de forma segura y se limpió la memoria local.',
      };
      this.stage = 'login';
      this.activeTab = 'profile';
      this.isBusy = false;
      this.#notify();
    }
  }

  async seleccionarTab(tab) {
    if (this.stage !== 'home') return;
    if (!(await this.verificarVigenciaLocal())) return;
    this.activeTab = tab;
    this.modal = null;
    this.#notify();
    if (tab === 'catalog' && !this.catalog.hasLoaded) {
      await this.cargarCatalogo();
    } else if (tab === 'history' && !this.history.hasLoaded) {
      await this.cargarHistorial(0);
    }
  }

  // ---- M2 Catálogo ----
  get catalogCategorias() {
    return agruparCatalogoPorCategoria(this.catalog.productos);
  }

  get catalogFiltrados() {
    const q = this.catalog.searchQuery.trim().toLowerCase();
    return this.catalog.productos.filter((item) => {
      if (!this.catalog.filterAll && item.idCatTipoServicio !== this.catalog.selectedCategoryId) {
        return false;
      }
      if (!q) return true;
      return (
        String(item.producto || '').toLowerCase().includes(q) ||
        String(item.servicio || '').toLowerCase().includes(q) ||
        String(item.idProducto || '').includes(q)
      );
    });
  }

  actualizarBusquedaCatalogo(q) {
    this.catalog.searchQuery = String(q || '');
    this.#notify();
  }

  seleccionarCategoriaCatalogo(catId) {
    this.catalog.filterAll = false;
    this.catalog.selectedCategoryId = catId;
    this.#notify();
  }

  seleccionarTodasCategoriasCatalogo() {
    this.catalog.filterAll = true;
    this.catalog.selectedCategoryId = null;
    this.#notify();
  }

  abrirDetalleCatalogo(idProducto) {
    const found = this.catalog.productos.find((p) => Number(p.idProducto) === Number(idProducto));
    if (!found) return;
    this.catalog.selectedProduct = found;
    this.modal = { kind: 'catalog-detail' };
    this.#notify();
  }

  cerrarModal() {
    this.modal = null;
    this.#notify();
  }

  async cargarCatalogo() {
    if (this.catalog.isLoading || !this.#requireOnline('consultar el catálogo')) return;
    if (!(await this.verificarVigenciaLocal())) return;
    this.catalog.isLoading = true;
    this.catalog.error = null;
    this.#notify();
    try {
      const items = await this.api.consultarCatalogoProductos({ accessToken: this.session.accessToken });
      this.catalog.productos = items;
      this.catalog.hasLoaded = true;
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo consultar el catálogo')) return;
      this.catalog.error = this.#feedbackFromError(err, 'Catálogo no disponible');
    } finally {
      this.catalog.isLoading = false;
      this.#notify();
    }
  }

  // ---- M3 Compra e idempotencia ----
  get cuentaActivaCompra() {
    return (
      this.cuentas.find((c) => Number(c.cuentaId) === Number(this.purchase.selectedCuentaId)) ||
      this.cuentas.find((c) => c.cuentaId > 0 && String(c.estatus).toUpperCase() === 'ACTIVA') ||
      null
    );
  }

  async iniciarCompra(producto) {
    if (!(await this.verificarVigenciaLocal())) return;
    if (!producto || !producto.activo) return;
    const activa = this.cuentas.find((c) => c.cuentaId > 0 && String(c.estatus).toUpperCase() === 'ACTIVA');
    if (!activa) {
      this.feedback = {
        tone: 'pending',
        title: 'Cuenta financiera requerida',
        description: 'No tienes una cuenta propia activa entregada por el servidor para realizar pagos.',
      };
      this.#notify();
      return;
    }
    this.modal = null;
    this.activeTab = 'purchase';
    this.purchase = {
      stage: 'capture',
      producto,
      selectedCuentaId: activa.cuentaId,
      saldoFinanciero: null,
      isLoadingSaldo: false,
      isSubmitting: false,
      localValidationError: null,
      error: null,
      validacion: null,
      solicitudConfirmada: null,
      idempotencyKey: null,
      operacion: null,
      outcomeKind: null,
      pollAttempts: 0,
      maxPolls: 5,
    };
    this.#notify();
    await this.cargarSaldoCompra();
  }

  async cargarSaldoCompra() {
    const cuentaId = this.purchase.selectedCuentaId;
    if (!cuentaId || !this.isOnline || !this.session?.accessToken) return;
    this.purchase.isLoadingSaldo = true;
    this.#notify();
    try {
      const saldo = await this.api.consultarSaldoFinanciero({
        accessToken: this.session.accessToken,
        cuentaId,
      });
      this.purchase.saldoFinanciero = saldo;
    } catch (err) {
      await this.#handleApiError(err, 'No se pudo consultar el saldo');
    } finally {
      this.purchase.isLoadingSaldo = false;
      this.#notify();
    }
  }

  seleccionarCuentaCompra(cuentaId) {
    this.purchase.selectedCuentaId = Number(cuentaId);
    this.purchase.saldoFinanciero = null;
    this.#notify();
    return this.cargarSaldoCompra();
  }

  async prevalidarYRevisar({ montoRaw, destinoModo, destinoRaw }) {
    if (this.purchase.isSubmitting || !this.#requireOnline('prevalidar el pago')) return;
    if (!(await this.verificarVigenciaLocal())) return;
    const cuenta = this.cuentaActivaCompra;
    const p = this.purchase.producto;
    if (!cuenta || !p || String(cuenta.estatus).toUpperCase() !== 'ACTIVA') {
      this.purchase.localValidationError = 'Selecciona una cuenta propia activa.';
      this.#notify();
      return;
    }
    const cleanMonto = String(montoRaw ?? '').trim().replace(',', '.');
    if (!/^\d{1,12}(\.\d{1,2})?$/.test(cleanMonto) || Number(cleanMonto) < 0.01) {
      this.purchase.localValidationError = 'Ingresa un importe válido mayor a 0.00 (máximo 2 decimales).';
      this.#notify();
      return;
    }
    const cleanDest = String(destinoRaw ?? '').trim();
    let telefono = null;
    let referencia = null;
    if (destinoModo === 'telefono') {
      if (!/^\d{10}$/.test(cleanDest)) {
        this.purchase.localValidationError = 'El teléfono debe contener exactamente 10 dígitos numéricos.';
        this.#notify();
        return;
      }
      telefono = cleanDest;
    } else if (destinoModo === 'referencia') {
      if (!cleanDest) {
        this.purchase.localValidationError = 'Ingresa la referencia requerida para validar en servidor.';
        this.#notify();
        return;
      }
      referencia = cleanDest;
    }

    const pagoReq = {
      idServicio: p.idServicio,
      idProducto: p.idProducto,
      montoPago: Number(cleanMonto),
      telefono,
      referencia,
    };

    this.purchase.isSubmitting = true;
    this.purchase.localValidationError = null;
    this.purchase.error = null;
    this.#notify();
    try {
      const validacion = await this.api.validarPagoLocal({
        accessToken: this.session.accessToken,
        pago: pagoReq,
      });
      if (!validacion?.contratoLocalValido) {
        this.purchase.localValidationError = 'El servidor no autorizó el contrato local para este producto.';
        return;
      }
      this.purchase.validacion = validacion;
      this.purchase.solicitudConfirmada = { cuentaId: cuenta.cuentaId, pago: pagoReq };
      this.purchase.idempotencyKey = this.#uuidGen();
      this.purchase.operacion = null;
      this.purchase.outcomeKind = null;
      this.purchase.pollAttempts = 0;
      this.purchase.stage = 'review';
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo prevalidar la operación')) return;
      this.purchase.error = this.#feedbackFromError(err, 'Validación rechazada');
    } finally {
      this.purchase.isSubmitting = false;
      this.#notify();
    }
  }

  volverACapturaCompra() {
    if (this.purchase.isSubmitting) return;
    this.purchase.stage = 'capture';
    this.purchase.error = null;
    this.#notify();
  }

  async confirmarYEnviarPago() {
    if (
      this.purchase.isSubmitting ||
      !this.purchase.solicitudConfirmada ||
      !this.purchase.idempotencyKey ||
      !this.#requireOnline('confirmar el pago')
    ) {
      return;
    }
    if (!(await this.verificarVigenciaLocal())) return;
    this.purchase.isSubmitting = true;
    this.purchase.error = null;
    this.#notify();
    try {
      const op = await this.api.crearPagoIdempotente({
        accessToken: this.session.accessToken,
        idempotencyKey: this.purchase.idempotencyKey,
        solicitud: this.purchase.solicitudConfirmada,
      });
      await this.#aplicarEstadoOperacion(op);
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo procesar el pago')) return;
      if (this.#esIncertidumbreComunicacion(err)) {
        this.purchase.error = this.#feedbackFromError(err, 'Comunicación incierta');
        this.purchase.outcomeKind = 'uncertain';
        this.purchase.stage = 'result';
      } else {
        this.purchase.error = this.#feedbackFromError(err, `Operación rechazada (${err.codigo || 'ERROR'})`);
        this.purchase.outcomeKind = 'rejected';
        this.purchase.stage = 'result';
      }
    } finally {
      this.purchase.isSubmitting = false;
      this.#notify();
    }
  }

  async consultarEstadoORecuperarCompra() {
    if (this.purchase.isSubmitting || !this.#requireOnline('consultar el estado del pago')) return;
    if (!(await this.verificarVigenciaLocal())) return;
    this.purchase.isSubmitting = true;
    this.purchase.error = null;
    this.#notify();
    try {
      if (this.purchase.operacion?.id) {
        if (this.purchase.pollAttempts >= this.purchase.maxPolls) {
          this.purchase.error = {
            tone: 'pending',
            title: 'Límite de consultas alcanzado',
            description: 'Se alcanzó el límite acotado de consultas manuales en esta vista.',
          };
          return;
        }
        this.purchase.pollAttempts += 1;
        const op = await this.api.consultarPago({
          accessToken: this.session.accessToken,
          pagoId: this.purchase.operacion.id,
        });
        await this.#aplicarEstadoOperacion(op);
      } else if (this.purchase.solicitudConfirmada && this.purchase.idempotencyKey) {
        const op = await this.api.crearPagoIdempotente({
          accessToken: this.session.accessToken,
          idempotencyKey: this.purchase.idempotencyKey,
          solicitud: this.purchase.solicitudConfirmada,
        });
        await this.#aplicarEstadoOperacion(op);
      }
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo consultar la operación')) return;
      if (this.#esIncertidumbreComunicacion(err)) {
        this.purchase.error = this.#feedbackFromError(err, 'Comunicación incierta');
        this.purchase.outcomeKind = 'uncertain';
        this.purchase.stage = 'result';
      } else {
        this.purchase.error = this.#feedbackFromError(err, 'Error al verificar operación');
      }
    } finally {
      this.purchase.isSubmitting = false;
      this.#notify();
    }
  }

  async conciliarOperacionUnknown() {
    if (this.purchase.isSubmitting || !this.purchase.operacion?.id || !this.#requireOnline('conciliar el pago')) {
      return;
    }
    if (!(await this.verificarVigenciaLocal())) return;
    this.purchase.isSubmitting = true;
    this.purchase.error = null;
    this.#notify();
    try {
      const op = await this.api.conciliarPago({
        accessToken: this.session.accessToken,
        pagoId: this.purchase.operacion.id,
      });
      await this.#aplicarEstadoOperacion(op);
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo conciliar la operación')) return;
      if (this.#esIncertidumbreComunicacion(err)) {
        this.purchase.error = this.#feedbackFromError(err, 'Comunicación incierta');
        this.purchase.outcomeKind = 'uncertain';
      } else {
        this.purchase.error = this.#feedbackFromError(err, 'No se pudo completar la conciliación');
      }
    } finally {
      this.purchase.isSubmitting = false;
      this.#notify();
    }
  }

  async #aplicarEstadoOperacion(op) {
    this.purchase.operacion = op;
    this.purchase.stage = 'result';
    const estado = String(op?.estado || '').toUpperCase();
    if (estado === 'CAPTURED') {
      this.purchase.outcomeKind = 'captured';
      await this.cargarSaldoCompra();
      if (this.purchase.saldoFinanciero) {
        this.cuentas = this.cuentas.map((c) =>
          Number(c.cuentaId) === Number(this.purchase.saldoFinanciero.cuentaId)
            ? { ...c, saldo: this.purchase.saldoFinanciero.contabilizado }
            : c,
        );
      }
    } else if (estado === 'FAILED') {
      this.purchase.outcomeKind = 'rejected';
    } else {
      this.purchase.outcomeKind = 'pending';
    }
  }

  #esIncertidumbreComunicacion(err) {
    if (!err) return true;
    if (err.isNetworkError) return true;
    if (err.codigo === 'RESPUESTA_INVALIDA') return true;
    if (err.statusCode >= 500 && err.codigo !== 'REFERENCIA_NO_CONCLUYENTE') return true;
    return false;
  }

  // ---- M4 Historial ----
  async cargarHistorial(page = 0) {
    if (this.history.isLoading || page < 0 || !this.#requireOnline('consultar el historial')) return;
    if (!(await this.verificarVigenciaLocal())) return;
    this.history.isLoading = true;
    this.history.error = null;
    this.#notify();
    try {
      const res = await this.api.consultarHistorialPagos({
        accessToken: this.session.accessToken,
        page,
        size: this.history.size,
      });
      this.history.items = res.contenido.filter((c) => String(c.estado).toUpperCase() === 'CAPTURED');
      this.history.page = res.page;
      this.history.totalElementos = res.totalElementos;
      this.history.totalPaginas = res.totalPaginas;
      this.history.hasLoaded = true;
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo consultar el historial')) return;
      this.history.error = this.#feedbackFromError(err, 'No se pudo cargar el historial');
    } finally {
      this.history.isLoading = false;
      this.#notify();
    }
  }

  async abrirDetalleHistorial(pagoId) {
    const initial = this.history.items.find((i) => i.id === pagoId) || null;
    if (!initial) return;
    if (!(await this.verificarVigenciaLocal())) return;
    this.history.selectedDetail = initial;
    this.history.detailError = null;
    this.history.isLoadingDetail = true;
    this.modal = { kind: 'history-detail' };
    this.#notify();
    if (!this.isOnline) {
      this.history.isLoadingDetail = false;
      this.history.detailError = {
        tone: 'network',
        title: 'Sin conexión',
        description: 'No fue posible actualizar el detalle desde el servidor.',
      };
      this.#notify();
      return;
    }
    try {
      this.history.selectedDetail = await this.api.consultarDetalleHistorialPago({
        accessToken: this.session.accessToken,
        pagoId,
      });
    } catch (err) {
      if (await this.#handleApiError(err, 'No se pudo cargar el detalle')) return;
      this.history.detailError = this.#feedbackFromError(err, 'No se pudo refrescar el detalle');
    } finally {
      this.history.isLoadingDetail = false;
      this.#notify();
    }
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderBanner(fb) {
  if (!fb) return '';
  const cls =
    fb.tone === 'success'
      ? 'status-success'
      : fb.tone === 'rejected'
        ? 'status-rejected'
        : fb.tone === 'network'
          ? 'status-network'
          : 'status-pending';
  const icon =
    fb.tone === 'success'
      ? '✓'
      : fb.tone === 'rejected'
        ? '⊘'
        : fb.tone === 'network'
          ? '⚠'
          : '◷';
  return `
    <div class="status-banner ${cls}" role="status" aria-live="polite">
      <div class="status-header">
        <span class="status-icon" aria-hidden="true">${icon}</span>
        <h2>${escapeHtml(fb.title)}</h2>
      </div>
      <p>${escapeHtml(fb.description)}</p>
    </div>
  `;
}

export function mountGpagoWebApp(doc = globalThis.document, win = globalThis.window) {
  if (!doc || !win) return null;
  const appEl = doc.getElementById('gpago-app');
  if (!appEl) return null;

  const navEl = doc.getElementById('main-nav');
  const logoutBtn = doc.getElementById('header-logout-btn');
  const installBtn = doc.getElementById('pwa-install-btn');
  const offlineBanner = doc.getElementById('offline-banner');
  const modalRoot = doc.getElementById('modal-root');

  let initialBaseUrl = DEFAULT_PUBLIC_API_BASE_URL;
  try {
    const paramUrl = new URLSearchParams(win.location.search).get('apiBase');
    if (paramUrl) initialBaseUrl = paramUrl;
  } catch {
    // Mantiene URL pública por defecto.
  }

  let controller;
  try {
    controller = new GpagoWebController({
      apiClient: new GpagoWebApiClient({ baseUrl: initialBaseUrl }),
      sessionStore: new WebSecureSessionStore(win),
      isOnline: win.navigator?.onLine !== false,
    });
  } catch {
    controller = new GpagoWebController({
      apiClient: new GpagoWebApiClient({ baseUrl: DEFAULT_PUBLIC_API_BASE_URL }),
      sessionStore: new WebSecureSessionStore(win),
      isOnline: win.navigator?.onLine !== false,
    });
  }

  let deferredInstallPrompt = null;
  win.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    deferredInstallPrompt = event;
    installBtn?.classList.remove('hidden');
  });
  win.addEventListener('appinstalled', () => {
    deferredInstallPrompt = null;
    installBtn?.classList.add('hidden');
  });
  installBtn?.addEventListener('click', async () => {
    if (!deferredInstallPrompt) return;
    deferredInstallPrompt.prompt();
    await deferredInstallPrompt.userChoice;
    deferredInstallPrompt = null;
    installBtn.classList.add('hidden');
  });

  if (win.navigator?.serviceWorker) {
    win.navigator.serviceWorker
      .register('/sw.js', { type: 'module', scope: '/' })
      .catch(() => {
        // Entorno sin Service Worker disponible (no bloquea uso HTTPS).
      });
  }

  win.addEventListener('online', () => controller.setOnline(true));
  win.addEventListener('offline', () => controller.setOnline(false));

  logoutBtn?.addEventListener('click', () => controller.cerrarSesion());

  navEl?.querySelectorAll('[data-tab]').forEach((btn) => {
    btn.addEventListener('click', () => controller.seleccionarTab(btn.dataset.tab));
  });

  doc.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && controller.modal) {
      controller.cerrarModal();
    }
  });

  const copyToClipboard = async (text) => {
    try {
      await win.navigator.clipboard.writeText(text);
    } catch {
      // Ignora si el navegador restringe portapapeles sin foco.
    }
  };

  function render() {
    offlineBanner?.classList.toggle('hidden', controller.isOnline);
    const inHome = controller.stage === 'home';
    navEl?.classList.toggle('hidden', !inHome);
    logoutBtn?.classList.toggle('hidden', !inHome);

    if (inHome && navEl) {
      navEl.querySelectorAll('[data-tab]').forEach((tabBtn) => {
        const isCurrent = tabBtn.dataset.tab === controller.activeTab;
        tabBtn.setAttribute('aria-selected', isCurrent ? 'true' : 'false');
      });
    }

    if (controller.stage === 'login') {
      renderLogin();
    } else if (controller.stage === 'otp') {
      renderOtp();
    } else if (controller.stage === 'conflict') {
      renderConflict();
    } else {
      if (controller.activeTab === 'catalog') {
        renderCatalog();
      } else if (controller.activeTab === 'purchase') {
        renderPurchase();
      } else if (controller.activeTab === 'history') {
        renderHistory();
      } else {
        renderProfile();
      }
    }

    renderModal();
  }

  function renderLogin() {
    const expiredCard = controller.sessionExpiredNotice
      ? `
      <div class="card stack-sm" role="region" aria-label="Aviso de sesión" data-testid="session-expired-card">
        <div class="status-banner status-pending">
          <div class="status-header">
            <span class="status-icon" aria-hidden="true">🛡</span>
            <h2>Tu sesión ha dejado de estar activa</h2>
          </div>
          <p>• Se cerraron las vistas protegidas en esta pestaña.</p>
          <p>• El token JWT en memoria fue eliminado (nunca se persiste en localStorage).</p>
        </div>
        <button type="button" id="ack-expired-btn" class="btn btn-secondary">Entendido</button>
      </div>
    `
      : '';

    appEl.innerHTML = `
      <section class="form-shell stack-lg" aria-labelledby="login-heading">
        <div class="card stack-md">
          <h1 id="login-heading">Iniciar sesión</h1>
          <p class="help-text">
            Ingresa tus credenciales registradas. Enviaremos un código OTP de 6 dígitos a tu correo.
          </p>
          ${renderBanner(controller.feedback)}
          ${expiredCard}
          <form id="login-form" class="stack-md">
            <div class="field-group">
              <label class="field-label" for="login-email">Correo electrónico</label>
              <input id="login-email" name="correo" type="email" maxlength="100" required
                autocomplete="email" class="input-control" placeholder="usuario@dominio.com"
                ${controller.isBusy ? 'disabled' : ''} />
            </div>
            <div class="field-group">
              <label class="field-label" for="login-password">Contraseña</label>
              <input id="login-password" name="password" type="password" maxlength="72" required
                autocomplete="current-password" class="input-control"
                ${controller.isBusy ? 'disabled' : ''} />
            </div>
            <details>
              <summary class="help-text">Servidor API público HTTPS (configuración opcional)</summary>
              <div class="field-group" style="margin-top: 8px;">
                <label class="field-label" for="login-server-url">URL base del backend</label>
                <input id="login-server-url" name="serverUrl" type="url" class="input-control"
                  value="${escapeHtml(controller.api.baseUrl)}" />
              </div>
            </details>
            <button type="submit" class="btn btn-primary btn-full"
              ${controller.isBusy || !controller.isOnline ? 'disabled' : ''}>
              ${controller.isBusy ? 'Validando acceso...' : 'Continuar'}
            </button>
          </form>
        </div>
      </section>
    `;

    doc.getElementById('ack-expired-btn')?.addEventListener('click', () => {
      controller.descartarAvisoSesionExpirada();
    });
    doc.getElementById('login-form')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const fd = new FormData(e.currentTarget);
      controller.iniciarLogin({
        correo: fd.get('correo'),
        password: fd.get('password'),
        serverUrl: fd.get('serverUrl'),
      });
    });
  }

  function renderOtp() {
    const d = controller.desafio;
    if (!d) return;
    appEl.innerHTML = `
      <section class="form-shell stack-lg" aria-labelledby="otp-heading">
        <div class="card stack-md">
          <h1 id="otp-heading">Verificación en dos pasos</h1>
          <p class="help-text">
            Ingresa el código de 6 dígitos enviado a <strong>${escapeHtml(d.correoEnmascarado)}</strong>.
            Intentos restantes: ${escapeHtml(d.intentosRestantes)}.
          </p>
          ${renderBanner(controller.feedback)}
          <form id="otp-form" class="stack-md">
            <div class="field-group">
              <label class="field-label" for="otp-code">Código OTP de 6 dígitos</label>
              <input id="otp-code" name="codigoOtp" type="text" inputmode="numeric" pattern="[0-9]{6}"
                maxlength="6" required class="input-control folio" placeholder="000000" />
            </div>
            <div class="actions-wrap">
              <button type="button" id="otp-paste-btn" class="btn btn-secondary">Pegar código</button>
              <button type="button" id="otp-resend-btn" class="btn btn-text"
                ${controller.isBusy || d.reenviosRestantes <= 0 ? 'disabled' : ''}>
                Reenviar (${escapeHtml(d.reenviosRestantes)})
              </button>
            </div>
            <button type="submit" class="btn btn-primary btn-full"
              ${controller.isBusy || !controller.isOnline ? 'disabled' : ''}>
              Verificar código
            </button>
            <button type="button" id="otp-back-btn" class="btn btn-secondary btn-full">Volver a inicio</button>
          </form>
        </div>
      </section>
    `;
    doc.getElementById('otp-paste-btn')?.addEventListener('click', async () => {
      try {
        const raw = await win.navigator.clipboard.readText();
        const digits = String(raw || '').replace(/[^0-9]/g, '').slice(0, 6);
        const input = doc.getElementById('otp-code');
        if (input && digits) input.value = digits;
      } catch {
        // Portapapeles no disponible.
      }
    });
    doc.getElementById('otp-resend-btn')?.addEventListener('click', () => controller.reenviarOtp());
    doc.getElementById('otp-back-btn')?.addEventListener('click', () => controller.volverALogin());
    doc.getElementById('otp-form')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const fd = new FormData(e.currentTarget);
      controller.verificarOtp(fd.get('codigoOtp'));
    });
  }

  function renderConflict() {
    const c = controller.conflicto;
    if (!c) return;
    const activaLabel = c.plataformaActiva === 'ANDROID' ? 'GPago Android' : 'GPago Web';
    const nuevaLabel = c.plataformaNueva === 'ANDROID' ? 'GPago Android' : 'GPago Web';
    appEl.innerHTML = `
      <section class="form-shell stack-lg" aria-labelledby="conflict-heading">
        <div class="card stack-md">
          <h1 id="conflict-heading">Sesión activa en otra plataforma</h1>
          ${renderBanner({
            tone: 'pending',
            title: `Sesión vigente detectada en ${activaLabel}`,
            description: `Tu usuario mantiene una sesión única activa desde ${formatServerTimestamp(c.sesionActivaDesde) || c.sesionActivaDesde}.`,
          })}
          ${renderBanner(controller.feedback)}
          <p>
            Si confirmas explícitamente, se revocará de inmediato la sesión en <strong>${escapeHtml(activaLabel)}</strong>
            y se autorizará el acceso en <strong>${escapeHtml(nuevaLabel)}</strong>.
          </p>
          <div class="actions-stack">
            <button type="button" id="conflict-confirm-btn" class="btn btn-primary btn-full"
              ${controller.isBusy || !controller.isOnline ? 'disabled' : ''}>
              Cerrar sesión anterior y continuar
            </button>
            <button type="button" id="conflict-cancel-btn" class="btn btn-secondary btn-full">Cancelar</button>
          </div>
        </div>
      </section>
    `;
    doc.getElementById('conflict-confirm-btn')?.addEventListener('click', () => controller.confirmarReemplazoSesion());
    doc.getElementById('conflict-cancel-btn')?.addEventListener('click', () => controller.cancelarConflicto());
  }

  function renderProfile() {
    const nombre = [
      controller.cliente?.nombre,
      controller.cliente?.segundoNombre,
      controller.cliente?.apellidoPaterno,
      controller.cliente?.apellidoMaterno,
    ]
      .filter(Boolean)
      .join(' ');
    const cuentasHtml = controller.cuentas.length
      ? controller.cuentas
          .map(
            (c) => `
          <div class="summary-box stack-sm">
            <div class="actions-wrap" style="justify-content: space-between;">
              <strong>Cuenta ID #${escapeHtml(c.cuentaId)}</strong>
              <span class="badge ${c.estatus === 'ACTIVA' ? 'badge-success' : 'badge-neutral'}">${escapeHtml(c.estatus)}</span>
            </div>
            <div class="folio">${escapeHtml(c.numeroCuenta)}</div>
            <div class="amount">${escapeHtml(formatMxn(c.saldo))}</div>
          </div>
        `,
          )
          .join('')
      : '<p class="help-text">No hay cuentas propias asociadas registradas.</p>';

    appEl.innerHTML = `
      <section class="stack-lg" aria-labelledby="profile-heading">
        <div class="actions-wrap" style="justify-content: space-between;">
          <div>
            <h1 id="profile-heading">Cuenta verificada</h1>
            <p class="help-text">
              Sesión única activa en GPago Web · Almacenamiento en memoria (${escapeHtml(controller.sessionStore.persistenceStrategy)} · ${escapeHtml(CACHE_NAME)})
            </p>
          </div>
          <button type="button" id="refresh-session-btn" class="btn btn-secondary"
            ${controller.isBusy || !controller.isOnline ? 'disabled' : ''}>
            Verificar sesión
          </button>
        </div>
        ${renderBanner(controller.feedback)}
        <div class="grid-cards">
          <article class="card stack-sm">
            <h2>Perfil</h2>
            <p><strong>${escapeHtml(nombre || controller.perfil?.correo)}</strong></p>
            <p class="help-text">${escapeHtml(controller.perfil?.correo)}</p>
            <div class="actions-wrap">
              <span class="badge badge-neutral">Rol: ${escapeHtml(controller.perfil?.rol)}</span>
              <span class="badge badge-success">Estatus: ${escapeHtml(controller.cliente?.estatus || 'ACTIVO')}</span>
            </div>
          </article>
          <article class="card stack-md" style="grid-column: span 8;">
            <h2>Cuentas propias (ID servidor)</h2>
            ${cuentasHtml}
          </article>
        </div>
      </section>
    `;
    doc.getElementById('refresh-session-btn')?.addEventListener('click', () => controller.refrescarPerfilYSesion());
  }

  function renderCatalog() {
    const cats = controller.catalogCategorias;
    const list = controller.catalogFiltrados;

    const chips = [
      `<button type="button" class="chip" data-cat="ALL" aria-pressed="${controller.catalog.filterAll ? 'true' : 'false'}">Todas (${controller.catalog.productos.length})</button>`,
      ...cats.map(
        (cat) => `
          <button type="button" class="chip" data-cat="${escapeHtml(cat.idCatTipoServicio ?? 'NONE')}"
            aria-pressed="${!controller.catalog.filterAll && controller.catalog.selectedCategoryId === cat.idCatTipoServicio ? 'true' : 'false'}">
            ${escapeHtml(cat.etiqueta)} (${cat.productos.length})
          </button>
        `,
      ),
    ].join('');

    let bodyHtml = '';
    if (controller.catalog.isLoading && !controller.catalog.hasLoaded) {
      bodyHtml = '<div class="card"><p>Cargando catálogo real desde GPago...</p></div>';
    } else if (controller.catalog.error) {
      bodyHtml = `
        <div class="stack-md">
          ${renderBanner(controller.catalog.error)}
          <button type="button" id="catalog-retry-btn" class="btn btn-primary">Reintentar</button>
        </div>
      `;
    } else if (list.length === 0) {
      bodyHtml = `
        <div class="card stack-md">
          <h2>Sin productos disponibles</h2>
          <p class="help-text">No se encontraron servicios activos con el filtro actual.</p>
          <button type="button" id="catalog-retry-btn" class="btn btn-secondary">Consultar nuevamente</button>
        </div>
      `;
    } else {
      bodyHtml = `
        <div class="grid-cards">
          ${list
            .map(
              (p) => `
            <article class="card stack-sm">
              <div class="actions-wrap" style="justify-content: space-between;">
                <span class="badge badge-neutral">Cat #${escapeHtml(p.idCatTipoServicio ?? '-')}</span>
                <span class="badge ${p.activo ? 'badge-success' : 'badge-pending'}">${p.activo ? 'Disponible' : 'No disponible'}</span>
              </div>
              <h2>${escapeHtml(p.producto)}</h2>
              <p class="help-text">${escapeHtml(p.servicio)}</p>
              <div class="amount" style="font-size: 24px; line-height: 32px;">
                ${p.precio && p.precio > 0 ? escapeHtml(formatMxn(p.precio)) : 'Importe variable'}
              </div>
              <button type="button" class="btn btn-secondary btn-full" data-product-detail="${escapeHtml(p.idProducto)}">
                Ver detalle
              </button>
            </article>
          `,
            )
            .join('')}
        </div>
      `;
    }

    appEl.innerHTML = `
      <section class="stack-lg" aria-labelledby="catalog-heading">
        <div class="actions-wrap" style="justify-content: space-between;">
          <div>
            <h1 id="catalog-heading">Catálogo de servicios</h1>
            <p class="help-text">Listado sincronizado en servidor (sin llamadas directas de cuota al proveedor).</p>
          </div>
          <button type="button" id="catalog-refresh-btn" class="btn btn-secondary"
            ${controller.catalog.isLoading || !controller.isOnline ? 'disabled' : ''}>
            Actualizar
          </button>
        </div>
        <div class="field-group">
          <label class="field-label" for="catalog-search-input">Buscar servicio o producto</label>
          <input id="catalog-search-input" type="search" class="input-control"
            value="${escapeHtml(controller.catalog.searchQuery)}" placeholder="Nombre del servicio, producto o ID" />
        </div>
        <div class="chip-row" role="group" aria-label="Filtrar por categoría">${chips}</div>
        ${bodyHtml}
      </section>
    `;

    doc.getElementById('catalog-refresh-btn')?.addEventListener('click', () => controller.cargarCatalogo());
    doc.getElementById('catalog-retry-btn')?.addEventListener('click', () => controller.cargarCatalogo());
    doc.getElementById('catalog-search-input')?.addEventListener('input', (e) => {
      controller.actualizarBusquedaCatalogo(e.target.value);
    });
    appEl.querySelectorAll('[data-cat]').forEach((btn) => {
      btn.addEventListener('click', () => {
        if (btn.dataset.cat === 'ALL') controller.seleccionarTodasCategoriasCatalogo();
        else controller.seleccionarCategoriaCatalogo(btn.dataset.cat === 'NONE' ? null : Number(btn.dataset.cat));
      });
    });
    appEl.querySelectorAll('[data-product-detail]').forEach((btn) => {
      btn.addEventListener('click', () => controller.abrirDetalleCatalogo(Number(btn.dataset.productDetail)));
    });
  }

  function renderPurchase() {
    const ps = controller.purchase;
    const p = ps.producto;
    if (!p) {
      appEl.innerHTML = `<section class="card"><p>Selecciona un producto en Catálogo para iniciar una compra.</p></section>`;
      return;
    }

    if (ps.stage === 'review') {
      const s = ps.solicitudConfirmada;
      appEl.innerHTML = `
        <section class="form-shell stack-lg" aria-labelledby="purchase-review-heading">
          <div class="card stack-md">
            <h1 id="purchase-review-heading">Confirmar y pagar servicio</h1>
            <p class="help-text">Revisa los datos validados por el servidor antes de confirmar tu operación.</p>
            <div class="summary-box">
              <div class="summary-row"><span>Producto</span><strong>${escapeHtml(p.producto)}</strong></div>
              <div class="summary-row"><span>Cuenta ID</span><strong>#${escapeHtml(s.cuentaId)} (${escapeHtml(maskAccount(controller.cuentaActivaCompra?.numeroCuenta))})</strong></div>
              <div class="summary-row"><span>Importe total</span><strong>${escapeHtml(formatMxn(s.pago.montoPago))}</strong></div>
              <div class="summary-row"><span>Destino</span><span>${escapeHtml(s.pago.telefono || s.pago.referencia || 'Autorizado en servidor')}</span></div>
            </div>
            <div class="summary-box stack-sm">
              <span class="help-text">Idempotency-Key asignada</span>
              <div class="folio">${escapeHtml(ps.idempotencyKey)}</div>
            </div>
            <button type="button" id="purchase-confirm-btn" class="btn btn-primary btn-full"
              ${ps.isSubmitting || !controller.isOnline ? 'disabled' : ''}>
              ${ps.isSubmitting ? 'Enviando operación segura...' : `Confirmar y pagar ${formatMxn(s.pago.montoPago)}`}
            </button>
            <button type="button" id="purchase-edit-btn" class="btn btn-secondary btn-full"
              ${ps.isSubmitting ? 'disabled' : ''}>Modificar datos</button>
          </div>
        </section>
      `;
      doc.getElementById('purchase-confirm-btn')?.addEventListener('click', () => controller.confirmarYEnviarPago());
      doc.getElementById('purchase-edit-btn')?.addEventListener('click', () => controller.volverACapturaCompra());
      return;
    }

    if (ps.stage === 'result') {
      const op = ps.operacion;
      let outcomeHtml = '';
      if (ps.outcomeKind === 'captured' && op) {
        outcomeHtml = `
          ${renderBanner({ tone: 'success', title: 'Pago aplicado (CAPTURED)', description: 'Único estado que acredita compra confirmada en servidor.' })}
          <div class="summary-box stack-sm">
            <div class="amount">${escapeHtml(formatMxn(op.monto))}</div>
            <div><span class="help-text">UUID de operación</span><div class="folio">${escapeHtml(op.id)}</div></div>
            <div class="summary-row"><span>Fecha respuesta servidor</span><span>${escapeHtml(formatServerTimestamp(op.serverTimestamp) || 'No incluida en el DTO actual')}</span></div>
          </div>
          <button type="button" id="purchase-copy-uuid-btn" class="btn btn-secondary btn-full">Copiar UUID de operación</button>
        `;
      } else if (ps.outcomeKind === 'pending' && op) {
        outcomeHtml = `
          ${renderBanner({ tone: 'pending', title: `Operación pendiente (${op.estado})`, description: 'No acredita compra aplicada ni se reenvía automáticamente.' })}
          <div class="summary-box stack-sm">
            <div class="folio">${escapeHtml(op.id)}</div>
            ${op.revisionManual ? '<p class="badge badge-pending">Revisión manual requerida en servidor</p>' : ''}
          </div>
          <button type="button" id="purchase-poll-btn" class="btn btn-primary btn-full" ${ps.isSubmitting || !controller.isOnline ? 'disabled' : ''}>Consultar estado actual</button>
          ${op.estado === 'UNKNOWN' && !op.revisionManual ? `<button type="button" id="purchase-reconcile-btn" class="btn btn-secondary btn-full" ${ps.isSubmitting || !controller.isOnline ? 'disabled' : ''}>Conciliar UNKNOWN</button>` : ''}
        `;
      } else if (ps.outcomeKind === 'uncertain') {
        outcomeHtml = `
          ${renderBanner(ps.error || { tone: 'network', title: 'Comunicación incierta', description: 'Se conservó la misma Idempotency-Key para evitar cargos duplicados.' })}
          <div class="summary-box stack-sm">
            <span class="help-text">Idempotency-Key conservada</span>
            <div class="folio">${escapeHtml(ps.idempotencyKey)}</div>
          </div>
          <button type="button" id="purchase-recover-btn" class="btn btn-primary btn-full" ${ps.isSubmitting || !controller.isOnline ? 'disabled' : ''}>Verificar con misma clave</button>
        `;
      } else {
        outcomeHtml = renderBanner(ps.error || { tone: 'rejected', title: 'Operación rechazada', description: 'Resultado final sin reintento automático.' });
      }
      appEl.innerHTML = `
        <section class="form-shell stack-lg" aria-labelledby="purchase-result-heading">
          <div class="card stack-md">
            <h1 id="purchase-result-heading">Resultado de operación</h1>
            ${outcomeHtml}
            <button type="button" id="purchase-back-catalog-btn" class="btn btn-secondary btn-full">Volver al catálogo</button>
          </div>
        </section>
      `;
      doc.getElementById('purchase-copy-uuid-btn')?.addEventListener('click', () => copyToClipboard(op.id));
      doc.getElementById('purchase-poll-btn')?.addEventListener('click', () => controller.consultarEstadoORecuperarCompra());
      doc.getElementById('purchase-reconcile-btn')?.addEventListener('click', () => controller.conciliarOperacionUnknown());
      doc.getElementById('purchase-recover-btn')?.addEventListener('click', () => controller.consultarEstadoORecuperarCompra());
      doc.getElementById('purchase-back-catalog-btn')?.addEventListener('click', () => controller.seleccionarTab('catalog'));
      return;
    }

    const fixedAmount = p.precio && p.precio > 0 ? Number(p.precio).toFixed(2) : '';
    const cuentaOptions = controller.cuentas
      .map(
        (c) => `<option value="${escapeHtml(c.cuentaId)}" ${Number(c.cuentaId) === Number(ps.selectedCuentaId) ? 'selected' : ''}>
          ID #${escapeHtml(c.cuentaId)} · ${escapeHtml(maskAccount(c.numeroCuenta))} (${escapeHtml(c.estatus)})
        </option>`,
      )
      .join('');
    appEl.innerHTML = `
      <section class="form-shell stack-lg" aria-labelledby="purchase-heading">
        <div class="card stack-md">
          <h1 id="purchase-heading">Preparar pago</h1>
          <p class="help-text">${escapeHtml(p.producto)} · ${escapeHtml(p.servicio)}</p>
          ${ps.localValidationError ? renderBanner({ tone: 'rejected', title: 'Revisa los datos capturados', description: ps.localValidationError }) : ''}
          ${ps.error ? renderBanner(ps.error) : ''}
          <form id="purchase-form" class="stack-md">
            <div class="field-group">
              <label class="field-label" for="purchase-account">Cuenta propia (ID servidor)</label>
              <select id="purchase-account" name="cuentaId" class="input-control">${cuentaOptions}</select>
            </div>
            ${ps.saldoFinanciero ? `<div class="summary-box"><span class="help-text">Disponible en servidor:</span> <strong>${escapeHtml(formatMxn(ps.saldoFinanciero.disponible))}</strong></div>` : ''}
            <div class="field-group">
              <label class="field-label" for="purchase-mode">Tipo de destino</label>
              <select id="purchase-mode" name="destinoModo" class="input-control">
                <option value="telefono">Teléfono (10 dígitos)</option>
                <option value="referencia">Referencia de servicio</option>
                <option value="none">Sin destino</option>
              </select>
            </div>
            <div class="field-group">
              <label class="field-label" for="purchase-dest">Teléfono / Referencia</label>
              <input id="purchase-dest" name="destinoRaw" type="text" maxlength="100" class="input-control" />
            </div>
            <div class="field-group">
              <label class="field-label" for="purchase-amount">Importe (MXN)</label>
              <input id="purchase-amount" name="montoRaw" type="text" class="input-control"
                value="${escapeHtml(fixedAmount)}" ${fixedAmount ? 'readonly' : ''} />
            </div>
            <button type="submit" class="btn btn-primary btn-full"
              ${ps.isSubmitting || !controller.isOnline ? 'disabled' : ''}>
              Prevalidar y revisar importe
            </button>
          </form>
        </div>
      </section>
    `;
    doc.getElementById('purchase-account')?.addEventListener('change', (e) => controller.seleccionarCuentaCompra(e.target.value));
    doc.getElementById('purchase-form')?.addEventListener('submit', (e) => {
      e.preventDefault();
      const fd = new FormData(e.currentTarget);
      controller.prevalidarYRevisar({
        montoRaw: fd.get('montoRaw'),
        destinoModo: fd.get('destinoModo'),
        destinoRaw: fd.get('destinoRaw'),
      });
    });
  }

  function renderHistory() {
    const hs = controller.history;
    let bodyHtml = '';
    if (hs.isLoading && !hs.hasLoaded) {
      bodyHtml = '<div class="card"><p>Cargando historial paginado CAPTURED...</p></div>';
    } else if (hs.error) {
      bodyHtml = `
        <div class="stack-md">
          ${renderBanner(hs.error)}
          <button type="button" id="history-retry-btn" class="btn btn-primary">Reintentar</button>
        </div>
      `;
    } else if (hs.items.length === 0) {
      bodyHtml = `
        <div class="card stack-md">
          <h2>Sin compras capturadas registradas</h2>
          <p class="help-text">Aquí aparecen únicamente tus pagos confirmados en estado CAPTURED.</p>
          <button type="button" id="history-retry-btn" class="btn btn-secondary">Consultar nuevamente</button>
        </div>
      `;
    } else {
      bodyHtml = `
        <div class="grid-cards">
          ${hs.items
            .map(
              (item) => `
            <article class="card stack-sm">
              <div class="actions-wrap" style="justify-content: space-between;">
                <span class="badge badge-success">✓ Capturada</span>
                <span class="help-text">${escapeHtml(formatServerTimestamp(item.capturadoEn || item.creadoEn) || 'Sin fecha')}</span>
              </div>
              <h2>${escapeHtml(item.producto || `Producto #${item.idProducto}`)}</h2>
              <p class="help-text">${escapeHtml(item.servicio || `Servicio #${item.idServicio}`)}</p>
              <div class="amount" style="font-size: 24px; line-height: 32px;">${escapeHtml(formatMxn(item.monto))}</div>
              <div class="summary-box stack-sm">
                <div><span class="help-text">UUID:</span> <span class="folio">${escapeHtml(item.id)}</span></div>
                <div><span class="help-text">UPC:</span> <span class="folio">${escapeHtml(item.referenciaInternaUpc)}</span></div>
                <div><span class="help-text">Folio externo:</span> <span class="folio">${escapeHtml(item.folioExternoDisponible || 'Sin folio externo reportado')}</span></div>
              </div>
              <button type="button" class="btn btn-secondary btn-full" data-history-detail="${escapeHtml(item.id)}">
                Ver comprobante
              </button>
            </article>
          `,
            )
            .join('')}
        </div>
        <div class="card actions-wrap" style="justify-content: space-between;">
          <button type="button" id="history-prev-btn" class="btn btn-secondary" ${hs.page <= 0 ? 'disabled' : ''}>Anterior</button>
          <strong>Página ${hs.page + 1} de ${Math.max(hs.totalPaginas, 1)}</strong>
          <button type="button" id="history-next-btn" class="btn btn-secondary" ${hs.page + 1 >= hs.totalPaginas ? 'disabled' : ''}>Siguiente</button>
        </div>
      `;
    }

    appEl.innerHTML = `
      <section class="stack-lg" aria-labelledby="history-heading">
        <div class="actions-wrap" style="justify-content: space-between;">
          <div>
            <h1 id="history-heading">Historial de pagos aplicados</h1>
            <p class="help-text">Solo operaciones confirmadas (CAPTURED) · Total: ${escapeHtml(hs.totalElementos)}</p>
          </div>
          <button type="button" id="history-refresh-btn" class="btn btn-secondary"
            ${hs.isLoading || !controller.isOnline ? 'disabled' : ''}>Actualizar</button>
        </div>
        ${bodyHtml}
      </section>
    `;
    doc.getElementById('history-refresh-btn')?.addEventListener('click', () => controller.cargarHistorial(hs.page));
    doc.getElementById('history-retry-btn')?.addEventListener('click', () => controller.cargarHistorial(0));
    doc.getElementById('history-prev-btn')?.addEventListener('click', () => controller.cargarHistorial(hs.page - 1));
    doc.getElementById('history-next-btn')?.addEventListener('click', () => controller.cargarHistorial(hs.page + 1));
    appEl.querySelectorAll('[data-history-detail]').forEach((btn) => {
      btn.addEventListener('click', () => controller.abrirDetalleHistorial(btn.dataset.historyDetail));
    });
  }

  function renderModal() {
    if (!modalRoot) return;
    if (!controller.modal) {
      modalRoot.classList.add('hidden');
      modalRoot.innerHTML = '';
      return;
    }
    modalRoot.classList.remove('hidden');

    if (controller.modal.kind === 'catalog-detail' && controller.catalog.selectedProduct) {
      const p = controller.catalog.selectedProduct;
      const canBuy = p.activo && controller.cuentas.some((c) => c.cuentaId > 0 && c.estatus === 'ACTIVA');
      modalRoot.innerHTML = `
        <div class="modal-panel stack-md">
          <div class="actions-wrap" style="justify-content: space-between;">
            <h2 id="modal-title">${escapeHtml(p.producto)}</h2>
            <button type="button" id="modal-close-btn" class="btn btn-secondary" aria-label="Cerrar detalle">Cerrar</button>
          </div>
          <p class="help-text">${escapeHtml(p.servicio)}</p>
          <div class="amount">${p.precio && p.precio > 0 ? escapeHtml(formatMxn(p.precio)) : 'Importe variable'}</div>
          <div class="summary-box">
            <div class="summary-row"><span>ID Producto</span><span class="folio">${escapeHtml(p.idProducto)}</span></div>
            <div class="summary-row"><span>ID Servicio</span><span class="folio">${escapeHtml(p.idServicio ?? 'No especificado')}</span></div>
            <div class="summary-row"><span>Tipo referencia</span><span class="folio">${escapeHtml(p.tipoReferencia || 'No especificada')}</span></div>
          </div>
          <p class="help-text">${escapeHtml(p.legend || 'Sin indicaciones adicionales registradas para este producto.')}</p>
          <button type="button" id="modal-start-purchase-btn" class="btn btn-primary btn-full"
            ${canBuy && controller.isOnline ? '' : 'disabled'}>
            ${p.activo ? 'Continuar a compra' : 'Producto no disponible'}
          </button>
        </div>
      `;
      doc.getElementById('modal-close-btn')?.addEventListener('click', () => controller.cerrarModal());
      doc.getElementById('modal-start-purchase-btn')?.addEventListener('click', () => controller.iniciarCompra(p));
      return;
    }

    if (controller.modal.kind === 'history-detail' && controller.history.selectedDetail) {
      const d = controller.history.selectedDetail;
      const folio = d.folioExternoDisponible;
      modalRoot.innerHTML = `
        <div class="modal-panel stack-md">
          <div class="actions-wrap" style="justify-content: space-between;">
            <h2 id="modal-title">Detalle del comprobante</h2>
            <button type="button" id="modal-close-btn" class="btn btn-secondary" aria-label="Cerrar detalle">Cerrar</button>
          </div>
          <p><strong>${escapeHtml(d.producto || `Producto #${d.idProducto}`)}</strong></p>
          <div class="amount">${escapeHtml(formatMxn(d.monto))}</div>
          ${controller.history.detailError ? renderBanner(controller.history.detailError) : ''}
          <div class="summary-box stack-sm">
            <div>
              <span class="help-text">UUID de operación (servidor)</span>
              <div class="folio">${escapeHtml(d.id)}</div>
              <button type="button" id="copy-hist-uuid" class="btn btn-text">Copiar UUID</button>
            </div>
            <div>
              <span class="help-text">Referencia UPC interna</span>
              <div class="folio">${escapeHtml(d.referenciaInternaUpc)}</div>
              <button type="button" id="copy-hist-upc" class="btn btn-text">Copiar UPC</button>
            </div>
            <div>
              <span class="help-text">Folio proveedor externo (id_tx)</span>
              <div class="folio">${escapeHtml(folio || 'Sin folio externo reportado')}</div>
            </div>
            <div class="summary-row">
              <span>Fecha de captura</span>
              <span>${escapeHtml(formatServerTimestamp(d.capturadoEn || d.creadoEn) || 'No disponible')}</span>
            </div>
          </div>
        </div>
      `;
      doc.getElementById('modal-close-btn')?.addEventListener('click', () => controller.cerrarModal());
      doc.getElementById('copy-hist-uuid')?.addEventListener('click', () => copyToClipboard(d.id));
      doc.getElementById('copy-hist-upc')?.addEventListener('click', () => copyToClipboard(d.referenciaInternaUpc));
    }
  }

  controller.subscribe(render);
  render();
  return controller;
}

if (typeof document !== 'undefined' && document.getElementById('gpago-app')) {
  mountGpagoWebApp(document, window);
}
