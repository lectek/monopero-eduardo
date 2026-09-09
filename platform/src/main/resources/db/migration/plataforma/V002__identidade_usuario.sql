-- Índice de login global: resolve "em qual empresa esse e-mail existe" ANTES
-- de saber o schema/tenant — o login não pode consultar um schema que ainda
-- não foi resolvido. usuario_id_tenant aponta pro core.acesso.usuario dentro
-- do schema daquela empresa (sem FK cross-schema — Postgres não suporta FK
-- entre schemas de bancos lógicos distintos aqui, e mesmo suportando seria
-- FK pra uma tabela cujo schema muda por linha).
create table identidade_usuario (
    id                bigint generated always as identity primary key,
    email             varchar(255) not null,
    empresa_id        bigint       not null references empresa(id),
    usuario_id_tenant bigint       not null,
    senha_hash        varchar(100) not null,
    ativo             boolean      not null default true,
    criado_em         timestamptz  not null default now(),
    constraint uk_identidade_usuario_email_empresa unique (email, empresa_id)
);

create index ix_identidade_usuario_email on identidade_usuario (email);

comment on table identidade_usuario is
    'Um e-mail pode aparecer em N empresas (ex.: o dono com acesso a todas). '
    'Login resolve por email -> lista de empresas -> escolhe uma -> resolve '
    'schema -> autentica de fato contra core.acesso.usuario naquele schema.';
