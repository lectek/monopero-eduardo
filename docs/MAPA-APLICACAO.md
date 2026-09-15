# Mapa descritivo da aplicação

Retrato de onde o código está e o que cada parte faz, na data deste
relatório. Não substitui `docs/CONTEXTO.md` (por quê o projeto existe e as
decisões arquiteturais fechadas) nem `docs/ROADMAP.md` (o que falta, fase
por fase, sempre a fonte da verdade sobre progresso) — este documento é o
retrato estático de "o que tem e onde", pra alguém abrir o repositório e
se situar rápido.

> `platform/docs/FUNCIONALIDADES.md`, `ECOSSISTEMA.md` e `DEPLOY-LOJA.md`
> descreviam o antigo "Mini Mercadinho Rota"/MiniMercadinhoSaaS (SQLite,
> IMS Swing, arquitetura pré-multiempresa) — copiados durante o port
> original e nunca atualizados até esta rodada de limpeza:
> `FUNCIONALIDADES.md` e `DEPLOY-LOJA.md` foram reescritos pro estado
> atual; `ECOSSISTEMA.md` foi removido (conteúdo superado por este
> documento + `docs/CONTEXTO.md`).

---

## 1. Visão geral

```
monopero-eduardo/
├── platform/   # backend multiempresa (Spring Boot + Postgres) — o grosso do sistema
├── pdv/        # cliente de caixa offline-first (Java Swing, desktop)
└── docs/       # CONTEXTO.md, ROADMAP.md, este arquivo
```

Um núcleo comercial genérico (Cadastros, Produtos, Estoque, Compras,
Vendas, Entrega, Acesso) servindo várias empresas (**tenants**) com dados
isolados de verdade — schema Postgres separado por empresa, não
`tenant_id` numa tabela compartilhada. `platform/` é o servidor; `pdv/` é
o caixa físico da loja, que precisa continuar funcionando sem internet.

---

## 2. `platform/` — o backend

Spring Boot 3 / Java 21, Postgres, Flyway, Thymeleaf pro que é
server-rendered, REST pro que não é. Pacote raiz `br.com.lojagenerica`.

### 2.1 Multiempresa (`multitenancy/`, `platform/`)

| Peça | Papel |
|---|---|
| `SchemaTenantIdentifierResolver` / `SchemaMultiTenantConnectionProvider` | Fazem o Hibernate trocar de schema Postgres por conexão, a partir do `TenantContext` (ThreadLocal) já setado quando a query roda |
| `TenantContext` | ThreadLocal simples — schema do tenant atual |
| `TenantGuard` | Fail-closed: barra qualquer acesso a repositório se `TenantContext` não estiver setado |
| `TenantAwareTaskDecorator` | Propaga o `TenantContext` pra threads `@Async` |
| `PlataformaMigrationRunner` / `TenantMigrationRunner` | Rodam as migrations Flyway — uma vez no schema `plataforma`, uma vez por schema de tenant existente |
| `TenantBootstrapService` | O que acontece na criação de um tenant novo: papel ADMINISTRADOR + sincroniza `PermissaoCatalogo` + tipos de movimentação de sistema + 1 usuário — nunca dado comercial de exemplo |
| `ProvisionamentoTenantService` | Orquestra: reserva schema → migra → bootstrap → grava `IdentidadeUsuario` na `plataforma` → tudo-ou-nada (dropa o schema se qualquer passo falhar) |
| `Empresa` / `IdentidadeUsuario` (schema `plataforma`) | Cadastro de empresas + índice de login global (resolve "esse e-mail existe em qual empresa" antes de saber o schema) |

Testado por `MultiTenancyIsolationIT` (o teste mais crítico do projeto —
força reciclagem de conexão em loop de 50 iterações pra pegar vazamento
de `search_path` entre tenants) e `ProvisionamentoTenantServiceIT`.

### 2.2 Segurança — quatro mecanismos coexistindo

