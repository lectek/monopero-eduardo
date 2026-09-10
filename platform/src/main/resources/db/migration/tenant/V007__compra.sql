-- core.compra — nada relevante entra em estoque sem passar por aqui
-- (fluxo do spec: FORNECEDOR -> COMPRA -> ESTOQUE -> ...).
create table compra (
    id                    bigint generated always as identity primary key,
    numero                varchar(50),
    fornecedor_id         bigint       not null references fornecedor(id),
    data_emissao          date         not null default current_date,
    data_entrada          date,
    status                varchar(20)  not null default 'RASCUNHO',
    subtotal              numeric(15,4) not null default 0,
    desconto_valor        numeric(15,4) not null default 0,
    frete                 numeric(15,4) not null default 0,
    outros_custos         numeric(15,4) not null default 0,
    total                 numeric(15,4) not null default 0,
    condicao_pagamento_id bigint references condicao_pagamento(id),
    forma_pagamento_id    bigint references forma_pagamento(id),
    local_estoque_id      bigint       not null references local_estoque(id),
    observacoes           text,
    usuario_id            bigint       references usuario(id),
    criado_em             timestamptz  not null default now(),
    confirmada_em         timestamptz,
    constraint ck_compra_status check (status in (
        'RASCUNHO', 'CONFIRMADA', 'RECEBIDA_PARCIAL', 'RECEBIDA', 'CANCELADA'
    ))
);
create index ix_compra_fornecedor on compra (fornecedor_id);

create table item_compra (
    id                      bigint generated always as identity primary key,
    compra_id               bigint not null references compra(id) on delete cascade,
    produto_id              bigint not null references produto(id),
    quantidade              numeric(18,6) not null,
    unidade_id              bigint not null references unidade_medida(id),
    fator_conversao         numeric(18,6) not null default 1,
    quantidade_base         numeric(18,6) not null,
    preco_unitario          numeric(15,4) not null,
    desconto_valor          numeric(15,4) not null default 0,
    rateio_frete            numeric(15,4) not null default 0,
    rateio_outros_custos    numeric(15,4) not null default 0,
    -- Custo pronto pra virar saldo_estoque.custo_medio na confirmação —
    -- rateio de frete/outros custos entra no custo do produto, não fica
    -- "perdido" num total de compra sem atribuição por item.
    custo_unitario_final    numeric(15,4) generated always as (
        case when quantidade_base = 0 then 0
        else ((preco_unitario * quantidade) - desconto_valor + rateio_frete + rateio_outros_custos) / quantidade_base
        end
    ) stored,
    total_linha             numeric(15,4) not null
);
create index ix_item_compra_compra on item_compra (compra_id);
