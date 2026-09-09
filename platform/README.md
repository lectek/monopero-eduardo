# Loja Genérica — plataforma comercial multiempresa

Núcleo comercial genérico e configurável (Cadastros, Produtos, Estoque,
Compras, Vendas/PDV, Financeiro, Fiscal, Relatórios etc. — ver
`/home/alex/.claude/plans/bubbly-baking-donut.md` pro plano de fases
completo). Nasceu adaptando o SaaS single-tenant "Mini Mercadinho Rota"
(histórico em [`docs/ECOSSISTEMA.md`](docs/ECOSSISTEMA.md) e
[`docs/DEPLOY-LOJA.md`](docs/DEPLOY-LOJA.md), preservados como referência)
pra virar multiempresa de verdade: **schema Postgres separado por tenant**,
sem lógica fixa de nenhum ramo de comércio específico.

O par desktop deste sistema — o PDV/caixa offline-first — vive em `../pdv`
(adaptado de `raj-blow-plast-producao`, Java Swing).

## Stack

- Java 21, Spring Boot 3.5, Thymeleaf, Spring Data JPA
- Postgres, schema por tenant (Flyway rodado manualmente por schema — ver
  `br.com.lojagenerica.multitenancy`, Fase A do plano)
- JWT próprio (portado do ParaisoPet) — sem toggle "desligar autenticação",
  incompatível com isolamento multiempresa
- Mercado Pago (Pix + cartão) para checkout online, módulo de entrega
  (geocodificação/roteirização) — ambos portados do ParaisoPet, agora
  opt-in por tenant

## Como rodar localmente

```bash
docker compose up -d postgres   # só o banco, pra rodar a app fora do container
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Sobe em `http://localhost:8080` contra Postgres local (`DB_*` em
`application.yml`, defaults funcionam com o `docker-compose.yml` deste
repo). Na Fase 0 o banco ainda não tem schema de tenant nenhum (isso entra
na Fase A, via Flyway) — o objetivo desta fase é só a fundação (Postgres,
Flyway, testes, remoção do acoplamento com SQLite/IMS).

## Produção

`docker-compose.yml` sobe Postgres + app + Caddy (HTTPS) + DuckDNS
(atualização de IP dinâmico). Copie `.env.example` para `.env` (nunca
commitado) e preencha `DB_PASSWORD`, `JWT_SECRET`, e o que mais for
aplicável por tenant.

## Reaproveitado de outros projetos do ecossistema

Módulo de entrega, autenticação JWT, checkout Mercado Pago e boa parte do
CSS/layout do site vieram do ParaisoPet — não foram reescritos do zero.
Onde a adaptação foi mais que cosmética, o histórico está em
`docs/ECOSSISTEMA.md`. O acoplamento antigo com a chave natural
nome+cor+peso do IMS (`rbp.db`) está sendo removido nesta reescrita — ver
o plano de fases pra detalhes (Fase A, módulo Produtos).
