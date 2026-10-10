create table receipt
(
    id             bigserial primary key,
    chat_id        bigint      not null,
    user_id        bigint      not null,
    store          text,
    tax_id         text,
    date_time_raw  text,
    purchased_at   timestamp,
    total          numeric(14, 2),
    payment_method text,
    vat_amount     numeric(14, 2),
    receipt_number text,
    model          text        not null,
    raw_json       text        not null,
    created_at     timestamptz not null default now()
);

create table receipt_item
(
    receipt_id bigint not null references receipt (id) on delete cascade,
    position   int    not null,
    name       text,
    price      numeric(14, 2),
    quantity   numeric(14, 3),
    total      numeric(14, 2),
    primary key (receipt_id, position)
);

-- отдельно от receipt, чтобы списки и статистика не тянули картинки
create table receipt_image
(
    receipt_id bigint primary key references receipt (id) on delete cascade,
    mime_type  text  not null,
    content    bytea not null
);
