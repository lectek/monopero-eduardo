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

## ✅ Fase A — Multi-tenancy + Acesso + Cadastros + Produtos + Estoque (concluída)

**A maior fase (~4-5 semanas de escopo) — tudo depois depende dela.** Não
começar Fase B antes desta terminar de verdade.

**Progresso até aqui** (commits `1cb6ca9`, `8c5fecd`):
- ✅ Multi-tenancy real (schema por tenant, `TenantContext` +
  `SchemaMultiTenantConnectionProvider` + `TenantGuard`), schema
  `plataforma` (`Empresa`, `IdentidadeUsuario`, `refresh_tokens`),
  `PlataformaMigrationRunner`/`TenantMigrationRunner` (Flyway manual).
- ✅ `ProvisionamentoTenantService` ponta a ponta (schema + migrations +
  papel ADMINISTRADOR com todas as permissões + usuário inicial + tipos
  de movimentação de sistema), exposto em `POST /api/plataforma/empresas`.
- ✅ `core.acesso` (Usuario/Papel/Permissao/papel_permissao/usuario_papel/
  papel_restricao) + catálogo de permissões em código
  (`PermissaoCatalogo`) sincronizado por tenant.
- ✅ `core.cadastro`: os 8 cadastros do spec, tabelas criadas e mapeadas
  (repositórios simples; ainda sem tela/endpoint de CRUD dedicado —
  entram quando o primeiro consumidor real precisar, ex. Compras na
  Fase B).
- ✅ Login multiempresa (`IdentidadeService`/`AuthController`,
  `POST /api/v1/auth/login`) + `JwtAuthenticationFilter` novo
  (`/api/v1/**`, `@PreAuthorize` por permissão) — coexistindo com o login
  antigo (`AdminAuthController`, `TENANT_UNICO` hardcoded), que continua
  intocado até a Fase C.
- ✅ `core.auditoria` (evento de domínio + `@TransactionalEventListener
  BEFORE_COMMIT` — não Envers, ver docs/CONTEXTO.md).
- ✅ `core.produto`: `Produto` genérico + `ProdutoService` (alteração de
  preço sempre grava histórico + evento de auditoria) + `ProdutoController`.
  **Decisão registrada**: o catálogo antigo do IMS
  (`ImsProdutoRepository`, chave natural nome+cor+peso) **não foi
  deletado ainda** — `CheckoutService`/storefront/`AdminProdutoController`
  ainda o referenciam e só são repontados pro modelo novo na Fase C; os
  dois catálogos coexistem em pacotes distintos (`core.produto` vs
  `adapters.outbound.ims`) até lá, evitando quebrar a compilação do
  checkout antes da hora certa de reescrevê-lo.
- ✅ Validado ponta a ponta por teste de integração HTTP real
  (`AuthAndProdutoFlowIT`, mais `MultiTenancyIsolationIT` e
  `LedgerImutabilidadeIT` do critério de saída) — `mvn verify`: 8 testes,
  todos passando.

- ✅ `MovimentacaoEstoqueService` (chokepoint único de escrita no ledger,
  resolve conversão de unidade, custo médio ponderado, idempotente por
  origem, lock pessimista em `saldo_estoque`) + `EstoqueController`.
- ✅ `tools/importador` (`RbpImportService`): migra produtos + estoque
  inicial + contas admin de um `rbp.db` real pra um tenant novo,
  `analisar()` dry-run sempre antes de `importar()` real. **Não migra
  histórico de vendas** (`sold_records`) — depende do agregado Venda, que
  só existe a partir da Fase C; rodar de novo então completa essa parte.
  Cor/peso do legado viram texto solto na descrição por ora (não
  `produto_atributo` estruturado — completar quando os 8 cadastros
  ganharem CRUD de verdade).
- Bug real encontrado pelo próprio teste de integração durante o
  desenvolvimento desta fase: uma chamada a `identidadeUsuarioRepository`
  (schema "plataforma") rodando de dentro de um bloco com
  `TenantContext` setado pro schema do tenant — exatamente o risco de
  vazamento/cross-schema que o design previa, agora confirmado na prática
  e corrigido. Reforça que o padrão "nunca aninhar uma chamada
  plataforma-schema dentro de uma transação já aberta pro schema do
  tenant" precisa de atenção deliberada em toda fase futura que toque os
  dois mundos.

**Ficou pra quando um consumidor real precisar** (não bloqueia as
próximas fases): CRUD/endpoints reais pros 8 cadastros — hoje só
repositórios existem; entram quando, por exemplo, Compras (Fase B)
precisar de fato cadastrar um fornecedor ou uma condição de pagamento
pela UI.

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

