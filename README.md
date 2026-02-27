# Mod Minecraft 1.21.8 (Fabric)

Mod Fabric pour Minecraft 1.21.8.

## Prérequis

- **Java 21** ou supérieur (téléchargez le JDK depuis [adoptium.net](https://adoptium.net/))
- Assurez-vous que `java` est dans votre PATH ou définissez `JAVA_HOME`
- **Minecraft 1.21.8** avec **Fabric Loader** et **Fabric API**

## Développement

> **Note :** Si `gradlew` ne fonctionne pas (erreur avec gradle-wrapper.jar), installez [Gradle](https://gradle.org/install/) puis exécutez `gradle wrapper` dans le dossier du projet pour régénérer le wrapper.

### Compilation

```bash
.\gradlew build
```

Le fichier JAR du mod sera généré dans `build/libs/`.

### Lancer Minecraft en mode développement

```bash
.\gradlew runClient
```

### Générer les sources

```bash
.\gradlew genSources
```

## Structure du projet

- `src/main/java/` - Code commun (serveur + client)
- `src/client/java/` - Code spécifique au client
- `src/main/resources/` - Ressources (fabric.mod.json, etc.)

## Configuration

Modifiez `gradle.properties` pour changer :
- `archives_base_name` - Nom du fichier JAR
- `maven_group` - Package de base (ex: com.monmod)
- `mod_version` - Version du mod

Modifiez `src/main/resources/fabric.mod.json` pour :
- `id` - Identifiant unique du mod
- `name` - Nom affiché
- `description` - Description du mod
