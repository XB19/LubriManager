-- LubriManager — Phase 11 : simplification du périmètre
-- Retrait des ventes (POS), du stock, de la TVA, des catégories et des moyens de
-- paiement. Nouveau modèle : débit carte pour produits (card_purchase).

-- 1. Ventes (POS) ---------------------------------------------------------------
drop table if exists sale_item;
drop table if exists sale;

-- 2. Mouvements de stock ------------------------------------------------------
drop table if exists inventory_movement;

-- 3. Recharge : espèces uniquement, validation immédiate --------------------
alter table recharge drop column if exists payment_method_id;
alter table recharge drop column if exists rejected_reason;

-- 4. Moyens de paiement ----------------------------------------------------------
drop table if exists payment_method;

-- 5. Produit : modèle minimal (nom, marque, unité, prix, description) -----------
--    Postgres retire automatiquement les contraintes CHECK et index portés par
--    les colonnes supprimées.
update product set brand = 'N/A' where brand is null;
alter table product alter column brand set not null;
alter table product drop column if exists vat_rate;
alter table product drop column if exists stock_quantity;
alter table product drop column if exists alert_threshold;
alter table product drop column if exists status;
alter table product drop column if exists category_id;

-- 6. Catégories ----------------------------------------------------------------
drop table if exists category;

-- 7. Compteurs de référence --------------------------------------------------
delete from reference_counter where scope like 'VENT%';

-- 8. Débit carte : achat de produits réglé sur le solde ---------------------
create table card_purchase (
    id             bigserial primary key,
    transaction_id bigint not null unique references account_transaction (id),
    total          numeric(15, 2) not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    version        bigint not null default 0,
    constraint chk_card_purchase_total check (total >= 0)
);

create index idx_card_purchase_created on card_purchase (created_at);

-- Nom / référence / prix du produit figés au moment de l'opération.
create table card_purchase_item (
    id                bigserial primary key,
    purchase_id       bigint       not null references card_purchase (id),
    product_id        bigint       not null references product (id),
    product_name      varchar(160) not null,
    product_reference varchar(20)  not null,
    quantity          numeric(15, 2) not null,
    unit_price        numeric(15, 2) not null,
    line_total        numeric(15, 2) not null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint not null default 0,
    constraint chk_card_purchase_item_qty  check (quantity > 0),
    constraint chk_card_purchase_item_line check (line_total >= 0)
);

create index idx_card_purchase_item_purchase on card_purchase_item (purchase_id);
create index idx_card_purchase_item_product on card_purchase_item (product_id);
