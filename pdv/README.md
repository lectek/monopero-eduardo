# Raj Blow Plast — Ambiente de Produção (IMS)

**Repositório privado.** Este é o código que **de fato roda no computador
da loja (Mini Mercadinho Rota — O Melhor da Zona Sul)** — diferente de
[`lectek/Inventory_Management`](https://github.com/lectek/Inventory_Management),
que é o ambiente de teste/desenvolvimento genérico.

## Versão atual: v1.0.8 (tag `v1.0.8`, commit `3265d8f`)

Última versão lançada e instalada no computador da loja
(`C:\ControleEstoque\programa\IMS-1.0.6.jar` — o nome do arquivo ficou
histórico, o conteúdo é o desta tag). Cada deploy fez backup do `.jar`
anterior ao lado (`IMS-1.0.6.jar.bak-pre-<mudança>-<timestamp>`).

## Status

Este repositório contém o código real capturado do computador da loja em
02/09/2026 (scanner, recibo, Pix, feitos direto na loja fora do git) mais
tudo desenvolvido depois, já aplicado e testado na loja:

**Capturado da loja em 02/09/2026:**
- **Leitor de código de barras** na tela de Venda: campo dedicado que,
  ao bipar e apertar Enter, adiciona automaticamente 1 unidade do
  produto encontrado à venda em andamento (sem precisar digitar
  quantidade nem clicar em nenhum botão). Também preenche os campos
  Produto/Cor/Peso para conferência visual.
- **Dropdowns de Cor e Peso dependentes do Produto** selecionado na tela
  de Venda — só mostram combinações realmente cadastradas em estoque
  (`Db.fetchColoursForProduct` / `Db.fetchWeightsForProduct`), evitando
  vender uma combinação inexistente.
- **Impressão de recibo** (`Receipt.java`) ao confirmar uma venda — usa a
  impressora padrão configurada no Windows, formata como cupom de 80mm.
  Se nenhuma impressora estiver configurada, avisa mas não impede a
  venda.
- **Venda atômica multi-item** (`Db.sellProducts`): confirmar uma venda
  com várias linhas dá baixa em todas dentro de uma única transação — se
  uma linha falhar, nenhuma é aplicada.
- **Pagamento via Pix** (`MercadoPagoPixClient.java` / `PixPaymentDialog.java`):
  ao confirmar uma venda, escolha entre Dinheiro ou Pix (gera cobrança no
  Mercado Pago, mostra QR code + código copia-e-cola, só dá baixa no
  estoque após o pagamento confirmado). **`MP_ACCESS_TOKEN` já está
  configurado com a conta de produção real da loja** — Pix está ativo,
  não é mais uma pendência.
- **Tela de Configurações**: colar/trocar o `MP_ACCESS_TOKEN` do Pix
  direto pela interface, sem editar `config.properties` manualmente.

**Desenvolvido depois, direto neste repositório (03/09/2026):**
- **Bug do `Db.sellProduct`/`Db.sellProducts` corrigido**: o código nunca
  avançava o cursor do `ResultSet` (faltava `rs.next()`), então todo
  Despacho manual — e, dependendo do caminho, toda Venda — falhava e não
  descontava estoque.
- **Aviso de produto duplicado** na tela de Produção: cadastrar um
  produto (nome+cor+peso) que já existe agora avisa e pergunta quantas
  unidades adicionar, em vez de mesclar silenciosamente com a quantidade
  do formulário de "novo produto".
- **Troco no pagamento em dinheiro**: a venda pergunta o valor recebido,
  mostra o troco na confirmação e imprime a forma de pagamento + troco
  no cupom (`Receipt.print` ganhou uma linha extra pra isso).
- **Faturamento e relatórios (portado do `Inventory_Management` v1.0.7/v1.0.8)**:
  `sold_records` ganhou a coluna `pprice` (preço no momento da venda);
  novas telas **Vendas por dia**, **Calendário de vendas** (grade
  mensal, clique no dia lista as vendas) e **Início/Home** (atalhos pra
  todas as telas + faturamento de hoje/mês/ano/total, com o botão de
  Configurações incluído — a v1.0.8 do repo de teste não tem essa tela).
- **Backfill de preço histórico**: as 127 vendas feitas antes do
  `pprice` existir tinham preço nulo (faturamento apareceria zerado).
  `Db.backfillSoldRecordsPrices` roda uma vez (idempotente, só mexe em
  linhas ainda nulas) e preenche com o preço atual do produto no
  cadastro — aproximado para produtos que mudaram de preço desde a
  venda, mas melhor que R$ 0,00. 123 das 127 vendas foram preenchidas;
  as 4 restantes são de 2 produtos sem preço cadastrado.
- **Identidade visual**: nome da empresa corrigido de "Raj Blow Plast"
  (nome legado de fabricação) para "Mini Mercadinho Rota o Melhor da
  Zona Sul" (janela + tela Início); logo da loja (`logo.png`) substitui
  o título de texto na tela Início e aparece em miniatura no rodapé de
  toda tela, acima do crédito `@lektec.tech`.
- **Tema visual novo**: paleta vermelho paixão / azul marinho / branco
  bebê (`Theme.java`), substituindo o verde/creme original.

Baseado na v1.0.6 do `Inventory_Management` (tradução completa pt-BR,
tela de Venda, catálogo redesenhado) — ver o histórico desse repositório
para o que veio de lá. Este repositório privado é a fonte da verdade do
que roda na loja; mudanças futuras no computador da loja devem ser
commitadas aqui.

## Pendências conhecidas

- 2 produtos vendidos no histórico não têm preço cadastrado (`ovos
  metade` e `sardinha coqueiro`) — as vendas antigas deles ficam de fora
  do backfill de faturamento até o preço ser cadastrado em Modificar
  produtos.

## Requisitos, instalação e configuração

Mesma coisa que o `Inventory_Management` — ver o README de lá para
detalhes de instalação, `config.properties`, requisitos de Java etc. A
única diferença é que o `.jar` publicado a partir deste repositório já
inclui scanner, dropdowns dependentes, impressão de recibo e pagamento
Pix. Para ativar o Pix de verdade, edite `C:\ims_files\config.properties`
na loja e preencha `MP_ACCESS_TOKEN` com o access token de produção do
Mercado Pago (nunca um token de teste/sandbox, e nunca commitado no git).

**Nunca** commitar `ims_files/rbp.db` (dados reais de estoque da loja) —
já está no `.gitignore`.

## Building

```bash
mvn clean package
```

Gera `target/IMS-<version>.jar`, um fat JAR com todas as dependências
(driver SQLite incluso) e `mysquare.core.IMStart` como classe principal.
