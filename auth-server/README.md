# BlocaQoL - Serveur d'authentification

API d'auth pour le mod BlocaQoL avec MySQL.

---

## Option 1 : Docker (le plus simple pour VPS)

**Une seule commande** - MySQL + API inclus :

```bash
# Créer .env avec vos mots de passe
echo "MYSQL_PASSWORD=VotreMotDePasseSecurise" > .env
echo "MYSQL_ROOT_PASSWORD=AutreMotDePasse" >> .env

# Lancer tout
docker compose up -d

# Créer un compte
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"votremdp123"}'
```

L'API est sur le port 3000. Sur un VPS, ouvrez le firewall (ufw allow 3000).

**Config du mod** : `authApiUrl: "http://IP_DE_VOTRE_VPS:3000"`

---

## Option 2 : Variables d'environnement (VPS avec MySQL existant)

Si vous avez déjà MySQL sur votre VPS :

```bash
# Exporter les variables
export PORT=3000
export MYSQL_HOST=localhost
export MYSQL_USER=blocaqol
export MYSQL_PASSWORD=votremdp
export MYSQL_DATABASE=blocaqol_auth

# Init DB + lancer
npm install
npm run init-db
npm start
```

Ou créer un fichier `.env` (avec dotenv) - ou utiliser PM2 :

```bash
npm install -g pm2
MYSQL_PASSWORD=votremdp pm2 start ecosystem.config.cjs
pm2 save
pm2 startup  # pour redémarrage auto
```

---

## Option 3 : config.json (local)

```bash
cp config.example.json config.json
# Éditer config.json
npm install
npm run init-db
npm start
```

---

## Endpoints

| Méthode | URL | Description |
|---------|-----|-------------|
| GET | /health | Vérifier que l'API répond |
| POST | /auth/login | Connexion |
| POST | /auth/register | Créer un compte |
| POST | /auth/verify | Vérifier un token |

---

## VPS - Checklist

1. **Ouvrir le port** : `ufw allow 3000 && ufw reload`
2. **HTTPS** (recommandé) : mettre Nginx en reverse proxy devant l'API
3. **Config mod** : `authApiUrl: "https://auth.votredomaine.com"` (si HTTPS)
