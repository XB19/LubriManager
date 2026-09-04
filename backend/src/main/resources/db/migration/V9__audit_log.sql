-- LubriManager — Phase 9 : piste d'audit + références annulation / ajustement

insert into reference_counter (scope, next_value) values ('ANNL', 0), ('AJUS', 0);

-- En ajout seul. old_value / new_value en JSON (texte) — migrables vers jsonb plus tard.
create table audit_log (
    id          bigserial primary key,
    user_id     bigint,
    username    varchar(60),
    action      varchar(40)   not null,
    entity_type varchar(40),
    entity_id   bigint,
    reference   varchar(40),
    summary     varchar(1000) not null,
    old_value   text,
    new_value   text,
    ip          varchar(45),
    user_agent  varchar(300),
    branch_id   bigint,
    created_at  timestamptz   not null default now()
);

create index idx_audit_action_created on audit_log (action, created_at);
create index idx_audit_entity on audit_log (entity_type, entity_id);
create index idx_audit_user_created on audit_log (user_id, created_at);
create index idx_audit_created on audit_log (created_at);
