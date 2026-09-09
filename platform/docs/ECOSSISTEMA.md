# Ecossistema Mini Mercadinho Rota

Este documento descreve o que é este projeto, como ele se encaixa com o
software que a loja já usa em produção, e o que cada peça faz — refletindo
o estado **atual** da implementação (não é o documento de planejamento
original; esse ficou registrado em `PROJETO-SAAS-MINI-MERCADINHO.md`, no
repositório `ParaisoPet`, como histórico da decisão de arquitetura).

Loja: **Mini Mercadinho Rota — O Melhor da Zona Sul** (João Pessoa/PB).
WhatsApp: (83) 99927-6452 e (83) 99635-2709.

---

## 1. Visão geral: dois softwares, um banco

```
                    ┌─────────────────────────────┐
                    │   rbp.db (SQLite, WAL)       │
                    │   C:\ims_files\rbp.db        │
                    │   ÚNICO arquivo, sem cópia   │
                    │   nem sincronização          │
                    └───────────┬───────────┬─────┘
                                │           │
                 lê/grava       │           │  lê/grava
                                │           │
                  ┌─────────────▼──┐   ┌────▼──────────────────┐
                  │ IMS (produção)  │   │ MiniMercadinhoSaaS     │
                  │ Java Swing      │   │ Spring Boot, Docker    │
                  │ roda no caixa   │   │ roda 24h na mesma rede │
                  └─────────────────┘   └────────────────────────┘
```

- **IMS** — o software que o lojista já usa todo dia no caixa (venda,
  estoque, Pix, recibo, relatórios). Continua exatamente no seu papel
  central; só recebeu duas adições pontuais (seção 4).
- **MiniMercadinhoSaaS** (este repositório) — o site de compras web +
  painel administrativo, novo, feito para rodar sozinho em Docker,
  ligado 24h, na mesma máquina/rede do PC da loja.

Os dois **leem e gravam no mesmo arquivo `rbp.db`**. Não existe rota de
sincronização, fila, replicação ou "banco legado" — é literalmente o
mesmo arquivo aberto por dois processos, com `PRAGMA journal_mode=WAL`
habilitado para tolerar leitura/escrita concorrente.

---

## 2. Os três repositórios

| Repositório | Papel | Branch de trabalho | Estado |
|---|---|---|---|
| `lectek/raj-blow-plast-producao` | IMS em **produção** — o que roda de verdade na loja hoje | `feature/chat-e-link-saas` | Alterações feitas **localmente**, não commitadas/enviadas — aguardando autorização explícita para ir à produção |
| `lectek/Inventory_Management` | Cópia de **teste** do IMS, usada para validar mudanças antes da produção | `teste` | Alterações de chat + link commitadas e enviadas (`origin/teste`) |
| `MiniMercadinhoSaaS` (este repo) | O SaaS novo (site + admin) | `main` | Versionado localmente, ainda sem remoto configurado |

Fluxo de mudança no IMS: qualquer alteração vai primeiro para
`Inventory_Management` (teste), é validada ali, e só depois — com
autorização separada do usuário — é replicada para
`raj-blow-plast-producao` (produção). Nunca o caminho inverso.



## 3. O que este SaaS faz — funções por módulo

### 3.1 Catálogo (`ImsProdutoRepository`, `CatalogoService`, `PublicProdutoController`)
Lê a tabela `products` do `rbp.db` diretamente (mesma tabela que o IMS usa
no caixa) — **não existe** uma cópia do catálogo em outra tabela. A
vitrine pública (`GET /api/public/produtos`) só mostra o que tem preço
(`pprice > 0`) e estoque (`pqt > 0`); o admin (`GET /api/admin/produtos`)
vê tudo, inclusive sem preço.

Chave do produto: **nome + cor + peso** (`pname`, `pclr`, `pwt`), a mesma
chave natural composta que o IMS usa — não há ID numérico.

**Markup do preço online**: `fetchVitrine`/`fetchPorChave`/`fetchPorCodigoBarras`
(o que o cliente vê e paga) aplicam um markup configurável sobre o `pprice`
presencial — chave `catalogo.markup_percentual` em `app_settings`, default
7%. `fetchTodos` (visão admin) continua mostrando o preço presencial exato,
sem markup. É assim que a taxa da plataforma (comissão de entrega +
mensalidade) fica embutida no preço sem aparecer separada no checkout —
decisão do dono pra não expor "taxa de serviço" pro cliente.

