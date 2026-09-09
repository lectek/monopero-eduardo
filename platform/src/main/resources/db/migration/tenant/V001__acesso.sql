-- core.acesso — modelo real de usuário/papel/permissão (substitui o antigo
-- admin_users.role string solto "ADMIN"/"MOTOBOY"). Aplicado uma vez por
-- schema de tenant (ver TenantMigrationRunner).

create table usuario (
    id             bigint generated always as identity primary key,
    funcionario_id bigint,       -- FK adicionada em V0xx__pessoal.sql (Fase G) — nullable de propósito:
                                  -- dono tem login e não é funcionário; funcionário pode não ter login.
    nome           varchar(200) not null,
    email          varchar(255) not null unique,
    ativo          boolean      not null default true,
    ultimo_acesso_em timestamptz,
    criado_em      timestamptz  not null default now(),
    atualizado_em  timestamptz  not null default now()
);

create table papel (
    id          bigint generated always as identity primary key,
    nome        varchar(100) not null unique,
    descricao   varchar(500),
    -- Só ADMINISTRADOR é sistema=true: indeletável, sempre com todas as
    -- permissões — sem isso um tenant poderia se autobloquear.
    sistema     boolean      not null default false
);

create table permissao (
    id        bigint generated always as identity primary key,
    codigo    varchar(100) not null unique,   -- ex.: "VENDA_CANCELAR", "ESTOQUE_AJUSTAR"
    modulo    varchar(50)  not null,
    descricao varchar(300)
);

-- Sincronizado no boot a partir do catálogo em código (PermissaoSyncRunner) —
-- o que é configurável é o mapeamento papel->permissão, não o conjunto do
-- que o software sabe checar (ver docs/CONTEXTO.md).

create table papel_permissao (
    papel_id    bigint not null references papel(id) on delete cascade,
    permissao_id bigint not null references permissao(id) on delete cascade,
    primary key (papel_id, permissao_id)
);

create table usuario_papel (
    usuario_id bigint not null references usuario(id) on delete cascade,
    papel_id   bigint not null references papel(id) on delete cascade,
    primary key (usuario_id, papel_id)
);

-- Restrição fina por papel, ex.: chave="desconto.percentual_maximo" valor="10".
-- É como "desconto conforme permissão" funciona sem número fixo no código.
create table papel_restricao (
    id       bigint generated always as identity primary key,
    papel_id bigint       not null references papel(id) on delete cascade,
    chave    varchar(100) not null,
    valor    varchar(200) not null,
    constraint uk_papel_restricao unique (papel_id, chave)
);
