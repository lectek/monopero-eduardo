-- core.venda — substitui o antigo PedidoEntity/StatusPedido (9 valores
-- misturando 3 máquinas de estado ortogonais: pagamento, entrega,
-- cancelamento). Aqui status cobre só o comercial (RASCUNHO/CONFIRMADA/
-- CANCELADA); satélites de entrega/pagamento de gateway ficam pra quando
-- os módulos de entrega/loja online forem repontados pra cá.
create table venda (
    id                      bigint generated always as identity primary key,
    uuid                    uuid         not null default gen_random_uuid() unique,
    numero                  varchar(50),
    canal                   varchar(20)  not null,
    terminal_id             bigint,
    cliente_id              bigint       references cliente(id),
    funcionario_id          bigint,
    usuario_id              bigint       references usuario(id),
    local_estoque_id        bigint       not null references local_estoque(id),
    data                    timestamptz  not null default now(),
    status                  varchar(20)  not null default 'RASCUNHO',
    subtotal                numeric(15,4) not null default 0,
    desconto_valor          numeric(15,4) not null default 0,
    desconto_percentual     numeric(6,3),
    acrescimo               numeric(15,4) not null default 0,
    total                   numeric(15,4) not null default 0,
    origem_orcamento_id     bigint       unique,
    observacoes             text,
    cancelada_em            timestamptz,
    cancelamento_motivo     varchar(500),
    cancelado_por_usuario_id bigint      references usuario(id),
    criado_offline          boolean      not null default false,
    sincronizado_em         timestamptz,
    criado_em               timestamptz  not null default now(),
    constraint ck_venda_canal check (canal in ('PDV', 'ONLINE', 'ORCAMENTO', 'IMPORTADO')),
    constraint ck_venda_status check (status in ('RASCUNHO', 'CONFIRMADA', 'CANCELADA'))
);
create index ix_venda_cliente on venda (cliente_id);
create index ix_venda_data on venda (data);

create table item_venda (
    id                      bigint generated always as identity primary key,
    venda_id                bigint not null references venda(id) on delete cascade,
    produto_id              bigint not null references produto(id),
    descricao_snapshot      varchar(255) not null,
    codigo_snapshot         varchar(100),
    quantidade              numeric(18,6) not null,
    unidade_id              bigint not null references unidade_medida(id),
    fator_conversao         numeric(18,6) not null default 1,
    quantidade_base         numeric(18,6) not null,
    preco_unitario          numeric(15,4) not null,
    desconto_valor          numeric(15,4) not null default 0,
    total_linha             numeric(15,4) not null,
    -- Base inteira do módulo de Rentabilidade (Fase H): margem tem que usar
    -- o custo vigente NA VENDA, não o de hoje.
    custo_unitario_snapshot numeric(15,4),
    quantidade_devolvida    numeric(18,6) not null default 0
);
create index ix_item_venda_venda on item_venda (venda_id);

-- 1:N de propósito — pagamento dividido (parte dinheiro, parte cartão) é
-- impossível com uma coluna única de forma de pagamento.
create table venda_pagamento (
    id                  bigint generated always as identity primary key,
    venda_id            bigint not null references venda(id) on delete cascade,
    forma_pagamento_id  bigint not null references forma_pagamento(id),
    valor               numeric(15,4) not null,
    parcelas            smallint,
    valor_recebido      numeric(15,4),
    troco               numeric(15,4),
    referencia_externa  varchar(100),
    recebido_em         timestamptz  not null default now()
);
create index ix_venda_pagamento_venda on venda_pagamento (venda_id);
