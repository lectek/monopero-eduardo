-- Satélite 1:1 opcional de venda — só para vendas do canal ONLINE, cujo
-- pagamento passa por um gateway externo (Mercado Pago). Vendas de PDV não
-- têm linha aqui. Mantido junto de core.venda por ora (não em
-- modules/lojaonline, que ainda não existe como pacote separado — ver
-- docs/ROADMAP.md).
create table venda_pagamento_gateway (
    venda_id                    bigint primary key references venda(id) on delete cascade,
    tipo_pagamento_online       varchar(20)  not null,
    provider                    varchar(30),
    preference_id               varchar(100),
    external_reference          varchar(100),
    checkout_url                varchar(500),
    payment_id                  varchar(100),
    payment_status              varchar(30),
    payment_status_detail       varchar(100),
    payment_updated_at          timestamptz,
    payment_ticket_url          varchar(500),
    pix_qr_code                 text,
    pix_qr_code_base64          text,
    forma_pagamento_recebida    varchar(60),
    pagamento_divergente        boolean      not null default false,
    pagamento_recebido_em       timestamptz,
    constraint ck_venda_pgtw_tipo check (tipo_pagamento_online in (
        'PIX', 'BOLETO', 'CARTAO_CREDITO', 'CARTAO_DEBITO'
    ))
);
create index ix_venda_pgtw_payment_id on venda_pagamento_gateway (payment_id);
create index ix_venda_pgtw_external_ref on venda_pagamento_gateway (external_reference);