| Superfície | Mecanismo | Identidade | Onde |
|---|---|---|---|
| `/gestao/**` (admin novo) | Sessão + `AdminAuthenticationProvider`, `@PreAuthorize` por permissão | `core.acesso.Usuario` | `security/admin/AdminSecurityConfig` (`@Order(1)`) |
| `/api/v1/**` (API nova) | JWT stateless, `JwtAuthenticationFilter` popula `Authentication` de verdade | `core.acesso.Usuario` | `security/JwtAuthenticationFilter` |
| `/`, `/login`, `/cadastro`, `/minha-conta`, `/checkout` (storefront) | Sessão, form login + Google OAuth2 | `CustomerEntity` (legado, pré-multitenant) | `adapters/inbound/web/security/SecurityConfig` (`@Order(2)`) |
| `/api/admin/**`, `/api/motoboy/**` (legado) | `AdminJwtAuthFilter` — só seta atributos de request, não uma `Authentication` real | `admin_users` (legado) | `adapters/inbound/web/security/AdminJwtAuthFilter` |
| `/api/public/**`, `/webhooks/**`, `/rastreio/**` | Sem auth — tenant resolvido por subdomínio ou header `X-Empresa` | — | `security/PublicTenantResolutionFilter` |
| `/api/v1/pdv/**` | API key do terminal físico (não usuário) | `pdv.Terminal` | `security/TerminalAuthenticationFilter` |

Os dois primeiros (`/gestao/**` e `/api/v1/**`) são o modelo de acesso
**de verdade**, com RBAC real (`Papel`/`Permissao`, catálogo fechado em
`PermissaoCatalogo`, sincronizado no boot). O quarto (`/api/admin/**`)
é uma sobrevivência do modelo pré-port — hoje só serve
`AdminPedidoController` e as telas `/admin/pedidos`/`/admin/produtos`
(ver § 2.9); tudo o mais que dependia dele (entregas, motoboy) foi
substituído e removido.

### 2.3 `core/acesso` — usuário, papel, permissão

`Usuario` (funcionário opcional, dono pode não ser funcionário) — N:N
com `Papel` — N:N com `Permissao`. `PermissaoCatalogo` é o enum fechado de
códigos que o sistema sabe checar (ex.: `PRODUTO_ESCREVER`,
`VENDA_CANCELAR`, `ENTREGA_GERENCIAR`); `PermissaoCatalogSyncService`
garante que toda entrada do enum vira linha na tabela `permissao` de cada
tenant a cada boot, e que o papel `ADMINISTRADOR` (papel de sistema,
indeletável) sempre tem todas elas. O que é configurável por tenant é o
**mapeamento** papel→permissão, nunca o conjunto de códigos existente.

### 2.4 `core/cadastro` — os 8 cadastros base

`Marca`, `UnidadeMedida`, `ConversaoUnidade`, `FormaPagamento` (com
`NaturezaFormaPagamento` — PIX/DINHEIRO/CARTAO_*/BOLETO), `LocalEstoque`,
`CondicaoPagamento`, `TipoMovimentacao` (linhas de sistema fixas via
`TipoMovimentacaoSistema` + linhas livres do usuário), `Categoria`
(hierárquica) e `AtributoDefinicao`. Nada pré-populado além dos tipos de
movimentação de sistema — regra explícita contra dado comercial fictício
em produção.

### 2.5 `core/produto`

`Produto` — só `nome` + `unidadeEstoque` obrigatórios (substitui a chave
natural nome+cor+peso do IMS antigo). `ProdutoPrecoHistorico` audita toda
mudança de preço. `ProdutoService` concentra a criação/edição.

### 2.6 `core/estoque` — ledger append-only

