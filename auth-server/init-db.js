/**
 * Script d'initialisation de la base de données MySQL pour BlocaQoL
 * Utilise config.json OU variables d'environnement
 */

const mysql = require('mysql2/promise');
const { loadConfig } = require('./config-loader');

async function init() {
  const config = loadConfig();

  if (!config.mysql.password) {
    console.error('Erreur: MYSQL_PASSWORD ou config.mysql.password requis');
    process.exit(1);
  }

  const conn = await mysql.createConnection({
    host: config.mysql.host,
    port: config.mysql.port || 3306,
    user: config.mysql.user,
    password: config.mysql.password,
    multipleStatements: true
  });

  await conn.query(`CREATE DATABASE IF NOT EXISTS ${config.mysql.database}`);
  await conn.query(`USE ${config.mysql.database}`);

  await conn.query(`
    CREATE TABLE IF NOT EXISTS users (
      id INT AUTO_INCREMENT PRIMARY KEY,
      username VARCHAR(64) NOT NULL UNIQUE,
      password_hash VARCHAR(255) NOT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
  `);

  console.log('Base de données initialisée avec succès !');
  await conn.end();
}

init().catch(console.error);
