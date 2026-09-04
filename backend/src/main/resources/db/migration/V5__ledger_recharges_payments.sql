-- LubriManager — Phase 5 : grand livre, recharges, moyens de paiement

create table payment_method (
    id         bigserial primary key,
    code       varchar(30) not null unique,
    label      varchar(80) not null,
    active     boolean     not null default true,
    sort_order integer     not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0
);

insert into payment_method (code, label, sort_order) values
    ('ESPECES',  'Espèces',            1),
    ('TMONEY',   'TMoney',             2),
    ('FLOOZ',    'Flooz',              3),
    ('VIREMENT', 'Virement bancaire',  4),
    ('CHEQUE',   'Chèque',             5),
    ('AUTRE',    'Autre',              6);

-- Grand livre. « transaction » étant un mot-clé SQL, la table s'appelle account_transaction.
-- Ajout seul : pas de DELETE ; seules les transitions de statut modifient une ligne.
create table account_transaction (
    id                     bigserial primary key,
    reference              varchar(30)   not null unique,
    type                   varchar(20)   not null,
    status                 varchar(20)   not null,
    amount                 numeric(15, 2) not null,
    account_id             bigint        not null references account (id),
    card_id                bigint        references card (id),
    customer_id            bigint        not null references customer (id),
    user_id                bigint        not null references app_user (id),
    branch_id              bigint        not null references branch (id),
    balance_before         numeric(15, 2) not null,
    balance_after          numeric(15, 2) not null,
    related_transaction_id bigint        references account_transaction (id),
    idempotency_key        varchar(80)   unique,
    description            varchar(500),
    created_at             timestamptz   not null default now(),
    updated_at             timestamptz   not null default now(),
    version                bigint        not null default 0,
    constraint chk_txn_type   check (type in
        ('RECHARGE', 'ACHAT', 'REMBOURSEMENT', 'ANNULATION', 'AJUSTEMENT', 'CORRECTION')),
    constraint chk_txn_status check (status in ('EN_ATTENTE', 'VALIDEE', 'REFUSEE', 'ANNULEE')),
    constraint chk_txn_amounts_whole check (
        amount = trunc(amount)
        and balance_before = trunc(balance_before)
        and balance_after = trunc(balance_after))
);

create index idx_txn_account_created on account_transaction (account_id, created_at);
create index idx_txn_type_status_created on account_transaction (type, status, created_at);
create index idx_txn_customer_created on account_transaction (customer_id, created_at);
create index idx_txn_card_created on account_transaction (card_id, created_at);

create table recharge (
    id                bigserial primary key,
    transaction_id    bigint not null unique references account_transaction (id),
    payment_method_id bigint not null references payment_method (id),
    comment           varchar(500),
    rejected_reason   varchar(500),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint not null default 0
);

create index idx_recharge_created on recharge (created_at);

create table idempotency_record (
    idem_key     varchar(80) primary key,
    endpoint     varchar(120) not null,
    request_hash varchar(64)  not null,
    status       varchar(20)  not null,
    http_status  integer,
    response_body text,
    created_at   timestamptz  not null default now(),
    constraint chk_idem_status check (status in ('IN_PROGRESS', 'COMPLETED'))
);