### 3.2 Checkout e pedidos (`CheckoutService`, `PublicCheckoutController`, `AdminPedidoController`)
- `POST /api/public/pedidos` — cliente monta o carrinho no site (guardado
  em `localStorage`, chave `mm_carrinho_v1`) e cria o pedido. Valida
  estoque contra o `rbp.db` em tempo real.
- Dois meios de pagamento: **Pix** (Mercado Pago) e **Dinheiro** — os dois
  valem tanto para retirada quanto para entrega (motoboy pode receber
  dinheiro na porta do cliente; ver 3.4.1 pra como isso é conciliado).
- Preço mostrado/cobrado no site já vem com um markup (~7%, configurável)
  sobre o preço presencial — cobre a comissão da entrega e a taxa da
  plataforma sem o cliente ver nenhuma taxa separada (ver 3.1).
- Pedido confirmado como pago em modo **Entrega** avança sozinho para
  `PRONTO_PARA_ENTREGA`, entrando na fila do módulo de entrega (3.4);
  em modo **Retirada** fica em `PAGO` (não passa por rota).
- Baixa de estoque só acontece quando o pedido é **confirmado como pago**
  — nunca na criação — para carrinho abandonado não travar estoque:
  - Pix: confirmação automática via webhook do Mercado Pago
    (`POST /webhooks/mercadopago`) ou polling do site
    (`POST /api/public/pedidos/{id}/sincronizar-pagamento`).
  - Dinheiro: **confirmação manual do staff**
    (`POST /api/admin/pedidos/{id}/confirmar-recebimento-dinheiro`),
    quando o cliente paga na loja.
- A baixa em si grava em `sold_records` — a **mesma tabela** que o IMS já
  usa nos relatórios de faturamento — no mesmo formato de timestamp
  (`dd/MM/yyyy HH:mm:ss`), para uma venda do site aparecer no caixa sem
  precisar mexer no IMS.
- Guarda de idempotência (`pedido.estoque_baixado`) evita descontar duas
  vezes se o Mercado Pago reenviar o mesmo webhook.

### 3.3 Pagamento — Mercado Pago (`MercadoPagoCheckoutService` e afins)
Pix + cartão de crédito/débito, com tratamento de status
(`approved/pending/in_process/rejected`). Token lido de `MP_ACCESS_TOKEN`
(variável de ambiente) ou de uma configuração salva (`pg.mp.access_token`)
— **sem InfinityPay**, decisão explícita do usuário.

### 3.4 Entrega (`AdminEntregaRouteService`, `DeliveryRouteService`, `DeliveryPricingService`, `DeliveryRouteOptimizer`, `PublicDeliveryEstimateService`, `AdminEntregasRestController`)
Módulo inteiro portado do ParaisoPet, sem redesenho:
- Geocodificação de endereço via Nominatim.
- Roteirização/matriz de distância via OSRM.
- Otimização de rota (Held-Karp/TSP, 3 a 10 pedidos por rota).
- Precificação por distância (`DeliveryPricingService`) — **sem raio
  grátis** (diferente do ParaisoPet original): até 3km cobra a tarifa
  mínima cheia de R$5; depois disso, soma R$2 por km excedente em cima
  da mínima. Entrega prioritária soma mais R$15 no frete padrão. Os
  quatro valores são configuráveis via `app_settings`
  (`entrega.frete.raio_minimo_km`, `entrega.frete.valor_minimo`,
  `entrega.frete.valor_km_excedente`, `entrega.frete.prioritario.acrescimo`).
- Ciclo completo: elegibilidade → preview → criação de rota → despacho →
  acompanhamento (localização, confirmação de parada, insucesso) →
  código de confirmação de entrega para o cliente.

### 3.4.1 Motoboy — comissão e conciliação de dinheiro (`calcularGanhoMotoboy`)
Comissão do motoboy é um **percentual do frete** de cada pedido entregue
(`AdminUserEntity.percentualComissao`, default 70% — o resto, 30%, é do
desenvolvedor). Como o motoboy pode coletar dinheiro na porta do cliente,
ele já fica com a própria comissão desse dinheiro em vez de esperar um
pagamento separado; `GET /api/motoboy/entregas/rotas/{id}/ganho` mostra:
- **Comissão total da rota** — % sobre o frete de todas as entregas
  concluídas (Pix, cartão ou dinheiro, não importa).
- **Dinheiro coletado** — soma do total dos pedidos pagos em dinheiro.
- **Devolver pro mercadinho** — dinheiro coletado menos a comissão
  correspondente só às entregas em dinheiro (o motoboy já reteve essa
  parte, só devolve o resto).