`MovimentacaoEstoque` — **UPDATE/DELETE bloqueados por trigger de banco**
(não só por convenção da camada de serviço, ver `V005__estoque.sql`); uma
correção é sempre uma movimentação nova compensatória.
`origem_tipo`/`origem_id` polimórfico (venda, compra, devolução,
inventário, ajuste manual) com índice único que dá idempotência de
verdade (repetir a mesma origem não duplica a baixa). `SaldoEstoque` é
projeção — sempre derivável da soma do ledger. Testado por
`LedgerImutabilidadeIT` (via JDBC puro, provando o trigger) e
`EstoqueMovimentacaoFlowIT`.

### 2.7 `core/parceiro` — clientes e fornecedores

Tabelas separadas (`Cliente`, `Fornecedor`) de propósito — regras de
unicidade e ciclo de vida diferentes o suficiente pra não valer juntar
numa "pessoa" genérica. Só `nome` é obrigatório em `Cliente` (cliente de
balcão não tem e-mail).

### 2.8 `core/compra`

`Compra` + `ItemCompra`, `StatusCompra`. `CompraService.confirmar()` é o
template de transação cross-módulo do projeto: grava ledger de entrada +
atualiza custo médio numa transação só, idempotente por status.
`GeradorContaPagar` é uma porta com única implementação `NoopGeradorContaPagar`
por enquanto — o Financeiro (Fase E) pluga aqui sem tocar `CompraService`.
Testado por `CompraFlowIT`.

### 2.9 `core/venda`

`Venda` (substitui o antigo `PedidoEntity`/`StatusPedido` de 9 valores
misturados) — status só comercial (`RASCUNHO`/`CONFIRMADA`/`CANCELADA`).
`ItemVenda` guarda `custoUnitarioSnapshot` (base do futuro módulo de
Rentabilidade). `VendaPagamento` é 1:N — pagamento dividido (parte
dinheiro, parte cartão) é possível. `modoEntrega`/`enderecoEntrega`/
`valorFrete` vivem na própria `Venda` (adicionados na integração com
`core.entrega`, ver § 2.10) — antes disso o frete só existia embutido em
`acrescimo`, sem coluna própria.

`VendaService.registrar()` (PDV/balcão, monta+confirma numa chamada) e
`.registrarPendente()`+`.confirmarPagamento()` (checkout online — nasce
em RASCUNHO, só baixa estoque quando o pagamento confirma) são os dois
ciclos de vida. `RegistrarVendaCommand` é o contrato de entrada, usado
por três chamadores: `VendaController` (API `/api/v1/vendas`),
`PdvSyncService` (sync do caixa) e `CheckoutService` (checkout online).

**Ainda faltando**: uma tela `/gestao/vendas` (CRUD web) — hoje toda
venda nasce só por API/checkout/PDV, sem tela de admin pra criar ou
editar uma venda existente.

Testado por `VendaFlowIT`, `AuthAndProdutoFlowIT` (prova o fix de
`AccessDeniedException`→403 em vez de 500).

### 2.10 `core/entrega` — roteirização de motoboy

O módulo mais recente e mais rico do núcleo — construído nesta sessão a
partir de pesquisa em três SaaS anteriores do mesmo usuário
(MiniMercadinhoSaaS/"Rota das Praias", SaúdeMaisFarma, `multlektec`).

- **`EntregaRota`** — um lote de vendas (modo ENTREGA) que o motoboy
  percorre numa única ida-e-volta. Estados: `PLANEJADA` →
  `EM_EXECUCAO` → `CONCLUIDA`/`CANCELADA`. Guarda
  `percentualComissaoSnapshot` (capturado na criação — mudar a config
  depois não altera rotas já criadas) e a última posição de GPS
  conhecida (`localizacaoLatitude`/`Longitude`/`AtualizadaEm`).
- **`EntregaParada`** — uma venda dentro da rota. Snapshots de
  nome/endereço/frete/coordenadas tirados na criação (estabilidade de
  auditoria). Estados: `PENDENTE` → `A_CAMINHO` → `CHEGOU` →
  `ENTREGUE`, com ramos de falha (`TENTATIVA_SEM_SUCESSO`/`REAGENDAR`) e
  `CANCELADA`. Cada parada tem um `tokenRastreio` (UUID) — vira o link
  público `/rastreio/{token}` que o cliente recebe pra acompanhar a
  própria entrega, sem login.
