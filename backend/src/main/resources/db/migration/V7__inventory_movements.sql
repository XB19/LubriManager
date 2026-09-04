-- LubriManager — Phase 7 : mouvements de stock

-- Ajout seul. quantity = delta signé appliqué. Invariant : product.stock_quantity = Σ quantity.
create table inventory_movement (
    id           bigserial primary key,
    product_id   bigint       not null references product (id),
    type         varchar(20)  not null,
    quantity     numeric(15, 2) not null,
    stock_before numeric(15, 2) not null,
    stock_after  numeric(15, 2) not null,
    reference    varchar(60),
    reason       varchar(500),
    user_id      bigint       not null references app_user (id),
    branch_id    bigint       not null references branch (id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    version      bigint       not null default 0,
    constraint chk_invmov_type        check (type in ('ENTREE', 'SORTIE', 'AJUSTEMENT', 'INVENTAIRE')),
    constraint chk_invmov_stock_after check (stock_after >= 0)
);

create index idx_invmov_product_created on inventory_movement (product_id, created_at);
create index idx_invmov_type_created on inventory_movement (type, created_at);
