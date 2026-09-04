# LubriManager — Backend (Phases 2–10)

API Spring Boot 3 / Java 17.

- **Phase 2 — Authentification :** utilisateurs, rôles, connexion JWT, refresh token avec
  rotation et détection de rejeu, protection des endpoints par rôle, gestion centralisée
  des erreurs, documentation OpenAPI.
- **Phase 3 — Clients :** entité `Customer`, n° client auto (`CLI-000001`), CRUD,
  recherche globale (n°, nom, raison sociale, téléphone, e-mail) + filtres type / statut,
  changement de statut. Générateur de références réutilisable (`reference_counter`).
- **Phase 4 — Comptes & cartes :** `Account` (1 par client, ouvert avec le client,
  `solde ≥ 0`, jamais de découvert), `Card` (n° auto `CARD-000001` + n° externe optionnel,
  plusieurs cartes actives par compte, expiration facultative), machine à états des cartes
  et tâche nocturne d'expiration.
- **Phase 5 — Recharges :** grand livre `account_transaction` (ajout seul), `Recharge`,
  `payment_method` (6 seeds), référence `RECH-20260904-000001`. **Intégrité financière** :
  opération atomique, verrou pessimiste sur le compte, en-tête `Idempotency-Key`
  obligatoire (garde anti-doublon en base). Au-delà d'un seuil (DEC-03) la recharge passe
  `EN_ATTENTE` d'une validation MANAGER ; le solde ne bouge qu'à la validation.
  Consultation du grand livre : `GET /api/transactions`.
