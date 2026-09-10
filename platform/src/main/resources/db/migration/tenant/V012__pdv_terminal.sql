-- core.pdv — terminal (o caixa físico) e o registro de idempotência de
-- eventos sincronizados. Chave de API é um segredo de alta entropia
-- gerado pelo servidor (não senha humana) — hash SHA-256 simples é
-- suficiente e permite lookup indexado (bcrypt não permite buscar por
-- hash, só verificar um valor já conhecido).
create table terminal (
    id             bigint generated always as identity primary key,
    uuid           uuid         not null default gen_random_uuid() unique,
    nome           varchar(150) not null,
    api_key_hash   varchar(64)  not null unique,
    ativo          boolean      not null default true,
    pareado_em     timestamptz  not null default now(),
    ultimo_sync_em timestamptz
);

-- Idempotência de sincronização: o mesmo evento_uuid (gerado no terminal,
-- na mesma transação SQLite local que grava a venda) nunca é processado
-- duas vezes, mesmo se a conexão cair depois do commit no servidor e o
-- terminal reenviar por não ter visto a resposta.
create table pdv_evento_recebido (
    id          bigint generated always as identity primary key,
    evento_uuid uuid        not null unique,
    terminal_id bigint      not null references terminal(id),
    tipo        varchar(30) not null,
    venda_id    bigint,
    recebido_em timestamptz not null default now()
);
