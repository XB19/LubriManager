-- LubriManager — Phase 6 : produits & catégories

insert into reference_counter (scope, next_value) values ('PROD', 0);

create table category (
    id          bigserial primary key,
    name        varchar(100) not null unique,
    description varchar(500),
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0
);

-- Jeu de départ (§19) — renommable / désactivable par l'entreprise.
insert into category (name, description) values
    ('Huile moteur',            'Lubrifiants pour moteurs thermiques'),
    ('Huile hydraulique',       'Fluides pour circuits hydrauliques'),
    ('Huile transmission',      'Boîtes de vitesses, ponts, différentiels'),
    ('Graisse',                 'Graisses de lubrification'),
    ('Liquide de refroidissement', 'Antigels et liquides de refroidissement'),
    ('Lubrifiants industriels', 'Applications industrielles diverses');

create table product (
    id              bigserial primary key,
    reference       varchar(20)  not null unique,
    name            varchar(160) not null,
    brand           varchar(100),
    category_id     bigint       not null references category (id),
    description     varchar(1000),
    unit            varchar(20)  not null,
    unit_price      numeric(15, 2) not null,   -- prix TTC en FCFA entiers (DEC-04)
    vat_rate        numeric(5, 2)  not null default 18.00,
    stock_quantity  numeric(15, 2) not null default 0,  -- cache ; vérité = mouvements de stock (Phase 7)
    alert_threshold numeric(15, 2) not null default 0,
    status          varchar(20)  not null default 'ACTIF',
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    version         bigint       not null default 0,
    constraint chk_product_price     check (unit_price >= 0 and unit_price = trunc(unit_price)),
    constraint chk_product_vat       check (vat_rate >= 0 and vat_rate <= 100),
    constraint chk_product_stock     check (stock_quantity >= 0),
    constraint chk_product_threshold check (alert_threshold >= 0),
    constraint chk_product_status    check (status in ('ACTIF', 'INACTIF'))
);

create index idx_product_category on product (category_id);
create index idx_product_status on product (status);
create index idx_product_name on product (lower(name));
create index idx_product_brand on product (lower(brand));
