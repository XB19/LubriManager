-- LubriManager — Phase 2 : socle d'authentification
-- DEC-02 : la colonne branch_id est introduite dès maintenant (agence unique par défaut).

create table branch (
    id         bigserial primary key,
    code       varchar(20)  not null unique,
    name       varchar(120) not null,
    active     boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    version    bigint       not null default 0
);

create table app_user (
    id                  bigserial primary key,
    username            varchar(60)  not null unique,
    email               varchar(160) unique,
    password_hash       varchar(100) not null,
    full_name           varchar(120) not null,
    role                varchar(20)  not null,
    active              boolean      not null default true,
    branch_id           bigint       not null references branch(id),
    failed_attempts     integer      not null default 0,
    locked_until        timestamptz,
    last_login_at       timestamptz,
    password_changed_at timestamptz,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    version             bigint       not null default 0,
    constraint chk_user_role check (role in ('ADMIN', 'MANAGER', 'AGENT', 'AUDITEUR'))
);

create index idx_user_role on app_user (role);
create index idx_user_active on app_user (active);

-- Session longue : jeton opaque, stocké haché (SHA-256 hex = 64 caractères).
create table refresh_token (
    id          bigserial primary key,
    user_id     bigint      not null references app_user (id),
    token_hash  varchar(64) not null unique,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    replaced_by varchar(64),
    user_agent  varchar(200),
    ip          varchar(45),
    created_at  timestamptz not null default now()
);

create index idx_refresh_user on refresh_token (user_id);
create index idx_refresh_expires on refresh_token (expires_at);