- **`EntregaOcorrencia`** — relato de imprevisto/segurança que o motoboy
  registra a qualquer momento durante a rota (não só na parada atual),
  sem mudar o estado da rota nem da parada. `TipoOcorrencia` cobre desde
  logística (trânsito, endereço não encontrado, veículo, clima) até
  segurança real (cliente agressivo/ameaça, local inseguro,
  roubo/assalto, acidente, emergência médica) — os graves viram alerta
  ativo no admin até serem resolvidos.
- **`EntregaRotaService`** — orquestra tudo: roteirização (reaproveita
  `DeliveryRouteService`/`DeliveryRouteOptimizer`, TSP exato via
  Held-Karp, já existente e nunca reescrito), reivindicação atômica de
  rota (`UPDATE ... WHERE status = 'PLANEJADA'` — só um motoboy consegue
  assumir cada rota, defeito presente em todas as três referências
  analisadas), avanço sequencial obrigatório (só a próxima parada
  acionável pode ser alterada), cálculo de comissão (confirmada +
  projetada, nunca "zera" visualmente ao iniciar a rota) e reconciliação
  de dinheiro coletado em espécie (quanto o motoboy fica, quanto devolve
  pra loja).

UI: `/gestao/entregas` (admin — roteiriza, cancela, vê comissão por
motoboy, vê alertas de segurança) e `/gestao/motoboy` (motoboy — assume
rota, vê mapa Leaflet da rota com posição ao vivo, executa parada por
parada, relata imprevisto/emergência). Rastreio público em `/rastreio/{token}`.

Testado por `EntregaRotaFlowIT` (3 testes: fluxo principal ponta a ponta,
rastreio+reconciliação de dinheiro, ocorrências de segurança), usando um
Nominatim falso embutido (`com.sun.net.httpserver.HttpServer`) — sem rede
real em teste.

### 2.11 `core/auditoria`

`RegistroAuditoria` + `EventoAuditoria`, escrito por
`AuditoriaService.registrar()` via `@TransactionalEventListener`
(`BEFORE_COMMIT`) — uma linha de auditoria nunca existe pra uma mudança
que sofreu rollback. Eventos de negócio explícitos (ex.:
`VENDA_CANCELADA`), não um diff automático de coluna (decisão deliberada
contra Hibernate Envers, ver `docs/CONTEXTO.md`).

### 2.12 `application/` — serviços que cruzam módulos

- **`checkout/CheckoutService`** — orquestra o checkout do site: monta
  `Venda` via `VendaService`, calcula frete via `DeliveryPricingService`
  quando modo ENTREGA, dispara `MercadoPagoCheckoutService` pra
  pagamento online. Confirmação de dinheiro é manual (staff bate na
  loja).
- **`delivery/DeliveryRouteService`** — geocodificação (Nominatim, com
  cadeia de variantes pra endereço brasileiro malformado + backoff de
  rate-limit) e matriz de distância (OSRM real, com fallback haversine
  puro). `DeliveryRouteOptimizer` resolve o TSP exato via Held-Karp
  (até ~12 paradas). Reaproveitado por `core.entrega` sem reescrever.
- **`delivery/DeliveryPricingService`** — calcula o frete: tarifa mínima
  até um raio configurável, depois valor por km excedente, mais
  acréscimo de prioridade — tudo com default sensato mas
  **sobrescrevível por `AppSettingService`**, nunca um valor comercial
  fixo sem fuga.
- **`domain/financeiro/mercadopago/MercadoPagoCheckoutService`** —
  cobrança Pix/cartão via Mercado Pago pro canal online.
