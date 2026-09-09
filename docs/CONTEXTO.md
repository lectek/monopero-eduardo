# Contexto do projeto — sistema do Eduardo

## Quem é o Eduardo e o que ele precisa

O Eduardo é dono de negócios de varejo (hoje, 3 empresas — nomes ainda não
definidos, tratadas como `empresa-1`/`empresa-2`/`empresa-3` até lá) e quer
um sistema de gestão comercial: controle de estoque, caixa/PDV, compras,
financeiro, relatórios — o tipo de coisa que hoje só existe, comprovada em
produção, pra **um** desses negócios (a loja "Mini Mercadinho Rota").

Ele quer poder acessar todas as áreas do sistema de todas as suas empresas
(o "dono" enxerga tudo); cada funcionário tem acesso só ao que precisa pro
seu trabalho.

## O que já existe hoje (fora deste repositório) e por que isso importa

Três repositórios irmãos, na mesma pasta pai (`~/Área de trabalho/`), formam
o par de software já validado em produção pra a loja "Mini Mercadinho Rota":

| Repositório | Papel |
|---|---|
| `raj-blow-plast-producao` | IMS (Inventory Management System) em **produção** — Java Swing, roda no computador do caixa físico da loja |
| `Inventory_Management` | Cópia de **teste** do mesmo IMS — onde mudanças são validadas antes de ir pra produção |
| `MiniMercadinhoSaaS` | O SaaS (Spring Boot) — site de compras + painel admin + app do motoboy, rodando em Docker na mesma rede da loja |

O IMS (Swing) e o SaaS (Spring Boot) **leem e gravam o mesmo arquivo
SQLite** (`rbp.db`) — não há sincronização, é literalmente o mesmo arquivo
aberto por dois processos. Essa arquitetura funciona bem pra 1 loja, mas
tem 3 propriedades que **não** servem pro pedido do Eduardo:

1. SQLite não aceita conexão de rede — só funciona porque os dois processos
   rodam fisicamente perto do arquivo, na mesma rede local da loja.
2. O modelo de dados é **específico do negócio da Rota** — produto
   identificado por nome+cor+peso (sem ID numérico), sem conceito de
   fornecedor, compra, orçamento, devolução, financeiro, papéis de acesso
   configuráveis etc.
3. Não existe conceito de "empresa" no código — é software para 1 loja só.

## O pivot: por que não é "clonar 3 vezes"

A primeira ideia levada a este projeto foi clonar o par IMS+SaaS da Rota
três vezes (um por empresa do Eduardo), cada cópia com seu SQLite local
independente — mecanicamente simples, mas resultaria em **3 produtos
separados**, cada um ainda hardcoded pro "formato Rota" (bebidas, entrega
por moto, etc.), sem nada realmente compartilhado além do código-fonte.

O Eduardo então trouxe uma especificação completa ("LOJA GENÉRICA"): ele
quer um **núcleo comercial genérico e configurável** — Cadastros, Produtos,
Clientes, Fornecedores, Compras, Estoque, Vendas/PDV, Orçamentos,
Devoluções, Financeiro, Fiscal, Funcionários, Usuários/Permissões,
Custos/Rentabilidade, Relatórios, Dashboard, Configurações — que sirva
qualquer tipo de comércio (material de construção, ferragens, autopeças,
papelaria etc. são só *exemplos* de uso, nunca hipóteses fixadas no
código). Cada uma das 3 empresas do Eduardo é um **tenant** desse núcleo
único, com dados isolados de verdade — não 3 cópias de software.

Decisão confirmada com o Eduardo: **adaptar o código existente** (não
reescrever do zero) — evoluir o `MiniMercadinhoSaaS` pro backend
multiempresa genérico, e o Swing do `raj-blow-plast-producao` pro cliente
de PDV **offline-first** (o caixa físico não pode parar quando a internet
cai — por isso ele continua sendo um app desktop, não uma tela web).

## Onde este repositório entra

`monopero-eduardo/` é onde o sistema novo nasce — cópia de trabalho dos
dois códigos-fonte acima, com **histórico git próprio** (os repositórios
originais continuam intocados, como referência de produção):

```
monopero-eduardo/
├── platform/   # clonado de MiniMercadinhoSaaS — vira o backend multiempresa
│               # (Spring Boot, Postgres, schema por tenant)
├── pdv/        # clonado de raj-blow-plast-producao — vira o cliente PDV
│               # offline-first (Java Swing, sincroniza com platform/ via API)
└── docs/       # este diretório
```

## Decisões arquiteturais já fechadas (não relitigar sem motivo novo)

1. **Banco**: Postgres, **schema separado por tenant** (não
   `tenant_id` em tabela compartilhada, não banco físico separado — ver
   `docs/ROADMAP.md` Fase A pra justificativa).
2. **PDV continua desktop, offline-first** — grava localmente sempre,
   sincroniza com o backend quando há internet (arquitetura de outbox —
   ver Fase D).
3. Nenhum dado comercial de exemplo (preço, margem, categoria, produto)
   pode existir em produção — só em ambiente de dev, fisicamente separado
   do classpath de produção.
4. O núcleo (`core/`) nunca pode depender de módulos opcionais
   (`modules/entrega`, `modules/lojaonline`) — regra imposta por teste
   (ArchUnit), não só por convenção.

## Onde ver o plano completo

`docs/ROADMAP.md`, neste mesmo diretório — passo a passo de todas as fases,
com status atualizado. É a versão "viva" (dentro do repo, versionada) do
plano original aprovado, que também existe em
`/home/alex/.claude/plans/bubbly-baking-donut.md` (fora do repo, específico
desta ferramenta) — em caso de divergência entre os dois, `docs/ROADMAP.md`
é a fonte da verdade, porque é o que evolui junto com o código.
