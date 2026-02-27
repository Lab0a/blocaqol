// PM2 - garde le serveur actif sur le VPS
// Usage: pm2 start ecosystem.config.cjs

module.exports = {
  apps: [{
    name: 'blocaqol-auth',
    script: 'server.js',
    cwd: __dirname,
    instances: 1,
    autorestart: true,
    watch: false,
    env: {
      NODE_ENV: 'production'
    }
  }]
};
