# LubriManager — Frontend (React + TypeScript + Vite)

Interface cliente du backend Spring Boot. Prévue pour être empaquetée avec **Tauri**
en application Windows, mais **testable dès maintenant dans le navigateur**.

## Prérequis

- Node.js 18+ (testé avec Node 24) — `npm` fourni avec.
- Le **backend démarré** sur `http://localhost:8080` (voir `../backend/README.md`).
- Rust n'est nécessaire **que** pour la version desktop Tauri (voir plus bas).

## Démarrer (mode navigateur — recommandé pour tester)

```bash
cd desktop
npm install
npm run dev
```

Ouvrez http://localhost:5173. Connectez-vous avec le compte administrateur créé
au premier démarrage du backend : **`admin` / `Admin!2026`**.

> Le CORS du backend autorise déjà `http://localhost:5173`. Si votre backend est
> ailleurs, changez l'URL dans **Paramètres → Serveur** (ou le fichier `.env`).

## Ce que vous pouvez tester

| Section | Actions |
|---|---|
| Tableau de bord | indicateurs jour / semaine / mois ; les agents voient leur rapport de caisse |
| Clients | rechercher, créer, modifier, changer le statut, ouvrir la fiche |
| Fiche client | solde du compte, cartes, **créer une carte**, **recharger une carte** |
| Cartes | rechercher, créer (avec choix du client), activer / suspendre / bloquer / désactiver, recharger |
| Recharges | liste, nouvelle recharge (choix carte + montant + moyen de paiement), **valider / refuser** (MANAGER/ADMIN) |
| Ventes | **point de vente** : choisir une carte, ajouter des produits, voir le total vs le solde, encaisser ; historique + détail |
| Transactions | grand livre filtrable ; **annuler (contre-passer)** une transaction validée (MANAGER/ADMIN) |
| Produits | rechercher, créer / modifier (prix TTC, TVA, seuil), activer / désactiver, filtre « stock faible » |
| Stock | mouvements ; **entrée / ajustement / inventaire** |
| Rapports | aperçu à l'écran + **export CSV** (ventes, recharges, transactions, annulations, stock, audit) |
| Audit | journal filtrable par action |
| Utilisateurs | liste, créer, activer / désactiver (ADMIN) |
| Notifications | cloche en haut à droite (stock bas, recharge en attente) |
| Paramètres | changer son mot de passe, régler l'URL du serveur |

Le contenu du menu s'adapte au rôle du compte connecté.

## Version desktop (Tauri) — optionnelle

Nécessite la **chaîne d'outils Rust** (https://rustup.rs) et, sur Windows, le
runtime **WebView2** (déjà présent sur Windows 10/11 récents).

```bash
# installer Rust puis, dans desktop/ :
npm run tauri dev      # lance l'app en fenêtre native
npm run tauri build    # produit LubriManager-Setup.exe (dans src-tauri/target/release/bundle)
```

Avant le premier `tauri build`, générez les icônes : `npm run tauri icon chemin/vers/logo.png`.

## Structure

```
src/
  lib/        api (axios + refresh JWT), auth (zustand), types, format (FCFA/dates), toast
  components/ AppLayout (menu §42), RequireAuth, StatusBadge, CustomerPicker, CardPicker, NotificationBell
  features/   un dossier par domaine (auth, dashboard, customers, cards, recharges, sales,
              transactions, products, inventory, reports, audit, users, settings)
```

Les types TS de `src/lib/types.ts` sont écrits à la main d'après les DTO du backend ;
ils pourront être générés depuis `openapi.json` (`/v3/api-docs`) plus tard.
