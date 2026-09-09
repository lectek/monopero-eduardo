-- Movido do schema de tenant pra "plataforma" pela mesma razão de
-- identidade_usuario: um refresh precisa reestabelecer o tenant antes de
-- existir contexto de schema resolvido.
create table refresh_tokens (
    id           bigint generated always as identity primary key,
    user_id      bigint       not null,
    tenant_id    varchar(100) not null,
    token        varchar(512) not null,
    issued_at    timestamptz  not null,
    expires_at   timestamptz  not null,
    revoked_at   timestamptz,
    ip_address   varchar(64),
    user_agent   varchar(512),
    constraint uk_refresh_token_token unique (token)
);

create index ix_refresh_token_user_tenant on refresh_tokens (user_id, tenant_id);
create index ix_refresh_token_expires_at on refresh_tokens (expires_at);
