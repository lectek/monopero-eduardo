# Mini Mercadinho Rota — SaaS de compras web

Plataforma de e-commerce (Spring Boot) para a loja **Mini Mercadinho Rota —
O Melhor da Zona Sul**. Roda ao lado do IMS (`lectek/raj-blow-plast-producao`,
Java Swing) e lê/grava o **mesmo banco SQLite** (`rbp.db`) que o IMS já usa
em produção — não é uma cópia nem uma sincronização assíncrona.

Visão completa do ecossistema (os dois softwares, os três repositórios,
funções de cada módulo) em [`docs/ECOSSISTEMA.md`](docs/ECOSSISTEMA.md).

## Stack

- Java 21, Spring Boot 3.5.11, Thymeleaf, Spring Data JPA
- SQLite (mesmo arquivo do IMS) via `hibernate-community-dialects`
- JWT próprio (portado do ParaisoPet) para login do admin
- Mercado Pago (Pix + cartão) para checkout online
- Módulo de entrega com geocodificação/roteirização (Nominatim + OSRM),
  também portado do ParaisoPet

## Como rodar localmente

Sem tocar no banco real da loja — usa uma cópia de amostra do `rbp.db`:

```bash
./mvnw spring-boot:run
# ou: mvn spring-boot:run
```

Sobe em `http://localhost:8080` com o perfil `dev`, apontando pra
`dev-data/rbp-sample.db` (banco de amostra, mesmo schema do IMS, vazio).

Login do admin padrão em dev (definido em `AdminUserSeeder`, só quando a
tabela `admin_users` está vazia):

- E-mail: `admin@minimercadinho.local`
- Senha: `admin123`

## Rotas principais

- Vitrine: `/`
- Produtos: `/produtos`
- Carrinho: `/carrinho`
- Checkout: `/checkout`
- Login/cadastro do cliente: `/login`, `/cadastro`, `/minha-conta`
- Painel admin: `/admin/login`, `/admin/pedidos`, `/admin/produtos`, `/admin/entregas`
- App do motoboy (mobile): `/motoboy` (mesmo login do admin, redireciona pela role)
- Admin (API): `/api/admin/**` — Motoboy (API): `/api/motoboy/**`
- Webhook Mercado Pago: `/webhooks/mercadopago`

## Produção

Roda em Docker, 24h, na mesma máquina/rede do PC da loja — `docker-compose.yml`
monta o `rbp.db` real como volume (`SAAS_DB_HOST_PATH`/`SAAS_DB_FILE`).
Copie `.env.example` para `.env` (nunca commitado) e preencha `JWT_SECRET`,
`MP_ACCESS_TOKEN` e, se quiser login com Google, `OAUTH_GOOGLE_CLIENT_ID`/
`OAUTH_GOOGLE_CLIENT_SECRET` (aí é só acrescentar `,oauth2` em
`SPRING_PROFILES_ACTIVE`). O admin **não** se cria por variável de ambiente
em produção — só pela tela "Acesso admin do site" dentro do IMS (ver
`docs/ECOSSISTEMA.md`).

## O que foi reaproveitado do ParaisoPet (`hospedapet`)

Módulo de entrega, autenticação JWT, checkout Mercado Pago e boa parte do
CSS/layout do site foram portados e adaptados do ParaisoPet — não
reescritos do zero. Onde a adaptação foi mais que cosmética (ex.: o
catálogo usa a chave natural nome+cor+peso do `rbp.db` em vez de um ID
numérico), isso está documentado como comentário na própria classe.
