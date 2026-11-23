import 'dart:convert';
import 'package:flutter/services.dart';
import '../models/auth_models.dart';

abstract interface class SessionStorage {
  Future<LoginTokensDto?> read();
  Future<void> save(LoginTokensDto session);
  Future<void> clear();
}

/// Almacenamiento en memoria volátil para pruebas y fallback cuando no hay keystore nativo.
class InMemorySessionStorage implements SessionStorage {
  InMemorySessionStorage([this._stored]);

  LoginTokensDto? _stored;

  @override
  Future<LoginTokensDto?> read() async => _stored;

  @override
  Future<void> save(LoginTokensDto session) async {
    _stored = session;
  }

  @override
  Future<void> clear() async {
    _stored = null;
  }
}

/// Delega en el canal seguro nativo (Android Keystore / EncryptedSharedPreferences)
/// y mantiene respaldo volátil en memoria sin escribir tokens en texto plano en disco.
class PlatformSecureSessionStorage implements SessionStorage {
  PlatformSecureSessionStorage({
    MethodChannel? channel,
    SessionStorage? fallback,
  }) : _channel = channel ?? const MethodChannel('mx.gpago.app/secure_session'),
       _fallback = fallback ?? InMemorySessionStorage();

  static const _storageKey = 'gpago_active_session_v1';
  final MethodChannel _channel;
  final SessionStorage _fallback;

  @override
  Future<LoginTokensDto?> read() async {
    try {
      final raw = await _channel.invokeMethod<String>('read', {
        'key': _storageKey,
      });
      if (raw == null || raw.isEmpty) {
        return await _fallback.read();
      }
      final map = jsonDecode(raw) as Map<String, dynamic>;
      return LoginTokensDto.fromJson(map);
    } on MissingPluginException {
      return _fallback.read();
    } on PlatformException {
      return _fallback.read();
    } on FormatException {
      await clear();
      return null;
    }
  }

  @override
  Future<void> save(LoginTokensDto session) async {
    await _fallback.save(session);
    try {
      await _channel.invokeMethod<void>('write', {
        'key': _storageKey,
        'value': jsonEncode(session.toJson()),
      });
    } on MissingPluginException {
      // Entorno sin plugin nativo (ej. pruebas unitarias / escritorio).
    } on PlatformException {
      // Mantiene la sesión únicamente en memoria volátil.
    }
  }

  @override
  Future<void> clear() async {
    await _fallback.clear();
    try {
      await _channel.invokeMethod<void>('delete', {'key': _storageKey});
    } on MissingPluginException {
      // No-op en pruebas/escritorio.
    } on PlatformException {
      // No-op si el canal nativo no está disponible.
    }
  }
}
