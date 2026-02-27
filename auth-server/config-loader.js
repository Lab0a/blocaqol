/**
 * Charge la config depuis config.json OU les variables d'environnement.
 * Priorité : variables d'environnement > config.json
 */

const fs = require('fs');
const path = require('path');

function loadConfig() {
  let config = {};

  // 1. Charger config.json si existant
  try {
    const configPath = path.join(__dirname, 'config.json');
    if (fs.existsSync(configPath)) {
      config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    }
  } catch (e) {
    console.warn('config.json ignoré:', e.message);
  }

  // 2. Variables d'environnement (prioritaires pour VPS)
  return {
    port: process.env.PORT || config.port || 3000,
    adminSecret: process.env.ADMIN_SECRET || config.adminSecret || '',
    mysql: {
      host: process.env.MYSQL_HOST || config.mysql?.host || 'localhost',
      port: parseInt(process.env.MYSQL_PORT || config.mysql?.port || '3306'),
      user: process.env.MYSQL_USER || config.mysql?.user || 'blocaqol',
      password: process.env.MYSQL_PASSWORD || config.mysql?.password || '',
      database: process.env.MYSQL_DATABASE || config.mysql?.database || 'blocaqol_auth'
    }
  };
}

module.exports = { loadConfig };