## ✅ Fase B — Fornecedores + Compras (concluída)

Direto do fluxo do spec: nada relevante entra em estoque sem uma compra.
`fornecedor`, `fornecedor_contato`, `fornecedor_produto`,
`condicao_pagamento`, `compra`+`item_compra`. Geração de conta a pagar fica
atrás de uma porta (`GeradorContaPagar`, no-op por enquanto) pra Fase E
plugar sem tocar em `CompraService`. Módulo não existia — nada foi
reaproveitado do código atual.

- ✅ `Fornecedor` + `FornecedorController` (`FornecedorContato`/
  `FornecedorProduto` só como tabela por ora — mesmo padrão de "entidade
  Java quando um consumidor real precisar" já usado pros 8 cadastros).
- ✅ `CondicaoPagamento` (cadastro).
- ✅ `Compra`+`ItemCompra` com rateio de frete/outros custos proporcional
  ao subtotal de cada item — vira custo considerado no módulo de
  Rentabilidade (Fase H).
- ✅ `CompraService.confirmar()`: grava 1 movimentação de estoque por item
  (ledger, origem rastreável) + atualiza custo do produto via o mesmo
  fluxo auditado de `ProdutoService.alterarPreco` + chama
  `GeradorContaPagar` (só `NoopGeradorContaPagar` por enquanto). Idempotente
  pelo status da compra + índice único do ledger.
- Bug real pego pelo teste de integração: o caminho idempotente de
  `confirmar()` retornava cedo sem inicializar a coleção lazy `itens`
  dentro da transação — só quebrava na segunda chamada (LazyInitialization
  fora da sessão, `open-in-view=false`). Corrigido; reforça (junto com o
  bug da Fase A) que toda escrita cross-schema/cross-sessão nesta
  arquitetura precisa de atenção deliberada, não só "funcionou uma vez".

**Saída confirmada**: `CompraFlowIT` — fornecedor → compra → item →
condições (rateio de frete) → confirmar → saldo de estoque e custo médio
do produto corretos → confirmar de novo é no-op. `mvn verify`: 11 testes,
BUILD SUCCESS.

## ✅ Fase C — Clientes + Vendas/PDV (lado servidor) (concluída)

Cliente entra **antes** de Venda (não depois, como a ordem do spec
sugeriria) porque `venda.cliente_id` referencia cliente. `PedidoEntity`
(e os 3 enums problemáticos `StatusPedido`/`TipoPagamento`/
`MotivoCancelamentoPedido` que misturavam pagamento/entrega/cancelamento
numa coisa só) foi **substituído**, não estendido, por `Venda`.

- ✅ `Cliente` + `ClienteController` (só `nome` obrigatório).
- ✅ `Venda`+`ItemVenda`+`VendaPagamento` (`core.venda`) + `VendaService`
  com dois ciclos de vida: `registrar()` (PDV/balcão, monta+confirma numa
  chamada só) e `registrarPendente()`+`confirmarPagamento()` (checkout
  online — nasce RASCUNHO, só baixa estoque quando o pagamento é
  recebido). Idempotente por UUID; valida desconto contra
  `papel_restricao`; `cancelar()` cobre RASCUNHO (cancela direto) e
  CONFIRMADA (reverte estoque), auditado.
- ✅ `CheckoutService`/`MercadoPagoCheckoutService` reescritos sobre
  `Venda`+`VendaPagamentoGateway` (satélite 1:1, só canal ONLINE — os 12
  campos de gateway que antes viviam direto em `PedidoEntity`). Pagamento
  aprovado dispara `confirmarPagamento`; recusado/cancelado dispara
  `cancelar`. Carrinho agora referencia `produtoId` (não mais nome+cor+
  peso do catálogo do IMS).
- ✅ **Removido** (dependia de `PedidoEntity`/catálogo antigo, sem FK
  possível pra Compra/Orçamento/Devolução): `ImsProdutoRepository`,
  `CatalogoService`, `domain.catalogo.Produto`, `AdminProdutoController`,
  `PublicProdutoController`, `PedidoEntity`, `ItemPedidoEntity`,
  `PedidoRepository`, `StatusPedido`, `TipoPagamento`,
  `MotivoCancelamentoPedido`.
- ✅ **Removido** o módulo inteiro de roteirização de entrega
  (`EntregaRotaEntity`/`EntregaParadaEntity`, `AdminEntregaRouteService`,
  `PublicDeliveryEstimateService`, controllers admin/motoboy) — dependia
  também de `AdminUserEntity`, que não foi migrado pro modelo novo de
  acesso; vira módulo próprio numa fase futura contra Venda+Usuario.
  Mantido `DeliveryPricingService`/`DeliveryRouteService`/
  `DeliveryRouteOptimizer` (só cálculo de frete, sem persistir rota) —
  o checkout ainda cota frete pro modo ENTREGA.
- Dois gaps reais de infraestrutura descobertos e corrigidos pelos
  próprios testes: (1) `/api/public/**` (storefront/checkout) nunca teve
  NENHUM mecanismo de resolução de tenant — só `/api/v1/**` via JWT
  resolvia `TenantContext`. Criado `PublicTenantResolutionFilter`
  (subdomínio em produção, header `X-Empresa` em dev/teste). (2)
  `app_settings` (usado por `AppSettingService` — token do Mercado Pago,
  tarifas de frete) não existia em nenhuma migration desde o pivot pra
  Postgres na Fase 0 — adicionado V011.
- Bug de mapeamento JPA pego pelo teste: `VendaPagamentoGateway` usa
  `@MapsId` (ID copiado da Venda) — sem implementar `Persistable`, o
  Spring Data JPA achava que a entidade já existia (ID não-nulo) e
  tentava UPDATE em vez de INSERT na primeira gravação.

**Pendências conhecidas pra produção multiempresa de verdade** (não
bloqueiam as próximas fases): a URL de webhook do Mercado Pago ainda usa
1 `app.web.base-url` global, não por subdomínio de tenant — funciona pra
1 empresa por processo; `CustomerAuthController` perdeu a listagem de
"meus pedidos" (dependia de `PedidoRepository`) até `CustomerEntity`
(login) e `core.parceiro.Cliente` (comercial) serem correlacionados.

**Saída confirmada**: `mvn verify`: 14 testes, BUILD SUCCESS.
`CheckoutServiceIT` prova o ciclo completo do canal online (RASCUNHO →
staff confirma dinheiro → baixa estoque) e que Pix sem token configurado
falha de forma limpa (409), não 500.

## 🔄 Fase D — Cliente PDV offline + sincronização

Depende da Fase C estar estável — mudar `Venda` depois desta fase custa em
dobro (servidor + schema local + contrato de sync).

Invariante estrutural: **o caixa só grava registros append-only escopados
ao terminal — nunca escreve dado de cadastro.** Isso resolve sozinho o
problema de conflito entre dois caixas. Pix vira online-only (token nunca
mais fica em arquivo texto na loja).

### ✅ Lado servidor — concluído

- `pdv.Terminal`/`TerminalRepository` (migração `V012__pdv_terminal.sql`):
  pareamento gera uma chave de API (`"<schema>.<segredo>"`, segredo de 32
  bytes aleatórios) — só o hash SHA-256 é persistido. O prefixo de schema é
  o que permite ao `TerminalAuthenticationFilter` resolver o tenant **antes**
  de autenticar, já que sync roda sem usuário logado (chave de terminal, não
  JWT). Permissão nova `TERMINAL_GERENCIAR` protege
  `POST /api/v1/pdv/terminais`.
- `pdv.PdvSyncService` + `PdvSyncController`
  (`POST /api/v1/pdv/sync/push`, `GET /api/v1/pdv/sync/pull`):
  - Push é idempotente por `evento_uuid` via tabela `pdv_evento_recebido` —
    reenviar o mesmo evento (reconexão no meio do envio) responde
    `DUPLICADO` em vez de duplicar a venda. `VENDA_REGISTRADA` usa o próprio
    uuid do evento como uuid da `Venda`, herdando a idempotência que
    `VendaService.registrar` já tem por baixo; `VENDA_CANCELADA` resolve a
    venda por uuid e reusa `VendaService.cancelar`.
    Falha de negócio (produto inexistente etc.) vira `REJEITADO` com a
    mensagem, nunca 500 — fica pro terminal decidir o que fazer.
  - Pull tem dois formatos: `produto` é incremental por cursor
    `atualizado_em` (catálogo pode ser grande); `forma_pagamento` e
    `local_estoque` são cadastros pequenos — devolvem a lista inteira
    sempre, sem paginação (adicionados quando o cliente Swing precisou
    de fato, ver abaixo). Categoria/unidade/cliente ainda faltam.
  - `PdvSyncFlowIT` prova o ciclo completo: pareamento → push venda → retry
    idempotente → push cancelamento → pull de produto/forma_pagamento/
    local_estoque com cursor avançando.
  - **Fix**: `VendaService.validarDesconto` checa permissão pelo e-mail do
    usuário, mas o payload do PDV só carrega `usuarioId` —
    `PdvSyncService.registrarVenda` agora resolve o e-mail via
    `UsuarioRepository` antes de montar o comando; sem isso, qualquer
    desconto vindo do caixa falhava sempre, mesmo pro dono.

**Saída confirmada**: `mvn verify`: 16 classes de IT, BUILD SUCCESS.

### 🔄 Lado cliente (Swing) — backbone completo, App rodável com pareamento + Venda

`pdv/` saiu do layout antigo (`sourceDirectory=src`, Java 8) para o padrão
Maven (`src/main/java`, Java 17) — o legado (`mysquare.core`, intocado
desde a cópia inicial) foi só movido (`git mv`, mesmo pacote), pra caber
lado a lado com o pacote novo `br.com.lojagenerica.pdvclient` durante a
migração incremental.

Backbone não-visual, construído e testado (18 testes JUnit5, sem
precisar de GUI nem servidor real — `HttpServer` embutido faz de servidor
falso):
- `LocalDb`: uma conexão por instância (não mais o singleton estático do
  antigo `Db.java`, que tinha deadlock latente assim que uma thread de
  sync existisse ao lado da EDT do Swing) + bootstrap de schema
  idempotente (`cache_produto`, `cache_forma_pagamento`,
  `cache_local_estoque`, `sync_cursor`, `sync_outbox`,
  `venda_local[_item|_pagamento]`).
- `VendaLocalDao.registrarVenda`: grava o espelho local da venda e
  enfileira o evento de sync na MESMA transação SQLite — a invariante
  central do PDV offline (uma venda nunca existe sem seu evento de
  outbox). O uuid da venda vira o `evento_uuid`, espelhando a
  idempotência de `VendaRegistradaPayload`/`EventoPushRequest` do
  servidor.
- `OutboxDao`: fila local com backoff exponencial por evento (5s a
  15min) em erro de rede, status PENDENTE/ENVIADO/REJEITADO.
- `ApiClient` + `SyncScheduler`: thread única em background, drena o
  outbox a cada 10s e puxa produto/forma_pagamento/local_estoque a cada
  60s (cada um isolado em seu próprio try/catch) contra o servidor —
  nunca bloqueia a EDT.
- `ConfiguracaoLocalStore`/`LocalPaths`: estado de pareamento do
  terminal (`servidorBaseUrl` + `terminalApiKey`) em
  `%APPDATA%/lojagenerica/config.properties`.

Telas construídas (compiladas e revisadas — **não verificadas
visualmente nesta sessão**, sem display disponível; precisam de um
primeiro uso manual numa máquina de verdade antes de confiar no fluxo
completo):
- `TelaPareamento`: cola a chave de API gerada pelo admin web
  (`POST /api/v1/pdv/terminais`), testa a conexão de verdade antes de
  habilitar "Salvar".
- `VendaScreen`: substitui `Sale.java` contra o backbone novo — busca
  produto por nome/código de barras em `cache_produto` (sem a cascata
  cor/peso do IMS), quantidade em ponto flutuante, local de estoque e
  forma de pagamento escolhidos do que já sincronizou. Fora desta leva
  de propósito: desconto (precisa de login pra identificar quem vende),
  impressão de recibo, pagamento dividido (o modelo já suporta, a UI
  ainda não).
- `App`: novo ponto de entrada (`br.com.lojagenerica.pdvclient.App`) —
  sem pareamento, abre só `TelaPareamento`; pareado, sobe todo o
  backbone + `SyncScheduler` e mostra `VendaScreen`. Ainda não é o
  `mainClass` do assembly (continua `mysquare.core.IMStart` até o corte
  final).

### ⬜ Ainda falta

Login de operador (identifica quem vende — destrava desconto e
atribuição de venda), `Dispatch.java`→`AjusteEstoqueScreen`, adaptar
`Stock.java`/`SalesReport.java`/`SalesCalendar.java`/`Receipt.java` pro
schema local novo, deletar `Production.java`/`ModifyProducts.java`/
`AdminSaas.java` (migram pro admin web), repontar
`MercadoPagoPixClient`/`PixPaymentDialog` (Pix é online-only por design),
`jpackage` pro instalador, cortar o `mainClass` do assembly pro `App`
novo (ver plano completo, §6/§7). Cada tela deve continuar sendo tratada
como sua própria fatia, comitada e revisada antes da próxima.

**Saída (parcial, já provada)**: venda feita offline reflete no estoque
local na hora (espelho `venda_local`), enfileira o evento de sync na
mesma transação, chega ao servidor depois de reconectar, reconexão no
meio do envio não duplica a venda (`SyncSchedulerTest` prova isso ponta a
ponta contra um servidor falso). Falta o teste manual num terminal de
verdade, offline de propósito, pra fechar o critério de saída da fase.

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
