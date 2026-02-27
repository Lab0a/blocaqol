# Pousser vers GitHub

## Prérequis
- [Git](https://git-scm.com/download/win) installé
- Compte GitHub configuré

## Commandes (dans le dossier mods)

```bash
cd C:\Users\NslBr\Desktop\mods

# Initialiser Git (si pas déjà fait)
git init

# Ajouter le dépôt distant
git remote add origin https://github.com/Lab0a/blocaqol.git

# Ou si origin existe déjà :
git remote set-url origin https://github.com/Lab0a/blocaqol.git

# Tout ajouter, committer, pousser
git add .
git commit -m "BlocaQoL mod + auth-server avec page web inscription"
git branch -M main
git push -u origin main
```

## Sur le VPS ensuite

```bash
git clone https://github.com/Lab0a/blocaqol.git
cd blocaqol/auth-server
docker compose up -d --build
```
