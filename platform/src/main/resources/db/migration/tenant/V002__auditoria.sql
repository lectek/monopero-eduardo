-- core.auditoria — eventos de negócio explícitos (não Hibernate Envers, ver
-- docs/CONTEXTO.md e o plano completo pra justificativa). Escrito só por
-- AuditoriaService.registrar(), dentro da mesma transação da mudança que
-- ele descreve (@TransactionalEventListener BEFORE_COMMIT).
create table registro_auditoria (
    id                bigint generated always as identity primary key,
    ocorrido_em       timestamptz  not null default now(),
    usuario_id        bigint,
    usuario_email_snapshot varchar(255),
    ip                varchar(64),
    user_agent        varchar(512),
    terminal_id       bigint,
    evento            varchar(100) not null,   -- ex.: "PRODUTO_PRECO_ALTERADO"
    entidade          varchar(100) not null,
    entidade_id       bigint,
    valores_antes     jsonb,
    valores_depois    jsonb,
    motivo            varchar(500),
    contexto          jsonb
);

create index ix_registro_auditoria_entidade on registro_auditoria (entidade, entidade_id);
create index ix_registro_auditoria_ocorrido_em on registro_auditoria (ocorrido_em);