- **Comissão não coberta por dinheiro** — a parte da comissão que vem de
  entregas pagas por fora (Pix/cartão), que o motoboy nunca teve na mão;
  precisa ser acertada com a loja por outro meio.

Testado de ponta a ponta com 3 entregas (2 em dinheiro, 1 em Pix) e
conferido o cálculo à mão — achado e corrigido um bug na primeira versão
(a "comissão não coberta" comparava os números errados).

### 3.5 Autenticação do admin e do motoboy (módulo `auth.jwt.*`, `AdminAuthController`, `AdminJwtAuthFilter`)
JWT (access + refresh token) portado do ParaisoPet/SaúdeMaisFarma —
mesmo padrão de "controle de acesso" que os outros SaaS do usuário já
usam. `POST /api/auth/login` autentica **tanto admin quanto motoboy**
(mesma tabela `admin_users`, campo `role` diferencia os dois);
`AdminJwtAuthFilter` protege `/api/admin/**` e `/api/motoboy/**`, sem
sessão/cookie — só Bearer token, com checagem de role por caminho: um
token `role=MOTOBOY` não passa em `/api/admin/**` e vice-versa (antes
disso, qualquer token válido tinha acesso total ao painel admin —
corrigido junto com a conta de motoboy).

**Quem cria os acessos**: o SaaS **não se auto-cadastra** como admin
nem motoboy fora de desenvolvimento (`AdminUserSeeder` só roda com
`@Profile("dev")`). Em produção, o único jeito de criar ou trocar
qualquer um dos dois é pela tela **"Acessos do site"** dentro do
próprio IMS — grava direto em `admin_users` no `rbp.db` compartilhado
(role "ADMIN" ou "MOTOBOY", com % de comissão pro motoboy), senha em
bcrypt (jBCrypt no IMS, mesmo formato `$2a$` que o
`BCryptPasswordEncoder` do site valida no login). Decisão do dono da
loja: só quem tem acesso físico ao IMS consegue criar qualquer um
desses acessos.

### 3.5.1 App do motoboy (`/motoboy`, `MotoboyEntregasController`)
Não é um app nativo nem Java Swing (Swing não roda em celular) — é uma
página web mobile-first, aberta no navegador do próprio celular, login
compartilhado com o admin (mesmo `/api/auth/login`, redireciona pra
`/motoboy` em vez de `/admin/pedidos` conforme a role do token).
- `/motoboy` — minhas rotas: as já atribuídas a mim + as disponíveis
  pra pegar (sem motoboy ainda, status Planejada/Despachada).
- `/motoboy/rotas/{id}` — mostra só a **parada de agora**
  (`DriverRouteView.proximaParada`, não a lista inteira — de propósito,
  um app de entregador deve focar numa coisa de cada vez), botão
  "Cheguei" → "Confirmar entrega", e o card "Quanto eu recebo" (3.4.1).
- `MotoboyEntregasController` reaproveita os mesmos métodos de
  `AdminEntregaRouteService` que o painel admin usa — só muda quem tem
  permissão de chamar.

### 3.6 Login e cadastro do cliente (`SecurityConfig`, `CustomerAuthController`, `GoogleOAuth2UserService`)
Diferente do admin: aqui é Spring Security completo (sessão + cookie),
adaptado do Copa Insider (CopadoMundo) — mas só para as rotas de
cliente, o admin continua isolado no mecanismo acima.
- `/login` e `/cadastro` — e-mail+senha (form login clássico), mais
  `/minha-conta` (autenticado) e `/logout`.
- **Login com Google** via `spring-boot-starter-oauth2-client`, ativado
  só com o profile `oauth2` (`OAUTH_GOOGLE_CLIENT_ID`/`SECRET`) — sem
  isso configurado, o app sobe normalmente e só não mostra o botão do
  Google (nunca tenta resolver as variáveis à toa).
- Não existe tabela de roles: todo `CustomerEntity` autenticado é
  simplesmente `ROLE_CLIENTE`.
- **Cadastro "reivindica" contas de convidado**: se o cliente já
  comprou como convidado no checkout (ou já entrou uma vez via Google),
  a conta existe com uma senha-placeholder
  (`CustomerEntity.SENHA_PLACEHOLDER_PREFIX`) que nunca bate com bcrypt
  nenhum; `/cadastro` detecta isso e define a senha real em vez de
  rejeitar como e-mail duplicado.
- Deliberadamente **sem** a camada extra de robustez que o Copa Insider
  tem (CSP customizado, rate-limit de login, remember-me persistente)
  — pode entrar depois se precisar, mas não é essencial pro login em
  si e cada peça a mais é mais coisa pra manter.

