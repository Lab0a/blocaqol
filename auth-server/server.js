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

// Créer la table users si elle n'existe pas (pratique pour Docker)
async function ensureTable() {
  try {
    await pool.execute(`
      CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(64) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    `);
  } catch (e) {
    console.warn('Table users:', e.message);
  }
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

// POST /auth/register - Inscription
app.post('/auth/register', async (req, res) => {
  try {
    const { username, password } = req.body;
    if (!username || !password || username.length < 3 || password.length < 6) {
      return res.status(400).json({ success: false, error: 'Username (3+ car) et password (6+ car) requis' });
    }

    const hash = await bcrypt.hash(password, 10);
    await pool.execute(
      'INSERT INTO users (username, password_hash) VALUES (?, ?)',
      [username, hash]
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

waitForMysql()
  .then(ensureTable)
  .then(() => {
  app.listen(config.port, '0.0.0.0', () => {
    console.log(`BlocaQoL Auth Server sur http://0.0.0.0:${config.port}`);
  });
}).catch(err => {
  console.error('Erreur démarrage:', err);
  process.exit(1);
});
