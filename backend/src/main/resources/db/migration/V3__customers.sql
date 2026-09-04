-- LubriManager — Phase 3 : clients

-- Compteurs de références métier (n° client, puis n° recharge / vente en Phase 5+).
create table reference_counter (
    scope      varchar(40) primary key,
    next_value bigint not null default 0
);
insert into reference_counter (scope, next_value) values ('CLI', 0);

create table customer (
    id           bigserial primary key,
    customer_no  varchar(20)  not null unique,
    full_name    varchar(160) not null,
    company_name varchar(160),
    phone        varchar(30),
    email        varchar(160),
    address      varchar(300),
    type         varchar(20)  not null,
    status       varchar(20)  not null default 'ACTIF',
    notes        varchar(1000),
    branch_id    bigint       not null references branch (id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    version      bigint       not null default 0,
    constraint chk_customer_type   check (type   in ('PARTICULIER', 'ENTREPRISE', 'ADMINISTRATION')),
    constraint chk_customer_status check (status in ('ACTIF', 'SUSPENDU', 'BLOQUE', 'ARCHIVE'))
);

create index idx_customer_status on customer (status);
create index idx_customer_type on customer (type);
create index idx_customer_full_name on customer (lower(full_name));
create index idx_customer_company on customer (lower(company_name));
create index idx_customer_phone on customer (phone);
create index idx_customer_branch on customer (branch_id);