### 3.6.1 Painel admin visual (`AdminMvcController`, `/admin/**`)
Diferente do login do cliente, o admin **não** passa pelo Spring
Security: as páginas em `/admin/**` são só a casca HTML, e todo dado
vem por `fetch` direto pra `/api/admin/**` com o token JWT guardado no
`localStorage` do navegador (`static/js/admin/auth.js`) — mesmo
mecanismo de sempre, só que agora com telas de verdade em vez de só
API:
- `/admin/login` — autentica em `/api/auth/login`, guarda o token.
- `/admin/pedidos` — lista os pedidos com botão "Confirmar
  recebimento" pros pedidos em Dinheiro ainda Abertos (ver 3.2).
- `/admin/produtos` — lista só leitura; estoque/preço/cadastro
  continuam sendo gerenciados no IMS ("Modificar produtos"), não
  duplicado aqui.
- `/admin/entregas` — pedidos elegíveis pra rota (checkbox + endereço
  de origem), pré-visualização e criação de rota, lista de rotas
  recentes. `/admin/entregas/rotas/{id}` — detalhe da rota: iniciar,
  registrar chegada em cada parada, confirmar entrega (com forma de
  pagamento recebida), regenerar código. Gestão de outros usuários
  admin ainda não tem tela.

Usa o layout "admin-page" já portado do ParaisoPet/HotelPet (paleta
cinza/slate, deliberadamente diferente do preto/dourado da vitrine).

### 3.7 Atendimento ao cliente — WhatsApp, não um chat próprio
Um chat próprio dentro do site chegou a ser cogitado (tabela
`chat_messages`, tela `Chat.java` no IMS lendo essa tabela), mas nunca
existiu nenhuma tela no site pra o cliente **escrever** — só o lado do
IMS pra ler, que ficaria sempre vazio. Removido: atendimento é só pelo
WhatsApp de verdade (os dois números já aparecem no cabeçalho/rodapé do
site) — o botão "WhatsApp Web" na tela inicial do IMS abre
`web.whatsapp.com` no navegador, pra quem está no caixa responder sem
precisar do celular.

### 3.8 Frontend (Thymeleaf + CSS/JS estático)
Vitrine (`/`), lista de produtos (`/produtos`), carrinho (`/carrinho`) e
checkout (`/checkout`) — visual preto/dourado batendo com a logo real da
loja. Grande parte do CSS base (tokens, layout, componentes) foi
reaproveitada do ecossistema ParaisoPet e adaptada; carrinho/checkout são
JS novo (fetch + `localStorage`, sem sessão de servidor).

---

## 4. As adições no IMS

Únicas mudanças aceitas no software de produção — tudo o mais nele
permanece como está:

1. **Botão "Loja Online"** na tela Home/menu, abre no navegador a URL do
   admin do SaaS (`SAAS_ADMIN_URL`, configurável em
   `ims_files/config.properties` ou pela tela de Configurações).
2. **Botão "WhatsApp Web"** na tela inicial — ver seção 3.7.
3. **Tela "Acessos do site"** — cria acesso de admin ou de motoboy, ver
   seção 3.5.

---

## 5. Por que Docker local (não nuvem)

SQLite não aceita conexão de rede — só funciona "mesmo banco" se o
processo que lê/grava estiver perto o suficiente do arquivo. Por isso o
SaaS roda em container **na mesma máquina ou rede local** do PC da loja
(bind mount do `rbp.db` real), ligado 24h, exposto à internet via proxy
reverso/túnel — não um deploy em nuvem tradicional sem relação de rede
com a loja.

**Domínio**: `mercadinhorota.duckdns.org` (DuckDNS, gratuito) — decidido
em vez de comprar um domínio próprio por enquanto. Já cadastrado como URI
de redirecionamento no Google OAuth. Falta, quando houver acesso físico à
rede da loja: script de atualização de IP do DuckDNS (a rede da loja
provavelmente tem IP dinâmico), e o proxy reverso/túnel de fato apontando
pra esse domínio (HTTPS incluso).

---

## 6. O que já foi validado (testes de ponta a ponta)

- Catálogo público lendo preço/estoque real do `rbp.db`.
- Criação de pedido com validação de estoque.
- Confirmação de pedido em Dinheiro pelo admin → baixa de estoque →
  grava em `sold_records` no formato do IMS → idempotente (não desconta
  duas vezes se confirmado de novo).
- Pedido em Pix sem token configurado erra de forma limpa (409), como
  esperado neste ambiente de desenvolvimento.
