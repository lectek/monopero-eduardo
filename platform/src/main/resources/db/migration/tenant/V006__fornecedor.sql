-- core.parceiro (lado fornecedor) — tabela separada de cliente (que só
-- chega na Fase C): regras de unicidade/ciclo de vida diferentes o
-- suficiente pra não valer juntar numa "pessoa" genérica (ver docs/CONTEXTO.md).
create table fornecedor (
    id                  bigint generated always as identity primary key,
    razao_social        varchar(200) not null,
    nome_fantasia       varchar(200),
    documento           varchar(32),
    inscricao_estadual  varchar(32),
    endereco            jsonb,
    observacoes         text,
    status              varchar(20)  not null default 'ATIVO',
    criado_em           timestamptz  not null default now(),
    constraint ck_fornecedor_status check (status in ('ATIVO', 'INATIVO'))
);

create table fornecedor_contato (
    id            bigint generated always as identity primary key,
    fornecedor_id bigint       not null references fornecedor(id) on delete cascade,
    nome          varchar(150) not null,
    cargo         varchar(100),
    telefone      varchar(32),
    email         varchar(255)
);
create index ix_fornecedor_contato_fornecedor on fornecedor_contato (fornecedor_id);

-- "Produtos fornecidos" do spec — histórico simples de último preço/compra,
-- não um catálogo de compras completo (isso é o que item_compra vai virar).
create table fornecedor_produto (
    fornecedor_id     bigint not null references fornecedor(id) on delete cascade,
    produto_id        bigint not null references produto(id),
    codigo_no_fornecedor varchar(100),
    ultimo_preco      numeric(15,4),
    ultima_compra_em  timestamptz,
    primary key (fornecedor_id, produto_id)
);

-- Cadastro: gera as parcelas de conta a pagar na confirmação da compra
-- (Fase E implementa a geração de verdade — ver GeradorContaPagar).
create table condicao_pagamento (
    id                    bigint generated always as identity primary key,
    nome                  varchar(100) not null,
    parcelas              smallint     not null default 1,
    intervalo_dias        integer      not null default 0,
    entrada_percentual    numeric(6,3),
    ativo                 boolean      not null default true
);
