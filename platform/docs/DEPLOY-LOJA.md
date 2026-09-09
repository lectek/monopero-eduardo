# Checklist — ida à loja para colocar o SaaS no ar

Tudo que dava pra preparar sem estar lá já está pronto e testado (domínio,
proxy HTTPS, atualização de IP, containers). O que falta só existe fisicamente
na loja. Siga na ordem — cada passo depende do anterior.

## 0. Preparar os repositórios git

Três repositórios estão envolvidos, cada um num estado diferente:

| Repositório | Papel | Estado |
|---|---|---|
| `lectek/MiniMercadinhoSaaS` | Este projeto (o site) | Commitado local; **sem remoto ainda** — precisa ser criado e enviado antes de dar pra clonar em qualquer outra máquina |
| `lectek/Inventory_Management` (branch `teste`) | IMS de teste | Já no GitHub, atualizado, nada a fazer |
| `lectek/raj-blow-plast-producao` | IMS de **produção** (o que roda na loja de verdade) | Mudanças prontas só **localmente, sem commit** — só vai pro GitHub quando você autorizar explicitamente (passo 10) |

Passos pra deixar o SaaS clonável:
1. Criar o repositório `lectek/MiniMercadinhoSaaS` no GitHub, **privado** (mesmo padrão do `raj-blow-plast-producao` — é o ambiente real da loja, mesmo sem segredo no código o histórico mostra detalhes de negócio).
2. `git remote add origin https://github.com/lectek/MiniMercadinhoSaaS.git`
3. `git push -u origin main` — envia os commits já feitos, nada novo é criado.

Depois disso, a máquina que for rodar o Docker na loja só precisa de:
```bash
git clone https://github.com/lectek/MiniMercadinhoSaaS.git
```

### Cuidado com dados sensíveis a partir daqui

Antes de criar/enviar qualquer coisa, conferido (histórico inteiro, não só o
estado atual):
- `.env` — **nunca foi commitado**, em nenhum momento da história do repositório.
- Client ID/Secret reais do Google, token do DuckDNS, token real do Mercado Pago — **não aparecem em nenhum arquivo rastreado nem em nenhum commit**.
- `dev-data/rbp-sample.db` (o único banco versionado) é uma amostra vazia — sem produto, cliente ou venda real.

Regras pra manter isso verdade daqui pra frente:
- **Nunca** `git add .env` — ele já está no `.gitignore`, mas confira com `git status` antes de qualquer commit se algo suspeito não entrou.
- **Nunca** commitar o `rbp.db` real da loja — o `docker-compose.yml` só monta esse arquivo como volume (bind mount), ele nunca entra na imagem nem no repositório.
- Se algum dia colar um token/senha real numa mensagem de commit ou código por engano, avise antes de eu enviar qualquer coisa — dá pra corrigir antes do push, depois de público fica bem mais difícil de apagar de verdade.

## 1. Escolher a máquina que roda o Docker

Precisa ficar ligada 24h, na mesma rede do PC que roda o IMS. Duas opções:
- O próprio PC do caixa (se tiver Docker Desktop e recursos sobrando).
- Um mini-servidor/PC extra na mesma rede.

Anote o IP local dessa máquina e, se possível, configure IP fixo (ou reserva
DHCP no roteador) — se o IP local mudar, o encaminhamento de porta (passo 4)
quebra.

## 2. Pegar o token real do Mercado Pago

Está em `C:\ims_files\config.properties` na máquina da loja (o mesmo que o
IMS já usa para o Pix). Copie o valor — vai entrar no `.env` no passo 5.

## 3. Copiar o `rbp.db` real pro caminho que o Docker vai montar

Confirme o caminho exato da pasta `ims_files` (ex.: `C:\ims_files`) — é o que
entra em `SAAS_DB_HOST_PATH` no `.env`. Não precisa copiar nada, só apontar
pra pasta real; o container lê/grava o arquivo original via bind mount.

## 4. Encaminhar as portas 80 e 443 no roteador

No painel do roteador da loja, redirecione as portas **80** e **443** pro IP
local da máquina do passo 1. Sem isso, o Caddy (proxy HTTPS) não consegue
completar o desafio do Let's Encrypt e o site fica inacessível de fora.

## 5. Criar o `.env` de produção

Na pasta do projeto, na máquina que vai rodar o Docker:

```bash
cp .env.example .env
```

Preencha (ver `.env.example` pros comentários de cada um):
- `SAAS_DB_HOST_PATH` — caminho real do passo 3 (ex.: `C:\ims_files`)
- `SAAS_DB_FILE=rbp.db`
- `JWT_SECRET` — qualquer string longa aleatória nova (não reaproveitar de
  outro projeto)
- `MP_ACCESS_TOKEN` — token do passo 2
- `DUCKDNS_TOKEN` — pegue em [www.duckdns.org](https://www.duckdns.org) (já
  logado, aparece no topo da página) — mantém `mercadinhorota.duckdns.org`
  apontando pro IP da loja sozinho
- `OAUTH_GOOGLE_CLIENT_ID` / `OAUTH_GOOGLE_CLIENT_SECRET` — se for ativar
  login com Google agora (ver passo 7); senão deixe em branco por enquanto

## 6. Subir os containers

```bash
docker compose up -d --build
```

Isso sobe três serviços: `saas` (o site), `duckdns` (atualiza o IP do
domínio sozinho a cada 5 min) e `proxy` (Caddy — pega o certificado HTTPS
automaticamente na primeira visita a `https://mercadinhorota.duckdns.org`).

Confira que subiu certo:
```bash
docker compose logs -f saas    # esperar "Started MiniMercadinhoSaasApplication"
docker compose logs duckdns    # deve aparecer "atualizado" com data, não "nao configurado"
```

Depois de alguns minutos, `https://mercadinhorota.duckdns.org` deve abrir
com o cadeado verde (certificado válido).

## 7. Criar o primeiro acesso admin

Pelo IMS (não pelo site — ver `docs/ECOSSISTEMA.md` seção 3.5): abra a tela
**"Acessos do site"**, crie seu login de Admin. Se for criar motoboys agora
também, é a mesma tela.

## 8. Testar concorrência real

Com o IMS aberto no caixa e o site no ar:
- Bipe uma venda no IMS enquanto o site está sendo usado (alguém navegando
  ou fazendo um pedido de teste) — confirme que nenhum dos dois trava com
  "database is locked".
- Faça um pedido de teste pelo site (retirada, Pix ou dinheiro) e confirme
  que aparece no IMS/relatórios.

## 9. Publicar o app do Google (opcional, se for usar login com Google)

Com o site já acessível em `https://mercadinhorota.duckdns.org` (a política
de privacidade precisa estar publicamente acessível):
- Google Cloud Console → Google Auth Platform → Público-alvo → **Publicar
  app**. Escopos usados são básicos (e-mail/perfil), não deve exigir
  revisão manual do Google.

## 10. Decidir sobre a produção do IMS

As mudanças no IMS (chat, botão "Loja Online", "Acessos do site") já estão
prontas e testadas no repositório de teste (`Inventory_Management`, branch
`teste`). Só vão para o `raj-blow-plast-producao` (o que realmente roda na
loja) quando você autorizar explicitamente.
