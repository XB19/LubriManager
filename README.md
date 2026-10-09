# LubriManager

Application de gestion des cartes prépayées, recharges, ventes de lubrifiants et stock
pour un distributeur au Togo (FCFA / XOF, TMoney & Flooz).

- **Poste de travail** : application desktop (React + TypeScript, empaquetée avec Tauri).
- **Serveur** : API REST Spring Boot — c'est lui qui fait autorité sur les soldes, le stock
  et l'historique. Toutes les règles métier et les permissions sont vérifiées côté serveur.
- **Base de données** : PostgreSQL, migrations gérées par Flyway.

## Principes non négociables

1. **Intégrité financière** — aucun solde modifié sans écriture au grand livre ; opérations
   critiques atomiques ; verrouillage pessimiste sur les comptes ; idempotence sur recharges
   et ventes.
2. **Traçabilité totale** — aucune transaction validée n'est supprimée ; une correction crée
   une contre-passation datée et motivée ; journal d'audit horodaté.
3. **Ne jamais faire confiance au frontend** — droits et règles revérifiés dans l'API.

## Structure du dépôt

| Dossier    | Contenu |
|------------|---------|
| `backend/` | API Spring Boot 3.3 (Java 17), sécurité JWT, JPA/Hibernate, Flyway `V1`→`V12` |
| `desktop/` | Interface React 18 + Vite + Mantine ; scaffold Tauri 2 dans `src-tauri/` |
| `docs/`    | Document d'architecture et support de présentation (HTML) |
| `docker-compose.yml` | Pile complète conteneurisée (PostgreSQL + API + interface web) — voir [Docker](#docker) |

## Démarrer en local

### Prérequis
- JDK 17
- Node.js 20+ et npm
- PostgreSQL 15+ avec une base `lubrimanager`

### Backend
```bash
cd backend
# Variables d'environnement (valeurs par défaut de dev entre parenthèses) :
#   DB_URL (jdbc:postgresql://localhost:5432/lubrimanager)
#   DB_USER (lubrimanager)   DB_PASSWORD (lubrimanager)
#   JWT_SECRET (secret de dev ; OBLIGATOIRE en production)
#   ADMIN_PASSWORD (Admin!2026)
./mvnw spring-boot:run
```
API sur `http://localhost:8080` — documentation OpenAPI sur `/swagger-ui`.
Un compte administrateur est créé au démarrage (`admin` / `ADMIN_PASSWORD`).

### Frontend
```bash
cd desktop
cp .env.example .env    # ajuster VITE_API_BASE_URL si besoin
npm install
npm run dev
```
Interface sur `http://localhost:5173`.

## Docker

Fait tourner toute la pile (PostgreSQL + API + interface web) en une commande, sans
rien installer d'autre que Docker. L'interface desktop Tauri (empaquetage natif) reste
un sujet à part — ceci lance la version web de l'interface, servie par nginx.

```bash
cp .env.example .env     # puis éditez .env : remplacez TOUTES les valeurs
docker compose up -d --build
```

- Interface : `http://localhost:8081`
- API : `http://localhost:8080` (doc OpenAPI sur `/swagger-ui`)
- PostgreSQL exposé sur `localhost:5433` (pour ne pas entrer en conflit avec un
  PostgreSQL déjà installé en local sur le port 5432 par défaut)

Le conteneur `backend` tourne avec `SPRING_PROFILES_ACTIVE=prod` : aucune valeur
sensible n'a de défaut, tout vient de `.env`. Changez le mot de passe du compte
`admin` dès la première connexion.

```bash
docker compose logs -f backend   # suivre les logs
docker compose down              # arrêter (garde les données PostgreSQL)
docker compose down -v           # arrêter et supprimer aussi les données
```

## Configuration de production

Activer le profil `prod` (`SPRING_PROFILES_ACTIVE=prod`) : toutes les valeurs sensibles
(`DB_*`, `JWT_SECRET`, `ADMIN_PASSWORD`) doivent alors provenir de l'environnement, sans
valeur par défaut. Voir `backend/src/main/resources/application-prod.yml`.
