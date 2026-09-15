# Deploy — subir a plataforma e provisionar a primeira empresa

Guia prático pra colocar `platform/` no ar: um servidor rodando Postgres
+ a aplicação + HTTPS automático, servindo uma ou mais empresas
(tenants). Não depende mais de nenhum arquivo físico de outra loja nem de
sincronização com um IMS — todo o estado vive no Postgres do próprio
compose.

> Versão anterior deste documento era um checklist específico pra colocar
> o antigo SaaS de uma loja só (SQLite compartilhado com um IMS Swing) no
> ar fisicamente na loja "Mini Mercadinho Rota". Reescrito do zero — nada
> daquele fluxo se aplica mais.

## 0. Cuidado com dados sensíveis

- `.env` **nunca é commitado** (já está no `.gitignore`) — confira com
  `git status` antes de qualquer commit se algo suspeito não entrou.
- Nunca cole um token/senha real em código ou mensagem de commit. Se
  acontecer por engano, corrija antes do push — depois de público é bem
  mais difícil de apagar de verdade.
- `PLATFORM_ADMIN_KEY`, `JWT_SECRET`, `MP_ACCESS_TOKEN`,
  `OAUTH_GOOGLE_CLIENT_SECRET`, `DUCKDNS_TOKEN`, `DB_PASSWORD` são todos
  segredos — cada um só existe no `.env` da máquina de produção, nunca no
  repositório.

## 1. Escolher a máquina e apontar o domínio

Qualquer máquina com Docker, ligada 24h, com as portas 80 e 443
alcançáveis da internet (encaminhadas no roteador, se for rede
residencial/comercial comum). Registre um domínio (ou use um serviço
gratuito como [DuckDNS](https://www.duckdns.org)) apontando pro IP público
dessa máquina.

Edite `infra/Caddyfile` — hoje aponta pro domínio de teste
`mercadinhorota.duckdns.org` (sobra do deploy original de referência);
troque pelo domínio real deste deploy antes de subir em produção.

## 2. Criar o `.env`

```bash
cp .env.example .env
```

Preencha (comentários de cada um estão no próprio `.env.example`):

- `DB_PASSWORD` — senha nova, não reaproveitar de outro projeto.
- `JWT_SECRET` — string longa e aleatória, nova.
- `PLATFORM_ADMIN_KEY` — string longa e aleatória, nova. Sem ela, o
  endpoint de criar empresa (§ 4) fica desligado (503) — nunca aberto por
  acidente.
- `MP_ACCESS_TOKEN` — token de produção do Mercado Pago, se algum tenant
  for usar Pix/cartão online.
- `OAUTH_GOOGLE_CLIENT_ID`/`SECRET` — só se for ativar "Entrar com
  Google" no storefront; sem isso o site funciona normalmente, só sem o
  botão.
- `DUCKDNS_DOMAIN`/`DUCKDNS_TOKEN` — só se estiver usando DuckDNS pro
  domínio (ver www.duckdns.org, token aparece no topo da página depois de
  logado). Com outro provedor de DNS, deixe os dois em branco e cuide da
  atualização de IP por fora.

## 3. Subir os containers

```bash
docker compose up -d --build
```

Sobe quatro serviços: `postgres` (o banco), `app` (a plataforma — roda as
migrations do schema `plataforma` sozinha ao iniciar), `duckdns`
(atualiza o IP do domínio a cada 5 min, se configurado) e `proxy` (Caddy
— pega o certificado HTTPS automaticamente na primeira visita ao
domínio).

Confira que subiu certo:

```bash
docker compose logs -f app       # esperar "Started LojaGenericaApplication"
docker compose logs postgres     # confirmar que aceitou conexões
docker compose logs duckdns      # se configurado, deve aparecer "atualizado" com data
```

Depois de alguns minutos, `https://SEU-DOMINIO` deve abrir com o cadeado
verde (certificado válido). Sem domínio configurado ainda, dá pra testar
direto por `http://IP-DA-MAQUINA:8080`.

## 4. Provisionar a primeira empresa

Não existe usuário nem empresa pré-cadastrados — o primeiro acesso nasce
por uma chamada à API de plataforma, autenticada pela
`PLATFORM_ADMIN_KEY` do passo 2:

```bash
curl -X POST https://SEU-DOMINIO/api/plataforma/empresas \
  -H "Content-Type: application/json" \
  -H "X-Platform-Admin-Key: SUA_PLATFORM_ADMIN_KEY" \
  -d '{
    "razaoSocial": "Nome da Empresa Ltda",
    "nomeFantasia": "Nome Fantasia",
    "subdominio": "nomefantasia",
    "administradorNome": "Seu Nome",
    "administradorEmail": "voce@example.com",
    "administradorSenha": "senha-forte-de-verdade"
  }'
```

Isso cria um schema Postgres novo pra essa empresa, roda as migrations
nele, e já cria o primeiro usuário com o papel `ADMINISTRADOR` (acesso
total). A resposta traz `schemaNome` (ex.: `empresa_001`) — guarde pra
referência, mas o dia a dia não precisa dele.

Se der `503`, `PLATFORM_ADMIN_KEY` não está configurada no `.env`. Se der
`401`, a chave enviada no header não bate com a do `.env`.

## 5. Primeiro login

Acesse `https://SEU-DOMINIO/gestao/login` com o e-mail/senha do
`administradorEmail`/`administradorSenha` do passo 4 — cai direto no
painel de gestão (`/gestao`), com acesso a todos os cadastros, produtos,
usuários/papéis e o módulo de entrega.

## 6. Empresas seguintes

Repita o passo 4 com dados diferentes (outro `subdominio`,
`administradorEmail`) pra cada nova empresa — cada uma ganha seu próprio
schema, completamente isolado das demais. Não há limite fixo, mas o
design atual foi pensado pra até ~200 tenants numa instância só (ver
`docs/CONTEXTO.md`).

## 7. Backup

Um schema por vez, não o banco inteiro:

```bash
docker compose exec postgres pg_dump -U lojagenerica -n empresa_001 lojagenerica > backup-empresa-001.sql
```

Troque `empresa_001` pelo `schemaNome` da empresa que quiser (retornado
no passo 4, ou consultável na tabela `plataforma.empresa`).
