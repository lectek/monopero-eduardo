-- Relato de imprevisto/segurança do motoboy durante a rota — log de
-- verdade, separado da máquina de estado da parada (reportar não muda o
-- fluxo da entrega). Gravidade denormalizada de TipoOcorrencia#isGrave()
-- pra consultar "alertas abertos" direto no banco.
create table entrega_ocorrencia (
    id                        bigint generated always as identity primary key,
    rota_id                   bigint       not null references entrega_rota(id) on delete cascade,
    parada_id                 bigint       references entrega_parada(id),
    tipo                      varchar(40)  not null,
    gravidade                 varchar(10)  not null default 'NORMAL',
    descricao                 text,
    latitude                  double precision,
    longitude                 double precision,
    criado_por_usuario_id     bigint       references usuario(id),
    criado_em                 timestamptz  not null default now(),
    resolvido_em              timestamptz,
    resolvido_por_usuario_id  bigint       references usuario(id),
    constraint ck_entrega_ocorrencia_gravidade check (gravidade in ('NORMAL', 'GRAVE'))
);
create index ix_entrega_ocorrencia_rota on entrega_ocorrencia (rota_id);
create index ix_entrega_ocorrencia_alerta_aberto on entrega_ocorrencia (gravidade, resolvido_em);
