-- core.produto — substitui a chave natural nome+cor+peso do IMS (ver
-- docs/CONTEXTO.md). Só nome + unidade_estoque são obrigatórios.
create table produto (
    id                     bigint generated always as identity primary key,
    uuid                   uuid         not null default gen_random_uuid() unique,
    nome                   varchar(255) not null,
    codigo_interno         varchar(100) unique,
    descricao              text,
    categoria_id           bigint references categoria(id),
    marca_id               bigint references marca(id),
    fabricante             varchar(150),
    unidade_estoque_id     bigint       not null references unidade_medida(id),
    unidade_venda_id       bigint references unidade_medida(id),
    preco_venda            numeric(15,4),
    custo_aquisicao        numeric(15,4),
    estoque_minimo         numeric(18,6),
    estoque_maximo         numeric(18,6),
    local_estoque_padrao_id bigint references local_estoque(id),
    peso                   numeric(15,4),
    altura                 numeric(15,4),
    largura                numeric(15,4),
    profundidade           numeric(15,4),
    controla_estoque       boolean      not null default true,
    permite_venda_sem_estoque boolean   not null default false,
    status                 varchar(20)  not null default 'ATIVO',
    observacoes            text,
    criado_em              timestamptz  not null default now(),
    atualizado_em          timestamptz  not null default now(),
    versao                 bigint       not null default 0,
    constraint ck_produto_status check (status in ('ATIVO', 'INATIVO', 'DESCONTINUADO'))
);
create index ix_produto_nome on produto (nome);
create index ix_produto_categoria on produto (categoria_id);

alter table conversao_unidade
    add constraint fk_conversao_unidade_produto foreign key (produto_id) references produto(id);

create table produto_codigo_barras (
    id         bigint generated always as identity primary key,
    produto_id bigint       not null references produto(id) on delete cascade,
    codigo     varchar(50)  not null unique,
    unidade_id bigint references unidade_medida(id),
    principal  boolean      not null default false
);

create table produto_atributo (
    id                     bigint generated always as identity primary key,
    produto_id             bigint not null references produto(id) on delete cascade,
    atributo_definicao_id  bigint not null references atributo_definicao(id),
    valor_texto            text,
    valor_numero           numeric(18,6),
    valor_data             date,
    valor_booleano         boolean,
    constraint uk_produto_atributo unique (produto_id, atributo_definicao_id)
);

-- Auditoria de preço é item #1 da lista obrigatória do spec — tabela
-- dedicada além do registro_auditoria genérico, porque é a base direta do
-- módulo de rentabilidade (Fase H).
create table produto_preco_historico (
    id              bigint generated always as identity primary key,
    produto_id      bigint       not null references produto(id) on delete cascade,
    preco_anterior  numeric(15,4),
    preco_novo      numeric(15,4),
    custo_anterior  numeric(15,4),
    custo_novo      numeric(15,4),
    vigente_de      timestamptz  not null default now(),
    usuario_id      bigint       not null references usuario(id),
    motivo          varchar(500)
);
create index ix_produto_preco_historico_produto on produto_preco_historico (produto_id);

create table custo_adicional_definicao (
    id        bigint generated always as identity primary key,
    nome      varchar(150) not null,
    tipo      varchar(20)  not null,
    valor     numeric(15,4) not null,
    aplicacao varchar(20)  not null,
    ativo     boolean      not null default true,
    constraint ck_custo_adicional_tipo check (tipo in ('PERCENTUAL', 'VALOR_FIXO')),
    constraint ck_custo_adicional_aplicacao check (aplicacao in ('POR_PRODUTO', 'POR_COMPRA', 'GLOBAL'))
);

create table produto_custo_adicional (
    produto_id             bigint not null references produto(id) on delete cascade,
    custo_adicional_definicao_id bigint not null references custo_adicional_definicao(id) on delete cascade,
    primary key (produto_id, custo_adicional_definicao_id)
);
