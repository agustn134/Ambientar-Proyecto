// Ejecutar: node tests/bruno/verificar-fechas.cjs
// Verifica la función real de la petición Bruno sin llamar al backend ni al proveedor.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const archivo = path.join(__dirname, 'GestoPago', 'Sincronizacion forzada', 'Productos despues de sincronizar.bru');
const contenido = fs.readFileSync(archivo, 'utf8');
const bloque = contenido.match(/^tests \{\r?\n([\s\S]*?)^\}/m);
assert.ok(bloque, 'La petición debe contener un bloque de pruebas');
assert.doesNotMatch(bloque[1], /\b\d+n\b|\bBigInt\s*\(/, 'El analizador de Bruno no admite literales BigInt');
const contexto = vm.createContext({test() {}});
vm.runInContext(bloque[1], contexto, {filename: archivo});
const normalizar = contexto.fechaEnMicrosegundos;

assert.equal(normalizar('2026-10-06T13:26:43.1293513'), normalizar('2026-10-06T13:26:43.129351'));
assert.equal(normalizar('2026-10-06T13:26:43.1293518'), normalizar('2026-10-06T13:26:43.129352'));
assert.equal(normalizar('2026-10-06T13:26:43.9999998'), normalizar('2026-10-06T13:26:44'));
assert.equal(normalizar('2026-10-06T23:59:59.9999998'), normalizar('2026-10-07T00:00:00'));
assert.equal(normalizar('2026-10-06T13:26:43.1'), normalizar('2026-10-06T13:26:43.100000'));
assert.notEqual(normalizar('2026-10-06T13:26:43.129351'), normalizar('2026-10-06T13:26:43.129352'));
assert.notEqual(normalizar('2026-10-06T13:26:43.129351'), normalizar('2026-10-06T13:26:44.129351'));
assert.throws(() => normalizar('fecha inválida'));
console.log('Comparación de fechas Bruno: 8 comprobaciones aprobadas.');
