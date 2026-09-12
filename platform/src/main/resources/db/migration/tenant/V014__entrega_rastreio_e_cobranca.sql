-- Rastreio ao vivo (posição do motoboy + coordenadas de cada parada, já
-- calculadas pelo DeliveryRouteService na criação da rota mas até aqui
-- descartadas) e reconciliação de dinheiro coletado na entrega (motoboy que
-- recebe o pagamento em espécie na porta do cliente precisa saber quanto
-- fica de comissão e quanto devolve pra loja).
alter table entrega_rota add column localizacao_latitude    double precision;
alter table entrega_rota add column localizacao_longitude   double precision;
alter table entrega_rota add column localizacao_atualizada_em timestamptz;

alter table entrega_parada add column latitude                          double precision;
alter table entrega_parada add column longitude                         double precision;
alter table entrega_parada add column token_rastreio                    uuid not null default gen_random_uuid() unique;
-- Quanto ainda faltava pagar da venda no momento em que a rota foi criada
-- (total da venda menos pagamentos já registrados) — é o que o motoboy
-- pode vir a cobrar em espécie na entrega, distinto do valor do frete.
alter table entrega_parada add column valor_cobrar_na_entrega_snapshot  numeric(15,4) not null default 0;
