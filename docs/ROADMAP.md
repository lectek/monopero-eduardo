# Roteiro até finalizar o projeto

Ver `docs/CONTEXTO.md` primeiro se você está chegando agora. Este documento
é o passo a passo completo, fase por fase, com status atualizado conforme o
trabalho avança. Cada fase lista: o que entra, por que nessa ordem, e o
critério de saída (como saber que a fase terminou de verdade).

**Legenda de status**: ✅ concluída · 🔄 em andamento · ⬜ não iniciada

---

## ✅ Fase 0 — Fundação (concluída)

**O que foi feito** (só em `platform/`; `pdv/` ainda não foi tocado — ele só
entra na Fase D):

- Pacote/artefato renomeado: `br.com.minimercadinho.saas` →
  `br.com.lojagenerica`, `minimercadinho-saas` → `lojagenerica-platform`.
- SQLite removido (era compartilhado com o IMS); Postgres no lugar —
  `docker-compose.yml` ganhou serviço `postgres`, `pom.xml` trocou
  `sqlite-jdbc`/`hibernate-community-dialects` pelo driver `postgresql`.
- Flyway adicionado como dependência, `spring.flyway.enabled=false` (será
  orquestrado manualmente por schema a partir da Fase A — o autoconfigure
  padrão do Spring Boot só conhece 1 schema). Esqueleto de pastas criado:
  `src/main/resources/db/migration/{plataforma,tenant}/`,
  `src/main/resources/db/seed/dev/`.
- Removidos: `SchemaInitializer.java` (275 linhas de
  `CREATE TABLE IF NOT EXISTS` — não escala pra N schemas de tenant),
  `AdminUserSeeder.java` (criava admin com senha fixa incondicionalmente),
  `NoopAuthTokenFacade.java` + o toggle `jwt.enabled` (autenticação não pode
  ser opcional num sistema multiempresa — era também um interruptor de
  isolamento entre tenants).
- Arcabouço de testes: Testcontainers (Postgres real via Docker) + ArchUnit,
  com `maven-failsafe-plugin` rodando as classes `*IT` (mais lentas) na fase
  `verify`, separadas dos `*Test` rápidos do Surefire.