- **Phase 6 — Produits & catégories :** `Category` (6 seeds : huile moteur, hydraulique…),
  `Product` (référence auto `PROD-000001`, marque, unité, **prix TTC** en FCFA entiers,
  `vatRate` défaut 18 %, seuil d'alerte). DEC-04 : prix saisis TTC, `priceExclTax` /
  `taxAmount` calculés pour info.
- **Phase 7 — Stock :** `inventory_movement` **en ajout seul** (ENTREE / AJUSTEMENT /
  INVENTAIRE manuels ; SORTIE réservée aux ventes). `product.stock_quantity` devient un
  cache mis à jour **atomiquement** avec le mouvement (verrou pessimiste sur le produit,
  invariant `stock = Σ deltas`, jamais négatif). `GET /api/inventory/movements`,
  `/low-stock`, `/api/products/{id}/movements`.
- **Phase 8 — Ventes :** `Sale` + `SaleItem` (prix/nom/TVA figés, RG-07), référence
  `VENT-20260904-000001`. `POST /api/sales` : **une transaction atomique** — verrou compte
  puis produits triés par id, contrôles carte ACTIVE / client ACTIF / produit vendable /
  stock suffisant / **solde suffisant** (RG-05, refus ⇒ rien n'est écrit), débit +
  écriture `ACHAT` au grand livre + `SORTIE` de stock par ligne. `Idempotency-Key`
  obligatoire. Consultation : `GET /api/sales`, `/{id}`, `/reference/{ref}`.
- **Phase 9 — Audit & annulation :**
  - `audit_log` **en ajout seul** (`AuditService.record` en propagation `REQUIRED` :
    atomique avec l'opération financière, mais dans sa propre transaction hors flux —
    connexion, **vente refusée** DEC-05). Tracé : connexions, recharges, ventes,
    annulations, ajustements, statuts carte / client, mouvements de stock. IP et agent
    capturés par filtre. `GET /api/audit` (ADMIN · MANAGER · AUDITEUR).
  - **Annulation / contre-passation** `POST /api/transactions/{id}/reversal` (MANAGER) —
    l'originale passe `ANNULEE`, une écriture `ANNULATION` de montant inverse lui est liée,
    motif obligatoire, annulation **totale** (DEC-06), restock des ventes (ENTREE, RG-12).
    Refusée si le solde deviendrait négatif.
  - **Ajustement manuel** `POST /api/accounts/{id}/adjustment` (ADMIN, motif obligatoire,
    RG-13) — écriture `AJUSTEMENT` + audit.
- **Phase 10 — Tableau de bord, rapports, notifications :**
  - `GET /api/dashboard` (indicateurs + agrégats jour/semaine/mois), `/top-products`,
    `/top-customers`, `/user-activity` (ADMIN·MANAGER·AUDITEUR) ; `GET /api/dashboard/me`
    = rapport de caisse du jour de l'agent, ventilé par moyen de paiement (DEC-20).
  - `GET /api/reports/{sales|recharges|transactions|cancellations|stock|audit}?from&to&format=json|csv`
    (ADMIN·MANAGER·AUDITEUR). CSV = BOM UTF-8 + `;` (Excel FR). PDF/XLSX natif = évolution.
  - Notifications applicatives (§34, DEC-25 in-app seulement) : `GET /api/notifications`,
    `/unread-count`, `PATCH /{id}/read`, `POST /read-all`. Générées sur **stock bas**
    (au franchissement du seuil, RG-14) et **recharge en attente**. Diffusion STAFF
    (ADMIN + MANAGER).

> L'architecture vise à terme le JDK 21 LTS ; le code n'utilise aucune API postérieure à
> 17. Pour basculer : `java.version` dans `pom.xml`.

## Prérequis

- JDK 17 (`JAVA_HOME` positionné)
- **Maven n'est pas requis** : le wrapper `./mvnw` le télécharge et le met en cache au premier appel (réseau requis une fois).
- PostgreSQL 16 pour lancer l'application. Les tests d'intégration utilisent Testcontainers (**Docker requis**) ; les tests unitaires non.

## Base de données locale

```sql
CREATE DATABASE lubrimanager;
CREATE USER lubrimanager WITH PASSWORD 'lubrimanager';
GRANT ALL PRIVILEGES ON DATABASE lubrimanager TO lubrimanager;
-- PostgreSQL 15+ : donner aussi les droits sur le schéma public
\c lubrimanager
GRANT ALL ON SCHEMA public TO lubrimanager;
```

Les migrations Flyway (`src/main/resources/db/migration`) créent le schéma au démarrage.
Sinon, surchargez `DB_URL` / `DB_USER` / `DB_PASSWORD` (voir ci-dessous).

## Lancer

```bash
./mvnw spring-boot:run
```

Variables d'environnement (valeurs de dev par défaut sinon) :

| Variable         | Rôle                                   | Défaut (dev)                                    |
|------------------|----------------------------------------|-----------------------------------------------|
| `DB_URL`         | URL JDBC PostgreSQL                    | `jdbc:postgresql://localhost:5432/lubrimanager` |
| `DB_USER`        | Utilisateur BD                         | `lubrimanager`                                 |
| `DB_PASSWORD`    | Mot de passe BD                        | `lubrimanager`                                 |
| `JWT_SECRET`     | Clé de signature HS256 (≥ 32 car.)     | secret de dev **non sécurisé**                 |
| `ADMIN_PASSWORD` | Mot de passe du compte admin initial   | `Admin!2026`                                   |

Au premier démarrage, si aucun ADMIN n'existe, un compte `admin` est créé et rattaché à
l'agence par défaut `MAIN` (DEC-02). **Connectez-vous et changez le mot de passe.**

## Points d'entrée

- Swagger UI : http://localhost:8080/swagger-ui
- OpenAPI JSON : http://localhost:8080/v3/api-docs
- Santé : http://localhost:8080/actuator/health

## Essai rapide

```bash
# 1. Connexion (nom d'utilisateur OU e-mail)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin!2026"}'

# 2. Appel protégé
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"

# 3. Rotation du refresh token
curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'
```

## Tests

```bash
./mvnw test      # tests unitaires — rapides, aucune base (References, JwtService, verrouillage)
./mvnw verify    # + tests d'intégration contre PostgreSQL local (base lubrimanager_test)
```

Convention : `*Test` = unitaire (phase `test`) ; `*IT` = intégration (phase `verify`).

Les tests d'intégration utilisent la base **`lubrimanager_test`** (créée à part) et la
réinitialisent à chaque test (`flyway clean` + `migrate` + recréation de l'admin).
Surchargeables via `TEST_DB_URL` / `TEST_DB_USER` / `TEST_DB_PASSWORD`.
Docker/Testcontainers pourront être réintroduits pour la CI (Phase 11).

> Si l'IDE (serveur de langage Java) a compilé des classes en Java 21 dans `target/`,
> lancez `./mvnw clean` — le build Maven fait foi.

## Décisions appliquées par défaut (modifiables dans `application.yml`)

- `DEC-14` : mot de passe ≥ 10 caractères ; verrouillage 15 min après 5 échecs ; pas de MFA.
- `DEC-15` : access token 15 min, refresh token 7 jours, rotation + détection de rejeu.
- `DEC-02` : colonne `branch_id` présente dès maintenant, agence unique `MAIN`.
- `DEC-21` : messages d'erreur en français, centralisés dans `ErrorCode`.

## Endpoints (Phases 3–4)

| Méthode & chemin                             | Rôle minimal            |
|----------------------------------------------|-------------------------|
| `GET /api/customers?q&type&status&page&size` | authentifié             |
| `GET /api/customers/{id}`                    | authentifié             |
| `POST /api/customers`                        | ADMIN · MANAGER · AGENT |
| `PUT /api/customers/{id}`                    | ADMIN · MANAGER · AGENT |
| `PATCH /api/customers/{id}/status`           | ADMIN · MANAGER         |
| `GET /api/customers/{id}/account`            | authentifié             |
| `GET /api/customers/{id}/cards`              | authentifié             |
| `GET /api/accounts/{id}`                     | authentifié             |
| `GET /api/cards?q&status&customerId&page&size` | authentifié           |
| `GET /api/cards/{id}`                        | authentifié             |
| `POST /api/cards`                            | ADMIN · MANAGER · AGENT |
| `PATCH /api/cards/{id}/status` (ACTIVE/SUSPENDUE/BLOQUEE/PERDUE) | ADMIN · MANAGER · AGENT |
| `POST /api/cards/{id}/deactivate`            | ADMIN · MANAGER         |
| `GET /api/payment-methods`                   | authentifié             |
| `POST /api/recharges` (en-tête `Idempotency-Key`) | ADMIN · MANAGER · AGENT |
| `GET /api/recharges?status&accountId&customerId&cardId&from&to` | authentifié   |
| `GET /api/recharges/{id}`                    | authentifié             |
| `POST /api/recharges/{id}/validate`          | ADMIN · MANAGER         |
| `POST /api/recharges/{id}/reject`            | ADMIN · MANAGER         |
| `GET /api/transactions?type&status&accountId&customerId&cardId&from&to` | authentifié |
| `GET /api/transactions/{id}` · `/reference/{reference}` | authentifié          |
| `GET /api/categories` · `GET /api/categories/{id}`     | authentifié          |
| `POST /api/categories` · `PUT /{id}` · `DELETE /{id}`  | ADMIN · MANAGER      |
| `GET /api/products?q&categoryId&status&lowStock&page&size` | authentifié      |
| `GET /api/products/{id}` · `/reference/{reference}`    | authentifié          |
| `POST /api/products` · `PUT /{id}` · `PATCH /{id}/status` | ADMIN · MANAGER    |
| `POST /api/inventory/movements` (ENTREE/AJUSTEMENT/INVENTAIRE) | ADMIN · MANAGER |
| `GET /api/inventory/movements?productId&type&from&to` · `/{id}` | authentifié   |
| `GET /api/inventory/low-stock` · `GET /api/products/{id}/movements` | authentifié |
| `POST /api/sales` (en-tête `Idempotency-Key`)          | ADMIN · MANAGER · AGENT |
| `GET /api/sales?customerId&cardId&from&to` · `/{id}` · `/reference/{ref}` | authentifié |
| `POST /api/transactions/{id}/reversal` (motif obligatoire) | ADMIN · MANAGER    |
| `POST /api/accounts/{id}/adjustment` (motif obligatoire) | ADMIN               |
| `GET /api/audit?action&entityType&entityId&userId&from&to` | ADMIN · MANAGER · AUDITEUR |
| `GET /api/dashboard` · `/top-products` · `/top-customers` · `/user-activity` | ADMIN · MANAGER · AUDITEUR |
| `GET /api/dashboard/me` (rapport de caisse du jour)   | authentifié             |
| `GET /api/reports/{type}?from&to&format=json\|csv`    | ADMIN · MANAGER · AUDITEUR |
| `GET /api/notifications` · `/unread-count` · `PATCH /{id}/read` · `POST /read-all` | authentifié |

## Ce qui est livré

```
config/            Horloge injectable, OpenAPI, audit JPA
common/error/      ApiError normalisé, ErrorCode (FR), handler global
common/web/        PageResponse, X-Request-Id
common/reference/  Générateur de références (CLI-000001, RECH-20260903-000001), compteur verrouillé
security/          SecurityConfig (stateless, RBAC méthode), JwtService, filtre JWT,
                   entry point / access-denied JSON, propriétés JWT & lockout & CORS
branch/            Entité Agence (+ seed MAIN)
user/              Entité User, rôles, CRUD (ADMIN), changement de mot de passe, bootstrap admin
auth/              login / refresh / logout / me, RefreshToken (haché, rotation),
                   comptage des échecs et détection de rejeu en transactions dédiées
customer/          Entité Customer, CRUD, recherche, statuts ; ouvre le compte à la création
account/           Entité Account (1/client), consultation, ouverture, verrou pessimiste
card/              Entité Card, machine à états (CardStatus), CRUD, job d'expiration
payment/           PaymentMethod (paramétrable), CRUD ADMIN
transaction/       Grand livre (ajout seul), consultation filtrée, projection DTO
recharge/          RechargeService — atomicité, verrou compte, idempotence, seuil de validation
category/          Category, CRUD, suppression protégée si utilisée
product/           Product, CRUD, prix TTC + décomposition TVA, recherche + filtre stock bas
inventory/         InventoryMovement (ajout seul), InventoryService (verrou produit, invariant), InventoryCalculation (pur)
sale/              Sale + SaleItem, SaleService (débit atomique multi-ressources, verrous ordonnés), SaleCalculation (pur)
audit/             AuditLog (ajout seul), AuditService (record/recordChange), AuditController
reversal/          TransactionReversalService (contre-passation + restock), ReversalController
account/           + AccountAdjustmentService (RG-13)
dashboard/         DashboardService (agrégats jour/semaine/mois, top N, activité, rapport agent)
report/            ReportService (6 rapports), CsvRenderer (BOM + ; pour Excel FR)
notification/      Notification, NotificationService (staffAlert dans la tx du fait métier)
common/money/      MoneyRules (FCFA entier, exclTax/taxOf)
common/idempotency/ IdempotencyService (reserve/complete/lookup), IdempotencyRecord
common/            Actor, AuditContext (IP/agent par requête)
db/migration/      V1..V8 (voir plus haut) · V9 audit · V10 notifications
```
