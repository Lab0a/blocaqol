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
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
    await pool.execute(`
      CREATE TABLE IF NOT EXISTS registration_codes (
        id INT AUTO_INCREMENT PRIMARY KEY,
        code VARCHAR(32) NOT NULL UNIQUE,
        used_at TIMESTAMP NULL,
        used_by INT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
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
      'SELECT id, username, password_hash FROM users WHERE username = ?',
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

    res.json({
      success: true,
      token: Buffer.from(`${user.id}:${Date.now()}`).toString('base64'),
      username: user.username
    });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: 'Erreur serveur' });
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
      'SELECT id FROM registration_codes WHERE code = ? AND used_at IS NULL',
      [codeUpper]
    );
    if (codeRows.length === 0) {
      return res.status(400).json({ success: false, error: 'Code d\'inscription invalide ou déjà utilisé' });
    }

    const hash = await bcrypt.hash(password, 10);
    const [result] = await pool.execute(
      'INSERT INTO users (username, password_hash) VALUES (?, ?)',
      [username, hash]
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
app.post('/admin/generate-code', requireAdmin, async (req, res) => {
  try {
    let code = randomCode();
    for (let i = 0; i < 10; i++) {
      const [rows] = await pool.execute('SELECT id FROM registration_codes WHERE code = ?', [code]);
      if (rows.length === 0) break;
      code = randomCode();
    }
    await pool.execute('INSERT INTO registration_codes (code) VALUES (?)', [code]);
    res.json({ success: true, code });
  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, error: 'Erreur serveur' });
  }
});

// GET /admin/codes - Liste des codes (admin uniquement)
app.get('/admin/codes', requireAdmin, async (req, res) => {
  try {
    const [rows] = await pool.execute(
      'SELECT c.code, c.created_at, c.used_at, u.username as used_by FROM registration_codes c LEFT JOIN users u ON c.used_by = u.id ORDER BY c.created_at DESC LIMIT 50'
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
