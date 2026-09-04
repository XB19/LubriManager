-- LubriManager — Phase 8 : ventes

insert into reference_counter (scope, next_value) values ('VENT', 0);

create table sale (
    id             bigserial primary key,
    transaction_id bigint not null unique references account_transaction (id),
    subtotal       numeric(15, 2) not null,
    tax            numeric(15, 2) not null default 0,
    total          numeric(15, 2) not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    version        bigint not null default 0,
    constraint chk_sale_amounts check (subtotal >= 0 and total >= 0)
);

create index idx_sale_created on sale (created_at);

-- Nom / référence / prix / TVA du produit figés au moment de la vente (RG-07).
create table sale_item (
    id                bigserial primary key,
    sale_id           bigint       not null references sale (id),
    product_id        bigint       not null references product (id),
    product_name      varchar(160) not null,
    product_reference varchar(20)  not null,
    quantity          numeric(15, 2) not null,
    unit_price        numeric(15, 2) not null,
    vat_rate          numeric(5, 2)  not null,
    line_total        numeric(15, 2) not null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint not null default 0,
    constraint chk_saleitem_qty  check (quantity > 0),
    constraint chk_saleitem_line check (line_total >= 0)
);

create index idx_saleitem_sale on sale_item (sale_id);
create index idx_saleitem_product on sale_item (product_id);

-- Les SORTIE de stock d'une vente portent sa référence VENT-… dans inventory_movement.reference.
