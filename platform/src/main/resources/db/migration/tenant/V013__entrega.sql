-- core.entrega — motoboy carrega várias vendas (modo ENTREGA) numa única
-- rota (ida-e-volta), na ordem calculada por
-- DeliveryRouteService/DeliveryRouteOptimizer (TSP exato, já existente,
-- reaproveitado sem reescrever). Venda ganha modo/endereço/valor de frete
-- próprios — antes disso o frete só existia embutido em "acrescimo", sem
-- coluna própria (ver core.venda.Venda).
alter table venda add column modo_entrega    varchar(20)  not null default 'RETIRADA';
alter table venda add column endereco_entrega text;
alter table venda add column valor_frete     numeric(15,4) not null default 0;
alter table venda add constraint ck_venda_modo_entrega check (modo_entrega in ('RETIRADA', 'ENTREGA'));

create table entrega_rota (
    id                          bigint generated always as identity primary key,
    uuid                        uuid         not null default gen_random_uuid() unique,
    origem                      text         not null,
    distancia_total_km          numeric(10,2),
    mapa_url                    text,
    status                      varchar(20)  not null default 'PLANEJADA',
    entregador_id               bigint       references usuario(id),
    criado_por_usuario_id       bigint       references usuario(id),
    percentual_comissao_snapshot numeric(6,3) not null default 0,
    criada_em                   timestamptz  not null default now(),
    iniciada_em                 timestamptz,
    finalizada_em               timestamptz,
    cancelada_em                timestamptz,
    cancelamento_motivo         varchar(500),
    constraint ck_entrega_rota_status check (status in ('PLANEJADA', 'EM_EXECUCAO', 'CONCLUIDA', 'CANCELADA'))
);
create index ix_entrega_rota_entregador on entrega_rota (entregador_id);
create index ix_entrega_rota_status on entrega_rota (status);

-- Snapshots de nome/endereço/frete: se a venda ou o cliente mudarem depois,
-- a parada não muda — estabilidade de auditoria (padrão idêntico em todas
-- as referências analisadas: MiniMercadinhoSaaS, SaúdeMaisFarma, multlektec).
create table entrega_parada (
    id                          bigint generated always as identity primary key,
    rota_id                     bigint       not null references entrega_rota(id) on delete cascade,
    venda_id                    bigint       not null references venda(id),
    ordem                       int          not null,
    cliente_nome_snapshot       varchar(200) not null,
    endereco_entrega_snapshot   text         not null,
    codigo_entrega              varchar(10)  not null,
    valor_frete_snapshot        numeric(15,4) not null default 0,
    distancia_anterior_km       numeric(10,2),
    distancia_acumulada_km      numeric(10,2),
    status                      varchar(25)  not null default 'PENDENTE',
    chegou_em                   timestamptz,
    entregue_em                 timestamptz,
    forma_pagamento_recebida    varchar(20),
    pagamento_divergente        boolean      not null default false,
    avaliacao_entrega           smallint,
    ocorrencias                 text,
    falha_motivo                varchar(500),
    observacao                  text,
    constraint ck_entrega_parada_status check (status in
        ('PENDENTE', 'A_CAMINHO', 'CHEGOU', 'ENTREGUE', 'TENTATIVA_SEM_SUCESSO', 'REAGENDAR', 'CANCELADA')),
    constraint uk_entrega_parada_venda_ativa unique (venda_id, rota_id)
);
create index ix_entrega_parada_rota on entrega_parada (rota_id);
create index ix_entrega_parada_venda on entrega_parada (venda_id);
