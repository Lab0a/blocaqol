# Déploiement VPS - BlocaQoL Auth

## Méthode rapide (Docker)

Sur votre VPS (Ubuntu/Debian) :

```bash
# 1. Installer Docker si besoin
curl -fsSL https://get.docker.com | sh

# 2. Cloner ou copier le dossier auth-server
cd auth-server

# 3. Créer .env avec mot de passe + clé admin
echo "MYSQL_PASSWORD=$(openssl rand -base64 24)" > .env
echo "MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24)" >> .env
echo "ADMIN_SECRET=$(openssl rand -hex 16)" >> .env

# 4. Lancer
docker compose up -d

# 5. Générer un code d'inscription (admin)
# Ouvrir http://IP_VPS:3000/admin → entrer ADMIN_SECRET → Générer un code
# Donner ce code aux personnes que tu autorises à s'inscrire

# 6. Créer un compte
# Option A : Page web → http://IP_VPS:3000/ (code + username + password)
# Option B : curl (avec code)
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"votrepseudo","password":"VotreMotDePasse123","code":"ABC12345"}'
```

## Ouvrir le port

```bash
sudo ufw allow 3000
sudo ufw reload
```

## Config du mod

Dans `config/blocaqol/config.json` :
```json
{
  "authApiUrl": "http://62.210.244.167:3000",
  "authEnabled": true
}
```

Remplacez `IP_VOTRE_VPS` par l'IP publique de votre serveur.

## HTTPS (optionnel mais recommandé)

Avec Nginx + Let's Encrypt :

```nginx
# /etc/nginx/sites-available/blocaqol
server {
    listen 443 ssl;
    server_name auth.votredomaine.com;
    ssl_certificate /etc/letsencrypt/live/auth.votredomaine.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/auth.votredomaine.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Puis dans le mod : `authApiUrl: "https://auth.votredomaine.com"`
