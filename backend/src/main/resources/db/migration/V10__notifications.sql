-- LubriManager — Phase 10 : notifications applicatives (§34)

create table notification (
    id         bigserial primary key,
    user_id    bigint,                      -- nominative si renseigné
    audience   varchar(20),                 -- diffusée si renseigné (STAFF = ADMIN + MANAGER)
    type       varchar(30)  not null,
    severity   varchar(20)  not null,
    title      varchar(160) not null,
    body       varchar(500),
    entity_ref varchar(60),
    read_at    timestamptz,
    created_at timestamptz  not null default now(),
    constraint chk_notification_target check (user_id is not null or audience is not null),
    constraint chk_notification_severity check (severity in ('INFO', 'ALERTE', 'CRITIQUE'))
);

create index idx_notification_user on notification (user_id, read_at, created_at);
create index idx_notification_audience on notification (audience, read_at, created_at);
