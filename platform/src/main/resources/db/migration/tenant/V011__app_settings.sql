-- Configuração key/value por tenant — usado por AppSettingService
-- (MercadoPagoCheckoutService: token/webhook; DeliveryPricingService: tarifas
-- de frete). Renomear pra "configuracao_empresa" com getters tipados fica
-- pra quando o resto do módulo Configurações for revisitado — por ora só
-- recria a tabela que o código já espera, que nenhuma migration criava
-- desde o pivot pra Postgres (Fase 0 removeu o SchemaInitializer antigo).
create table app_settings (
    id            bigint generated always as identity primary key,
    setting_key   varchar(100) not null unique,
    setting_value text,
    description   varchar(255),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);