- **`core/settings/AppSettingService`** — key/value por tenant (schema
  do tenant, apesar do nome de pacote sugerir algo global) — usado por
  frete, Mercado Pago, comissão de motoboy, velocidade de rastreio.

### 2.13 `pdv/` (dentro de `platform/`) — lado servidor do PDV

`Terminal` (pareamento por API key de alta entropia, hash SHA-256
indexável), `PdvEventoRecebido` (idempotência por `evento_uuid` — replay
de rede nunca duplica venda), `PdvSyncService` (processa
`VENDA_REGISTRADA`/`VENDA_CANCELADA`), `PullResponse`+DTOs de sync
(produto/forma-pagamento/local-estoque, com cursor incremental por
`atualizado_em`+`id`). Autenticado por `TerminalAuthenticationFilter`
(API key do terminal, não usuário). Testado por `PdvSyncFlowIT`.

### 2.14 `platform/` (pacote, plano de controle)

`Empresa`/`IdentidadeUsuario` (schema `plataforma`), `IdentidadeService`
(resolve tenant a partir do e-mail no login — 409 com lista de empresas
se o e-mail existir em mais de uma, sem seletor de UI ainda),
`PlataformaEmpresaController` (provisionamento de empresa nova via API).

### 2.15 `tools/importador`

`RbpImportService` + `LegacyRbpReader` — migra dados do `rbp.db` (SQLite
do IMS antigo) pro Postgres novo, com relatório de reconciliação
(`RelatorioImportacao`) antes de qualquer corte real. Testado por
`RbpImportServiceIT`.

### 2.16 UI web — três camadas coexistindo

| Camada | Rotas | Tecnologia | Status |
|---|---|---|---|
| **Gestão nova** | `/gestao/**` | Thymeleaf server-rendered, sessão, RBAC real | Ativa — produtos, cadastros, papéis/usuários, entregas, motoboy |
| **Admin legado** | `/admin/pedidos`, `/admin/produtos` | Thymeleaf casca + fetch JS pro `/api/admin/**` | Ainda ativa (não migrada) — pedidos e produtos só-leitura |
| **Storefront** | `/`, `/produtos`, `/carrinho`, `/checkout`, `/login` | Thymeleaf + JS/localStorage, visual preto/dourado | Ativa, mas ainda não multi-tenant de verdade na camada MVC (ver Fase I do ROADMAP) |
| **Rastreio público** | `/rastreio/{token}` | Thymeleaf simples, sem login | Ativa (parte do módulo de entrega) |

O admin/motoboy antigo (`/admin/entregas`, `/motoboy` do port original)
foi **removido** por estar órfão — nenhum controller o servia.

---

## 3. `pdv/` — cliente de caixa (Java Swing, offline-first)

Pacote `mysquare.core` (herdado do `raj-blow-plast-producao`, ainda não
renomeado). Já bem avançado na migração pro modelo novo:

- **`LocalDb`** — SQLite local (`pdv-local.db`), tabelas `cache_*`
  (só-leitura, puxadas do servidor: produto, forma de pagamento, local de
  estoque) e locais de venda gravados no caixa.
- **`OutboxDao`/`EventoOutbox`/`SyncScheduler`** — outbox pattern: toda
  venda grava seu evento de sync na mesma transação SQLite; drenado em
  ordem, idempotente via `evento_uuid`, retry com backoff sem travar a
  UI.
- **`ApiClient`** — HTTP client pro servidor (`platform/`'s
  `/api/v1/pdv/**`).
- **`TelaPareamento`** — pareamento do terminal com uma API key.
- **`VendaScreen`** (novo) — tela de venda contra o modelo genérico.
- **`Sale`, `Production`, `Dispatch`, `ModifyProducts`, `AdminSaas`,
  `IMStart`** — telas/lógica do IMS antigo, pendentes de
  remoção/substituição (ver ROADMAP, "Limpeza final do PDV").