- Corrigido bug sistêmico de timestamp: o driver SQLite gravava datas
  como número (epoch millis) em vez de texto, o que quebrava **qualquer**
  leitura JPA de pedido/cliente (`Error parsing time stamp`) assim que
  existisse mais de uma operação. Corrigido via `date_class=TEXT` na URL
  JDBC antes de haver dado real em produção.
- Acesso admin criado via IMS (hash jBCrypt) autentica certinho no
  login do site — testado ponta a ponta.
- Cadastro de cliente → login → `/minha-conta` → logout, e o fluxo de
  "reivindicar" uma conta criada como convidado no checkout, definindo
  senha pela primeira vez — testado ponta a ponta. `/api/admin/**` e
  `/api/public/**` seguem funcionando sem regressão com o Spring
  Security novo no meio.
- Painel admin (`/admin/login` → `/admin/pedidos` → `/admin/produtos`)
  testado com um browser de verdade via Chrome DevTools Protocol —
  login preenchendo o formulário, clique real no botão "Confirmar
  recebimento", conferido que o pedido muda de status na tela e o
  estoque desce no `rbp.db`, e logout limpando a sessão.
- Painel de entregas testado de ponta a ponta com endereços reais de
  João Pessoa/PB: checkout com entrega → confirmar pagamento → pedido
  elegível → criar rota (3 paradas, geocodificação real via Nominatim,
  TSP otimizado) → iniciar → registrar chegada → confirmar entrega de
  cada parada → rota fecha sozinha como Concluída. Achou e corrigiu 2
  bugs reais nesse processo (pedido em Entrega nunca ficava elegível;
  faltava a rota HTTP de "registrar chegada").
- Endereço real da loja configurado (`Rua Sibipiruna, 135, João
  Pessoa, PB`) — testado que o frete calculado ficou plausível
  (R$3,84 a R$13,28 num pedido real, contra o antigo endereço vago
  que geocodificava pra Cuité/PB, a ~180km de distância).
- Conta de motoboy testada de ponta a ponta: criada com role/comissão
  pela tela do IMS → login retorna token com a role certa → bloqueado
  em `/api/admin/**` (403) e liberado em `/api/motoboy/**` (200), e
  vice-versa pro admin → app mobile renderizando com dados reais
  (`/motoboy`, `/motoboy/rotas/{id}`) → cálculo de comissão/conciliação
  conferido à mão.

## 7. Pendências conhecidas

- **`sold_records.timestamp` como chave primária pode colidir**: duas
  vendas confirmadas no mesmo segundo (`dd/MM/yyyy HH:mm:ss`, sem
  fração) causam `UNIQUE constraint failed` e a segunda venda falha
  silenciosamente sem dar baixa. Schema e formato são do próprio IMS
  (`Db.sellProduct` usa o mesmo padrão) — não foi alterado aqui porque
  mudar essa tabela compartilhada exige decisão e teste coordenados
  com o IMS, não é algo pra mudar unilateralmente pelo lado do SaaS.
  Risco baixo com um caixa manual, mas real com confirmações
  automáticas (webhook do Pix) ou ações em lote no admin.
- **Painel admin — gestão de usuários**: só falta essa área; pedidos,
  produtos e entregas já têm tela.
- **Google OAuth em modo de teste**: projeto próprio criado no Google
  Cloud ("Mini Mercadinho Rota", separado de outros produtos do usuário
  para não misturar marca na tela de consentimento) e credenciais reais
  já configuradas — mas o app está restrito a usuários de teste
  cadastrados manualmente até ser publicado (exige domínio com política
  de privacidade pública, ainda pendente).
- **Só falta a ida física à loja pra ativar**: script de atualização de
  IP do DuckDNS e proxy HTTPS (Caddy) já estão prontos no
  `docker-compose.yml` e testados localmente (build da imagem, subida
  dos três serviços juntos, `caddy validate`, chamada real à API do
  DuckDNS) — só faltam as portas 80/443 encaminhadas no roteador da
  loja pra funcionar de verdade. Checklist completo do que fazer lá em
  `docs/DEPLOY-LOJA.md`: escolher a máquina que hospeda o container,
  pegar o token real do Mercado Pago, criar o `.env` de produção, subir
  os containers, testar concorrência real IMS+container no mesmo
  `rbp.db`.
- **Push da produção do IMS**: mudanças de chat/link/acesso admin estão
  prontas e testadas no repositório de teste; ir para
  `raj-blow-plast-producao` depende de autorização separada do usuário.
