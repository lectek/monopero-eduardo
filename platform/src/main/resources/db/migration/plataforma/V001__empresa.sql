-- Gera o sufixo numérico de schema_nome (empresa_007) de forma segura sob
-- concorrência — nextval() é atômico, ao contrário de "SELECT MAX(id)+1".
create sequence empresa_schema_seq start with 1;

-- Control plane: 1 linha por empresa (tenant). "schema_nome" é o nome
-- estável do schema Postgres daquela empresa (ex.: empresa_007) — NUNCA
-- o nome fantasia, pra um rebrand não exigir renomear schema nem migrar
-- dado nenhum.
create table empresa (
    id                bigint generated always as identity primary key,
    schema_nome        varchar(63)  not null unique,
    razao_social       varchar(200) not null,
    nome_fantasia      varchar(200),
    documento          varchar(32),
    subdominio         varchar(100) not null unique,
    -- Reserva pra sharding físico futuro (ver docs/ROADMAP.md, Fase A) — hoje
    -- toda empresa resolve pro mesmo DataSource "primary"; o dia que uma
    -- precisar de isolamento físico total, adiciona-se um segundo DataSource
    -- e troca-se só esta coluna.
    datasource_ref     varchar(50)  not null default 'primary',
    status             varchar(30)  not null default 'PROVISIONANDO',
    criado_em          timestamptz  not null default now(),
    ativada_em         timestamptz,
    constraint ck_empresa_status check (status in (
        'PROVISIONANDO', 'ATIVA', 'SUSPENSA',
        'MIGRACAO_FALHOU', 'PROVISIONAMENTO_FALHOU'
    ))
);

comment on table empresa is
    'Uma linha por tenant. schema_nome é o schema Postgres real (empresa_NNN); '
    'nome_fantasia pode mudar livremente sem afetar schema_nome nem dado nenhum.';