**Ainda faltando** (ver ROADMAP): login de operador no caixa
(`usuarioId` hoje sempre null), `AjusteEstoqueScreen`, recibo genérico,
Pix repontado pro servidor, relatórios locais lendo do modelo novo.

---

## 4. Banco de dados

Postgres, um schema fixo `plataforma` + um schema por tenant
(`empresa_NNN`), migrado por Flyway.

**Schema `plataforma`** (3 migrations): `empresa`, `identidade_usuario`
(índice global de login), `refresh_tokens`.

**Schema de tenant** (15 migrations, aplicadas em todo tenant existente):

| # | Migration | O que cria |
|---|---|---|
| 1 | `acesso` | `usuario`, `papel`, `permissao` e tabelas de junção |
| 2 | `auditoria` | `registro_auditoria` |
| 3 | `cadastro` | os 8 cadastros base |
| 4 | `produto` | `produto`, `produto_preco_historico` |
| 5 | `estoque` | `movimentacao_estoque` (ledger, trigger append-only), `saldo_estoque` |
| 6 | `fornecedor` | `fornecedor` |
| 7 | `compra` | `compra`, `item_compra` |
| 8 | `cliente` | `cliente` |
| 9 | `venda` | `venda`, `item_venda`, `venda_pagamento` |
| 10 | `venda_pagamento_gateway` | satélite 1:1 pra pagamento online |
| 11 | `app_settings` | configuração key/value por tenant |
| 12 | `pdv_terminal` | `terminal`, `pdv_evento_recebido` |
| 13 | `entrega` | `entrega_rota`, `entrega_parada`; `venda` ganha `modo_entrega`/`endereco_entrega`/`valor_frete` |
| 14 | `entrega_rastreio_e_cobranca` | posição de GPS da rota, coordenadas + token de rastreio + valor a cobrar na parada |
| 15 | `entrega_ocorrencia` | `entrega_ocorrencia` (relato de imprevisto/segurança) |

---

## 5. Testes

35 classes de teste (`mvn verify`, todas verdes). A maioria são
integration tests reais contra Postgres via Testcontainers — não mocks —
seguindo o padrão de provisionar um tenant novo por teste. Destaques:

- **`MultiTenancyIsolationIT`** — o teste mais crítico do projeto,
  prova isolamento entre tenants mesmo com reciclagem de conexão do pool.
- **`ArchitectureRulesTest`** — regras ArchUnit de longo prazo (ex.:
  proíbe voltar a usar SQLite).
- **`LedgerImutabilidadeIT`** — prova o trigger de imutabilidade do
  ledger de estoque via JDBC puro.
- **`EntregaRotaFlowIT`** — o mais extenso (3 testes), cobre o módulo de
  entrega inteiro ponta a ponta, incluindo geocodificação contra um
  Nominatim falso embutido (sem rede real em teste).
- Um `*GestaoFlowIT` por tela de `/gestao/**` (produtos, marcas, unidades,
  formas de pagamento, locais de estoque, condições de pagamento, tipos
  de movimentação, papéis, usuários) — MockMvc real, sessão real.
- **`GestaoAutorizacaoFlowIT`** / o teste extra em `AuthAndProdutoFlowIT`
  — provam que `@PreAuthorize` nega com 403 de verdade (não 500), tanto
  em `/gestao/**` quanto em `/api/v1/**`.

---

## 6. O que falta

Não duplicado aqui de propósito — `docs/ROADMAP.md` § "Próximos passos"
é a lista viva e priorizada. Resumo de uma linha: teste manual num
dispositivo real (PDV + `/gestao/**` + módulo de entrega no celular) é o
maior item pendente agora; depois disso, login de operador no PDV,
`AjusteEstoqueScreen`, recibo genérico, Pix no PDV, e as Fases E–I
(Financeiro, Orçamentos/Devoluções, Funcionários/Permissões fina,
Custos/Relatórios/Dashboard, Fiscal estrutural) ainda não começadas.
