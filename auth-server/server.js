/**
 * Serveur d'authentification BlocaQoL
 * API REST pour login avec MySQL
 * Support: config.json OU variables d'environnement (idéal pour VPS)
 */

const path = require('path');
const fs = require('fs');
const express = require('express');
const cors = require('cors');
const bcrypt = require('bcrypt');
const mysql = require('mysql2/promise');
const { loadConfig } = require('./config-loader');

const config = loadConfig();

// Debug en Docker (host utilisé pour MySQL)
if (process.env.MYSQL_HOST) {
  console.log('MySQL config:', config.mysql.host + ':' + config.mysql.port);
}

if (!config.mysql.password) {
  console.error('Erreur: MYSQL_PASSWORD ou config.mysql.password requis');
  process.exit(1);
}

const pool = mysql.createPool({
  host: config.mysql.host,
  port: config.mysql.port || 3306,
  user: config.mysql.user,
  password: config.mysql.password,
  database: config.mysql.database,
  waitForConnections: true,
  connectionLimit: 10
});

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

// Joueurs connectés (userId -> lastSeen timestamp). TTL 3 min.
const connectedUsers = new Map();
const CONNECTED_TTL_MS = 3 * 60 * 1000;

function markConnected(userId, username) {
  connectedUsers.set(String(userId), { username, lastSeen: Date.now() });
}

function getConnectedPlayers() {
  const now = Date.now();
  const list = [];
  for (const data of connectedUsers.values()) {
    if (now - data.lastSeen < CONNECTED_TTL_MS) list.push(data.username);
  }
  return list;
}

function disconnectUser(userId) {
  connectedUsers.delete(String(userId));
}

function parseToken(authHeader) {
  if (!authHeader || !authHeader.startsWith('Bearer ')) return null;
  try {
    const token = authHeader.slice(7);
    const decoded = Buffer.from(token, 'base64').toString();
    const [userId] = decoded.split(':');
    return userId ? { userId } : null;
  } catch (e) {
    return null;
  }
}

// Attendre MySQL (utile en Docker)
async function waitForMysql(maxAttempts = 90) {
  for (let i = 0; i < maxAttempts; i++) {
    try {
      await pool.execute('SELECT 1');
      return;
    } catch (e) {
      if (i === 0) console.log('En attente de MySQL...');
      if (i === maxAttempts - 1) {
        console.error('Dernière erreur MySQL:', e.message);
        throw new Error('MySQL non disponible: ' + e.message);
      }
      await sleep(1000);
    }
  }
}

