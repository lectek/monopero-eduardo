-- core.parceiro (lado cliente) — só nome obrigatório: cliente de balcão
-- não tem e-mail nem documento (ao contrário do customers.email UNIQUE
-- NOT NULL de hoje, que força senha-placeholder aleatória no checkout).
create table cliente (
    id              bigint generated always as identity primary key,
    uuid            uuid         not null default gen_random_uuid() unique,
    nome            varchar(200) not null,
    documento       varchar(32),
    telefone        varchar(32),
    email           varchar(255),
    endereco        jsonb,
    limite_credito  numeric(15,4),
    observacoes     text,
    status          varchar(20)  not null default 'ATIVO',
    criado_em       timestamptz  not null default now(),
    constraint ck_cliente_status check (status in ('ATIVO', 'INATIVO'))
);
create index ix_cliente_nome on cliente (nome);
create index ix_cliente_documento on cliente (documento) where documento is not null;
