import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 5173);

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
};

const server = http.createServer((req, res) => {
  const reqUrl = new URL(req.url || '/', `http://127.0.0.1:${port}`);
  let relPath = decodeURIComponent(reqUrl.pathname);
  if (relPath === '/') relPath = '/index.html';

  const normalized = path.normalize(relPath).replace(/^(\.\.[/\\])+/, '');
  const fullPath = path.join(__dirname, normalized);
  if (!fullPath.startsWith(__dirname) || fullPath.includes(`${path.sep}tests${path.sep}`)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('No encontrado');
    return;
  }

  fs.readFile(fullPath, (err, content) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('No encontrado');
      return;
    }
    const ext = path.extname(fullPath);
    const headers = {
      'Content-Type': MIME_TYPES[ext] || 'application/octet-stream',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'no-referrer',
      'Cache-Control': fullPath.endsWith('sw.js') ? 'no-cache' : 'public, max-age=300',
    };
    if (fullPath.endsWith('sw.js')) {
      headers['Service-Worker-Allowed'] = '/';
    }
    res.writeHead(200, headers);
    res.end(content);
  });
});

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  server.listen(port, '127.0.0.1', () => {
    console.log(`GPago Web PWA disponible en http://127.0.0.1:${port}`);
  });
}

export { server };
