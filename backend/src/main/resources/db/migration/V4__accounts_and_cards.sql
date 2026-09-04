-- LubriManager — Phase 4 : comptes & cartes

insert into reference_counter (scope, next_value) values ('CARD', 0);

-- DEC-01 : un compte par client. DEC-08 : jamais de découvert.
create table account (
    id          bigserial primary key,
    customer_id bigint        not null unique references customer (id),
    balance     numeric(15, 2) not null default 0,
    currency    varchar(3)    not null default 'XOF',
    status      varchar(20)   not null default 'ACTIF',
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    version     bigint        not null default 0,
    constraint chk_account_balance_positive check (balance >= 0),
    constraint chk_account_balance_whole    check (balance = trunc(balance)),
    constraint chk_account_status           check (status in ('ACTIF', 'GELE', 'CLOS'))
);

-- DEC-09 : n° interne généré + n° externe optionnel. DEC-23 : expiration facultative.
-- DEC-24 : plusieurs cartes actives possibles sur un même compte.
create table card (
    id           bigserial primary key,
    card_no      varchar(20) not null unique,
    external_no  varchar(40) unique,
    customer_id  bigint      not null references customer (id),
    account_id   bigint      not null references account (id),
    status       varchar(20) not null default 'EN_ATTENTE',
    issued_at    timestamptz not null default now(),
    activated_at timestamptz,
    expires_at   date,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    version      bigint      not null default 0,
    constraint chk_card_status check (status in
        ('EN_ATTENTE', 'ACTIVE', 'SUSPENDUE', 'BLOQUEE', 'PERDUE', 'EXPIREE', 'DESACTIVEE'))
);

create index idx_card_status on card (status);
create index idx_card_customer on card (customer_id);
create index idx_card_account on card (account_id);
create index idx_card_expires on card (expires_at);

-- Back-fill : ouvrir le compte des clients déjà enregistrés (Phase 3).
insert into account (customer_id, balance, currency, status)
select id, 0, 'XOF', 'ACTIF' from customer;