**Critério de saída (verificado)**: `mvn verify` roda a suíte completa e
passa — `ArchitectureRulesTest` (2 testes rápidos, provam que
SQLite/hibernate-community-dialects sumiram do classpath de produção) +
`LojaGenericaApplicationIT` (1 teste de integração, sobe o Spring context
contra um Postgres real via Testcontainers). Também confirmado manualmente:
`docker-compose up -d postgres` + `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
sobe limpo.

---

## ⬜ Fase A — Multi-tenancy + Acesso + Cadastros + Produtos + Estoque

**A maior fase (~4-5 semanas de escopo) — tudo depois depende dela.** Não
começar Fase B antes desta terminar de verdade.

### O que entra

1. **Schema `plataforma`** (control plane): tabela `empresa` (com
   `datasource_ref` reservado pra sharding futuro), `identidade_usuario`
   (índice de login global — resolve "qual empresa é essa" antes de saber o
   schema), `refresh_tokens` movido pra cá.
2. **Wiring de multi-tenancy** (pacote novo `br.com.lojagenerica.multitenancy`):
   `TenantContext` (ThreadLocal), `SchemaTenantIdentifierResolver`,
   `SchemaMultiTenantConnectionProvider` (`SET search_path` na aquisição
   *e* na devolução da conexão — ver risco #1 abaixo), `TenantResolutionFilter`,
   `TenantGuard` (fail-closed se o contexto não estiver setado).
3. **Provisionamento de empresa nova**: `POST /api/plataforma/empresas` →
   cria schema `empresa_NNN` → roda migrations → cria papel `ADMINISTRADOR`
   + 1 usuário + os `tipo_movimentacao` de sistema → tudo ou nada (falha
   parcial derruba o schema).
4. **Modelo mínimo de Acesso real** (`core.acesso`): `usuario`, `papel`,
   `permissao`, `papel_permissao`, `usuario_papel`. Login reescrito pra
   resolver tenant primeiro (índice em `plataforma`), depois autenticar
   dentro do schema certo. `AdminJwtAuthFilter` reescrito como
   `JwtAuthenticationFilter`, populando uma `Authentication` real (hoje ele
   só guarda atributos de request — não dá pra usar `@PreAuthorize` nisso).
   *A gestão fina (tela de papéis, editor de permissão por papel) fica pra
   Fase G — aqui só o modelo, pra Fase A não virar um projeto à parte.*
5. **Mecanismo de auditoria** (`core.auditoria`) — antes de existir
   qualquer escrita auditável. Tabela `registro_auditoria` + eventos de
   domínio explícitos (`@TransactionalEventListener(BEFORE_COMMIT)`), não
   Hibernate Envers (motivo: o spec pede eventos de negócio com motivo/
   contexto, não diff de coluna — ver plano completo pra detalhe).
6. **Os 8 cadastros** (`core.cadastro`): categoria (auto-referenciada),
   marca, unidade de medida, conversão de unidade, forma de pagamento, tipo
   de movimentação, local de estoque, definição de atributo (resposta a
   "nem todo produto tem peso/marca/lote/validade/nº série").
7. **Produto genérico** (`core.produto`): só `nome`+`unidade` obrigatórios.
   **Deleta** o catálogo atual do IMS (`domain/catalogo/Produto.java`,
   `ImsProdutoRepository.java`, `CatalogoService.java`,
   `AdminProdutoController.java`) — a chave natural nome+cor+peso não
   expressa produto sem cor/peso, não permite FK de Compra/Orçamento, e
   grava estoque em lugar (viola "nunca alterar estoque silenciosamente").
8. **Ledger de estoque** (`core.estoque`): `movimentacao_estoque`
   append-only (UPDATE/DELETE bloqueados por trigger), `saldo_estoque`
   (projeção cacheada), `inventario`/`inventario_item` (contagem física).
9. **`tools/importador`**: lê um `rbp.db` real, cria um tenant, migra
   produtos/estoque/vendas históricas/contas admin. Modo dry-run primeiro,
   nunca contra o banco real sem ensaio.

### Critério de saída (não-negociável)

- Teste de integração: provisiona 2 tenants, grava produtos distintos em
  cada, prova que uma request autenticada como tenant A retorna **zero**
  linhas de tenant B — inclusive depois de forçar reciclagem do pool de
  conexões (loop 50x). Isso existe porque o maior risco do projeto inteiro
  é o `search_path` de uma conexão vazar de um tenant pro próximo depois
  que ela volta pro pool — silencioso e catastrófico se acontecer.
- Teste que `UPDATE`/`DELETE` no ledger de estoque lança exceção.
- Provisionamento ponta a ponta (schema criado, migrations aplicadas, admin
  inicial funcional) num teste automatizado.

---

## ⬜ Fase B — Fornecedores + Compras

Direto do fluxo do spec: nada relevante entra em estoque sem uma compra.
`fornecedor`, `fornecedor_contato`, `fornecedor_produto`,
`condicao_pagamento`, `compra`+`item_compra`. Geração de conta a pagar fica
atrás de uma porta (`GeradorContaPagar`, no-op por enquanto) pra Fase E
plugar sem tocar em `CompraService`. Módulo não existe hoje — nada é
reaproveitado do código atual.

**Saída**: `CompraService.confirmar()` grava ledger de entrada + custo
médio + conta a pagar numa transação só, idempotente ao repetir a chamada.

## ⬜ Fase C — Clientes + Vendas/PDV (lado servidor)

Cliente entra **antes** de Venda (não depois, como a ordem do spec
sugeriria) porque `venda.cliente_id` referencia cliente. Reescreve
`PedidoEntity`→`Venda` (substitui, não estende — os 3 enums problemáticos
de hoje, `StatusPedido`/`TipoPagamento`/`MotivoCancelamentoPedido`, viram
3 máquinas de estado separadas + cadastros). `CheckoutService`/módulo de
loja online recolocados pra montar `Venda` — a lógica do Mercado Pago e o
módulo de entrega sobrevivem quase intactos.

**Saída**: `VendaService.registrar()` idempotente por UUID, valida
desconto contra permissão do papel.

## ⬜ Fase D — Cliente PDV offline + sincronização

Aqui é onde `pdv/` (ainda intocado desde a cópia inicial) finalmente muda.
Depende da Fase C estar estável — mudar `Venda` depois desta fase custa em
dobro (servidor + schema local + contrato de sync).

Invariante estrutural: **o caixa só grava registros append-only escopados
ao terminal — nunca escreve dado de cadastro.** Isso resolve sozinho o
problema de conflito entre dois caixas. Mecanismo: SQLite local
(`pdv-local.db`, não mais `rbp.db`) + tabela `sync_outbox` (evento gravado
na mesma transação que a venda) + API de sync com autenticação de terminal
separada de autenticação de usuário. Pix vira online-only (token nunca
mais fica em arquivo texto na loja).

**Saída**: venda feita offline reflete no estoque local na hora, chega ao
servidor depois de reconectar, reconexão no meio do envio não duplica a
venda (idempotência por UUID do evento).

## ⬜ Fase E — Financeiro

`conta_pagar`/`conta_receber` + parcelas, `categoria_financeira`,
`caixa`+`movimento_caixa` (mesma regra de ledger append-only do estoque),
fluxo de caixa previsto×realizado. Implementa as portas deixadas stubadas
nas Fases B/C.

## ⬜ Fase F — Orçamentos + Devoluções

Estruturalmente derivados de Venda (mesmo formato de item, mesmas chamadas
de ledger) — baratos depois de Vendas+Financeiro existirem, caros antes.
Devolução precisa de Financeiro (reembolso) e Estoque (retorno de item).

## ⬜ Fase G — Funcionários + Usuários/Permissões completo + Auditoria

`funcionario`; vínculo `funcionario_id` em venda/orçamento/movimentação
("venda por", "orçamento criado por"). UI de gestão de papel/permissão
(a parte que ficou de fora da Fase A de propósito), visualizador de log de
auditoria com diff antes/depois.

## ⬜ Fase H — Custos/Rentabilidade + Relatórios + Dashboard

Puramente leitura, deliberadamente por último — relatório é query sobre
dado que as fases anteriores produzem. Dashboard com estado vazio explícito
(nunca gráfico zerado que parece resultado real).

## ⬜ Fase I — Fiscal (estrutural) + Configurações + de-branding final

As 5 tabelas fiscais + porta `ProvedorDocumentoFiscal` (só
`NoopProvedorDocumentoFiscal` por enquanto — sem regra tributária fixa no
código). UI de configuração da empresa. Remove o que ainda restar da marca
"Mini Mercadinho Rota"/"Rota das Praias" nos templates do storefront
(JS/CSS ainda têm referências residuais — deixadas de propósito até aqui,
ver `docs/ARQUITETURA.md` se existir uma auditoria completa desses pontos).

---

## Riscos a monitorar (em ordem de gravidade)

1. **Vazamento de `search_path` entre conexões pooladas** (Fase A) —
   silencioso, catastrófico, viola o requisito inviolável de isolamento
   entre empresas.
2. **Fase A é grande e tudo trava nela** — resistir à tentação de começar
   Compras antes do ledger e do modelo de permissão estarem fechados.
3. **Migração de dados das 3 empresas reais** a partir de `rbp.db` (quando
   existirem os bancos reais) — `RbpImporter` precisa de dry-run,
   reconciliação (contagens antes×depois) e ensaio antes de qualquer corte
   real.
4. **Janela de corte do PDV**: entre a Fase C (servidor dono de Venda) e a
   Fase D (caixa sincroniza), o caixa antigo continua escrevendo local sem
   contraparte no servidor — manter o build antigo rodando até a Fase D
   estar pronta.

## Perguntas em aberto pro Eduardo (não bloqueiam o trabalho, cada uma com default assumido)

1. Postgres ou MySQL? → assumido **Postgres** (schema-por-tenant em MySQL
   equivale a banco-por-tenant, perde a vantagem de pool compartilhado).
2. Hospedagem: 1 VPS (Postgres+app+Caddy) ou Postgres gerenciado? →
   assumido **VPS único**.
3. Pode empacotar um JRE no instalador do PDV (`jpackage`, ~60MB) e subir
   pra Java 17? → assumido **sim** (Fase D).
4. Algum tenant precisa de mais de 1 caixa físico nos próximos 12 meses? →
   assumido **não, mas o design já suporta**.

## Onde está o plano técnico completo (nível de detalhe: entidades, colunas, classes)

`/home/alex/.claude/plans/bubbly-baking-donut.md` — o documento que foi
escrito durante o planejamento e aprovado antes de começar a Fase 0. Este
`ROADMAP.md` é o resumo executivo que evolui junto com o código; para
nomes exatos de tabela/coluna/classe ao implementar cada fase, consulte o
plano completo.
