-- core.cadastro — os 8 cadastros base. Nada aqui é pré-populado por esta
-- migration (regra do spec contra dado comercial fictício em produção) —
-- as únicas linhas criadas automaticamente são as de tipo_movimentacao
-- marcadas sistema=true, e só isso, feito pelo provisionamento (não por
-- Flyway), porque só o provisionamento sabe que é a primeira vez do tenant.

create table categoria (
    id             bigint generated always as identity primary key,
    nome           varchar(150) not null,
    categoria_pai_id bigint references categoria(id),
    caminho        varchar(500) not null,  -- caminho materializado, ex. "/1/7/22/"
    nivel          smallint     not null default 0,
    ativo          boolean      not null default true
);
create index ix_categoria_caminho on categoria (caminho);

create table marca (
    id          bigint generated always as identity primary key,
    nome        varchar(150) not null unique,
    fabricante  varchar(150),
    ativo       boolean      not null default true
);

create table unidade_medida (
    id             bigint generated always as identity primary key,
    codigo         varchar(20)  not null unique,  -- "UN", "KG", "M", "CX" — livre, nada seedado
    descricao      varchar(100) not null,
    casas_decimais smallint     not null default 0,
    fracionavel    boolean      not null default false,
    ativo          boolean      not null default true
);

create table conversao_unidade (
    id                bigint generated always as identity primary key,
    unidade_origem_id  bigint not null references unidade_medida(id),
    unidade_destino_id bigint not null references unidade_medida(id),
    fator             numeric(18,6) not null,
    produto_id        bigint,  -- FK adicionada em V004__produto.sql; null = regra global
    constraint uk_conversao_unidade unique (unidade_origem_id, unidade_destino_id, produto_id)
);

create table forma_pagamento (
    id                    bigint generated always as identity primary key,
    nome                  varchar(100) not null,
    natureza              varchar(30)  not null,
    afeta_caixa           boolean      not null default true,
    permite_parcelamento  boolean      not null default false,
    max_parcelas          smallint,
    prazo_recebimento_dias integer,
    taxa_percentual       numeric(6,3),
    ativo                 boolean      not null default true,
    constraint ck_forma_pagamento_natureza check (natureza in (
        'DINHEIRO', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'PIX', 'BOLETO',
        'TRANSFERENCIA', 'CREDITO_LOJA', 'OUTRO'
    ))
);

create table tipo_movimentacao (
    id               bigint generated always as identity primary key,
    codigo           varchar(50)  not null unique,
    nome             varchar(100) not null,
    sentido          varchar(10)  not null,
    sistema          boolean      not null default false,
    exige_motivo     boolean      not null default false,
    afeta_custo_medio boolean     not null default false,
    ativo            boolean      not null default true,
    constraint ck_tipo_movimentacao_sentido check (sentido in ('ENTRADA', 'SAIDA'))
);

create table local_estoque (
    id        bigint generated always as identity primary key,
    nome      varchar(150) not null,
    tipo      varchar(50),
    endereco  jsonb,
    principal boolean      not null default false,
    ativo     boolean      not null default true
);

create table atributo_definicao (
    id                    bigint generated always as identity primary key,
    nome                  varchar(100) not null,
    tipo                  varchar(20)  not null,
    opcoes                jsonb,
    obrigatorio           boolean      not null default false,
    aplicavel_categoria_id bigint references categoria(id),
    constraint ck_atributo_definicao_tipo check (tipo in (
        'TEXTO', 'NUMERO', 'DATA', 'BOOLEANO', 'LISTA'
    ))
);
