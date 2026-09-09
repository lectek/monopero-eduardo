-- core.estoque — ledger append-only. UPDATE/DELETE são bloqueados por
-- trigger (não só por convenção) — uma correção é uma movimentação
-- compensatória nova, nunca uma edição da antiga.
create table movimentacao_estoque (
    id                bigint generated always as identity primary key,
    uuid              uuid          not null default gen_random_uuid() unique,
    produto_id        bigint        not null references produto(id),
    local_estoque_id  bigint        not null references local_estoque(id),
    tipo_movimentacao_id bigint     not null references tipo_movimentacao(id),
    sentido           varchar(10)   not null,
    quantidade        numeric(18,6) not null,
    unidade_id        bigint        not null references unidade_medida(id),
    fator_conversao   numeric(18,6) not null default 1,
    quantidade_base   numeric(18,6) not null,  -- assinada; único campo que saldo_estoque soma
    custo_unitario    numeric(15,4),
    saldo_apos        numeric(18,6),
    origem_tipo       varchar(30)   not null,
    origem_id         bigint,
    origem_item_id    bigint,
    terminal_id       bigint,
    usuario_id        bigint        references usuario(id),
    motivo            varchar(500),
    ocorrido_em       timestamptz   not null default now(),
    registrado_em     timestamptz   not null default now(),
    constraint ck_movimentacao_estoque_sentido check (sentido in ('ENTRADA', 'SAIDA')),
    constraint ck_movimentacao_estoque_origem_tipo check (origem_tipo in (
        'VENDA', 'COMPRA', 'DEVOLUCAO', 'ORCAMENTO', 'INVENTARIO',
        'AJUSTE_MANUAL', 'TRANSFERENCIA', 'PEDIDO_ONLINE', 'IMPORTACAO'
    ))
);

create index ix_movimentacao_estoque_produto_local on movimentacao_estoque (produto_id, local_estoque_id);
create index ix_movimentacao_estoque_ocorrido_em on movimentacao_estoque (ocorrido_em);
-- Idempotência de "esta origem já gerou movimentação?" vira constraint de
-- banco em vez de um boolean que alguém tem que lembrar de checar.
create unique index uk_movimentacao_estoque_origem
    on movimentacao_estoque (origem_tipo, origem_id, origem_item_id)
    where origem_id is not null;

create or replace function bloquear_alteracao_movimentacao_estoque()
returns trigger as $$
begin
    raise exception 'movimentacao_estoque é append-only — % não é permitido (id=%). '
        'Gere uma movimentação compensatória em vez de alterar/apagar esta.',
        tg_op, coalesce(old.id, new.id);
end;
$$ language plpgsql;

create trigger trg_movimentacao_estoque_no_update
    before update or delete on movimentacao_estoque
    for each row execute function bloquear_alteracao_movimentacao_estoque();

-- Projeção cacheada, sempre derivável de sum(quantidade_base) — nunca
-- tratada como fonte da verdade (ver ReconciliacaoSaldoJob, Fase A).
create table saldo_estoque (
    produto_id       bigint        not null references produto(id),
    local_estoque_id bigint        not null references local_estoque(id),
    quantidade       numeric(18,6) not null default 0,
    custo_medio      numeric(15,4),
    atualizado_em    timestamptz   not null default now(),
    primary key (produto_id, local_estoque_id)
);

create table inventario (
    id            bigint generated always as identity primary key,
    local_estoque_id bigint     not null references local_estoque(id),
    status        varchar(20)   not null default 'ABERTO',
    aberto_por_usuario_id bigint not null references usuario(id),
    aberto_em     timestamptz   not null default now(),
    finalizado_em timestamptz,
    motivo        varchar(500),
    observacoes   text,
    constraint ck_inventario_status check (status in ('ABERTO', 'EM_CONTAGEM', 'FINALIZADO', 'CANCELADO'))
);

create table inventario_item (
    id                    bigint generated always as identity primary key,
    inventario_id         bigint not null references inventario(id) on delete cascade,
    produto_id            bigint not null references produto(id),
    quantidade_esperada   numeric(18,6) not null,
    quantidade_encontrada numeric(18,6),
    diferenca             numeric(18,6) generated always as (quantidade_encontrada - quantidade_esperada) stored,
    contado_por_usuario_id bigint references usuario(id),
    contado_em            timestamptz,
    motivo                varchar(500)
);
create index ix_inventario_item_inventario on inventario_item (inventario_id);