// Créer les tables si elles n'existent pas
async function ensureTables() {
  try {
    await pool.execute(`
      CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(64) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        allow_autofish TINYINT(1) DEFAULT 1,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
    try {
      await pool.execute('ALTER TABLE users ADD COLUMN allow_autofish TINYINT(1) DEFAULT 1');
    } catch (e) {
      if (e.code !== 'ER_DUP_FIELDNAME') console.warn('Migration allow_autofish:', e.message);
    }
    await pool.execute(`
      CREATE TABLE IF NOT EXISTS registration_codes (
        id INT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(32) NOT NULL UNIQUE,
        allow_autofish TINYINT(1) DEFAULT 1,
        used_at TIMESTAMP NULL,
        used_by INT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
    try {
      await pool.execute('ALTER TABLE registration_codes ADD COLUMN allow_autofish TINYINT(1) DEFAULT 1');
    } catch (e) {
      if (e.code !== 'ER_DUP_FIELDNAME') console.warn('Migration codes allow_autofish:', e.message);
    }
    try {
      await pool.execute("CREATE TABLE IF NOT EXISTS _migrations (name VARCHAR(64) PRIMARY KEY)");
      const [done] = await pool.execute("SELECT 1 FROM _migrations WHERE name = 'allow_autofish_default'");
      if (done.length === 0) {
        await pool.execute('UPDATE users SET allow_autofish = 1 WHERE allow_autofish IS NULL OR allow_autofish = 0');
        await pool.execute("INSERT INTO _migrations (name) VALUES ('allow_autofish_default')");
        console.log('Migration: allow_autofish mis à 1 pour les utilisateurs existants');
      }
    } catch (e) {
      console.warn('Migration allow_autofish:', e.message);
    }
  } catch (e) {
    console.warn('Tables:', e.message);
  }
}

function randomCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let s = '';
  for (let i = 0; i < 8; i++) s += chars[Math.floor(Math.random() * chars.length)];
  return s;
}

const app = express();
app.use(cors());
app.use(express.json());

// Page web d'inscription (http://IP:3000/)
const publicDir = path.join(__dirname, 'public');
const indexPath = path.join(publicDir, 'index.html');
app.use(express.static(publicDir));
app.get('/', (req, res) => {
  if (fs.existsSync(indexPath)) {
    res.sendFile(indexPath);
  } else {
    res.status(404).send('Page d\'inscription non disponible. Reconstruire l\'image Docker: docker compose up -d --build');
  }
});
app.get('/admin', (req, res) => {
  const adminPath = path.join(publicDir, 'admin.html');
  if (fs.existsSync(adminPath)) {
    res.sendFile(adminPath);
  } else {
    res.status(404).send('Page admin non disponible');
  }
});

// Health check (pour VPS/monitoring)
app.get('/health', (req, res) => res.json({ ok: true }));

// POST /auth/login - Connexion
app.post('/auth/login', async (req, res) => {
  try {
    const { username, password } = req.body;
    if (!username || !password) {
      return res.status(400).json({ success: false, error: 'Username et password requis' });
    }

    const [rows] = await pool.execute(
      'SELECT id, username, password_hash, COALESCE(allow_autofish, 1) as allow_autofish FROM users WHERE username = ?',
      [username]
    );

    if (rows.length === 0) {
      return res.status(401).json({ success: false, error: 'Identifiants incorrects' });
    }

    const user = rows[0];
    const valid = await bcrypt.compare(password, user.password_hash);
    if (!valid) {
      return res.status(401).json({ success: false, error: 'Identifiants incorrects' });
    }

    markConnected(user.id, user.username);
    res.json({
      success: true,
      token: Buffer.from(`${user.id}:${Date.now()}`).toString('base64'),
      username: user.username,
      allowAutofish: !!user.allow_autofish,
      connectedPlayers: getConnectedPlayers()
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: 'Erreur serveur' });
  }
});

// POST /auth/logout - Déconnexion (retire de la liste des connectés)
app.post('/auth/logout', (req, res) => {
  const parsed = parseToken(req.headers.authorization);
  if (parsed) {
    disconnectUser(parsed.userId);
  }
  res.json({ success: true });
});

// GET /auth/connected - Liste des joueurs connectés (Bearer token requis)
app.get('/auth/connected', async (req, res) => {
  try {
    const parsed = parseToken(req.headers.authorization);
    if (!parsed) {
      return res.status(401).json({ error: 'Token requis' });
    }
    const [rows] = await pool.execute('SELECT id, username, COALESCE(allow_autofish, 1) as allow_autofish FROM users WHERE id = ?', [parsed.userId]);
    if (!rows.length) {
      return res.status(401).json({ error: 'Token invalide' });
    }
    markConnected(rows[0].id, rows[0].username);
    res.json({
      connectedPlayers: getConnectedPlayers(),
      allowAutofish: !!rows[0].allow_autofish
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

// POST /auth/verify - Vérifier un token
app.post('/auth/verify', async (req, res) => {
  try {
    const { token } = req.body;
    if (!token) {
      return res.status(400).json({ valid: false });
    }

    const decoded = Buffer.from(token, 'base64').toString();
    const [userId] = decoded.split(':');
    const [rows] = await pool.execute('SELECT id FROM users WHERE id = ?', [userId]);

    res.json({ valid: rows.length > 0 });
  } catch (err) {
    res.json({ valid: false });
  }
});

// POST /auth/register - Inscription (code requis)
app.post('/auth/register', async (req, res) => {
  try {
    const { username, password, code } = req.body;
    if (!username || !password || !code) {
      return res.status(400).json({ success: false, error: 'Username, password et code d\'inscription requis' });
    }
    if (username.length < 3 || password.length < 6) {
      return res.status(400).json({ success: false, error: 'Username (3+ car) et password (6+ car) requis' });
    }

    const codeUpper = code.trim().toUpperCase();
    const [codeRows] = await pool.execute(
      'SELECT id, COALESCE(allow_autofish, 1) as allow_autofish FROM registration_codes WHERE code = ? AND used_at IS NULL',
      [codeUpper]
    );
    if (codeRows.length === 0) {
      return res.status(400).json({ success: false, error: 'Code d\'inscription invalide ou déjà utilisé' });
    }

    const allowAutofish = !!codeRows[0].allow_autofish;
    const hash = await bcrypt.hash(password, 10);
    const [result] = await pool.execute(
      'INSERT INTO users (username, password_hash, allow_autofish) VALUES (?, ?, ?)',
      [username, hash, allowAutofish ? 1 : 0]
    );
    const userId = result.insertId;
    await pool.execute(
      'UPDATE registration_codes SET used_at = NOW(), used_by = ? WHERE code = ?',
      [userId, codeUpper]
    );

    res.json({ success: true, message: 'Compte créé' });
  } catch (err) {
    if (err.code === 'ER_DUP_ENTRY') {
      return res.status(400).json({ success: false, error: 'Username déjà utilisé' });
    }
    console.error(err);
    res.status(500).json({ success: false, error: 'Erreur serveur' });
  }
});

// Middleware admin (secret dans header X-Admin-Secret)
function requireAdmin(req, res, next) {
  const secret = req.headers['x-admin-secret'] || req.body?.adminSecret;
  if (!config.adminSecret || secret !== config.adminSecret) {
    return res.status(401).json({ error: 'Accès refusé' });
  }
  next();
}

// POST /admin/generate-code - Générer un code (admin uniquement)
// Body: { allowAutofish: true/false } — définit si l'utilisateur inscrit avec ce code aura l'auto-pêche
app.post('/admin/generate-code', requireAdmin, async (req, res) => {
  try {
    const allowAutofish = req.body?.allowAutofish !== false;
    let code = randomCode();
    for (let i = 0; i < 10; i++) {
      const [rows] = await pool.execute('SELECT id FROM registration_codes WHERE code = ?', [code]);
      if (rows.length === 0) break;
      code = randomCode();
    }
    await pool.execute('INSERT INTO registration_codes (code, allow_autofish) VALUES (?, ?)', [code, allowAutofish ? 1 : 0]);
    res.json({ success: true, code, allowAutofish });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: 'Erreur serveur' });
  }
});

// GET /admin/users - Liste des utilisateurs (admin uniquement)
app.get('/admin/users', requireAdmin, async (req, res) => {
  try {
    const [rows] = await pool.execute('SELECT id, username, COALESCE(allow_autofish, 1) as allow_autofish FROM users ORDER BY username');
    res.json({ users: rows });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

// POST /admin/users/:id/allow-autofish - Activer/désactiver auto-fish (admin uniquement)
app.post('/admin/users/:id/allow-autofish', requireAdmin, async (req, res) => {
  try {
    const userId = parseInt(req.params.id, 10);
    const { allow } = req.body;
    if (isNaN(userId) || typeof allow !== 'boolean') {
      return res.status(400).json({ error: 'Paramètres invalides' });
    }
    await pool.execute('UPDATE users SET allow_autofish = ? WHERE id = ?', [allow ? 1 : 0, userId]);
    res.json({ success: true, allowAutofish: allow });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

// GET /admin/codes - Liste des codes (admin uniquement)
app.get('/admin/codes', requireAdmin, async (req, res) => {
  try {
    const [rows] = await pool.execute(
      'SELECT c.code, c.allow_autofish, c.created_at, c.used_at, u.username as used_by FROM registration_codes c LEFT JOIN users u ON c.used_by = u.id ORDER BY c.created_at DESC LIMIT 50'
    );
    res.json({ codes: rows });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Erreur serveur' });
  }
});

waitForMysql()
  .then(ensureTables)
  .then(() => {
  app.listen(config.port, '0.0.0.0', () => {
    console.log(`BlocaQoL Auth Server sur http://0.0.0.0:${config.port}`);
  });
}).catch(err => {
  console.error('Erreur démarrage:', err);
  process.exit(1);
});
