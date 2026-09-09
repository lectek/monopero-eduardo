# Funcionalidades — Mini Mercadinho Rota (SaaS)

Resumo do que o sistema faz, por área. Para detalhes técnicos (classes,
endpoints, decisões de arquitetura), ver `docs/ECOSSISTEMA.md`.

## Loja online (cliente)

- **Vitrine e catálogo** — produtos, preço e estoque lidos direto do mesmo
  banco que o IMS usa no caixa (sem cadastro duplicado, sem risco de vender
  o que já acabou).
- **Carrinho** — guardado no navegador do cliente, sobrevive a recarregar a
  página.
- **Checkout** — retirada na loja ou entrega, pagamento por Pix (Mercado
  Pago) ou dinheiro.
- **Cadastro e login** — e-mail/senha ou "Entrar com Google". Quem compra
  como convidado pode voltar depois e criar senha pra reivindicar a própria
  conta.
- **Minha conta** — dados cadastrais e histórico de todos os pedidos feitos.
- **Política de Privacidade e Termos de Serviço** — páginas próprias,
  publicadas no site.

## Pagamento

- **Pix** — via Mercado Pago, confirmação automática.
- **Dinheiro** — na retirada ou na entrega; confirmado manualmente (pelo
  admin, ou pelo próprio motoboy na hora da entrega).
- Toda venda pelo site desconta o estoque e aparece no relatório de
  faturamento do IMS, igual a uma venda de balcão.

## Frete e entrega

- **Cálculo automático de frete** por distância real até o endereço do
  cliente: tarifa mínima de R$5 até 3km, depois R$2 por km excedente;
  entrega prioritária soma mais R$15.
- **Roteirização** — o sistema monta a melhor ordem de visita quando tem
  várias entregas no mesmo dia (otimização de rota, 3 a 10 pedidos por
  vez).
- **Código de confirmação de entrega** — cada pedido gera um código que o
  cliente informa na hora de receber.

## App do motoboy (celular)

- Login próprio (mesma conta que o admin cria pelo IMS, com o papel
  "Motoboy").
- Lista das rotas atribuídas + rotas disponíveis pra pegar.
- Tela da entrega atual: endereço, abrir no Maps, marcar "cheguei",
  confirmar a entrega (com forma de pagamento recebida).
- **Comissão automática**: motoboy recebe um percentual do frete de cada
  entrega (padrão 70%, resto é da plataforma). Quando ele mesmo recebe em
  dinheiro na porta do cliente, já fica com a própria comissão na hora e o
  app mostra quanto precisa devolver pro caixa da loja.

## Painel administrativo (dono/equipe)

- **Login** próprio, separado do cliente.
- **Pedidos** — lista de todos os pedidos, com botão pra confirmar
  recebimento de pagamentos em dinheiro.
- **Produtos** — visão de estoque e preço (cadastro continua sendo feito no
  IMS).
- **Entregas** — escolher pedidos prontos, montar e criar rotas, acompanhar
  cada entrega até a confirmação.

## Atendimento ao cliente

- Direto pelo **WhatsApp** (os dois números já aparecem no site).
- Um botão dentro do IMS abre o WhatsApp Web no navegador, pra quem está
  no caixa responder sem precisar do celular.

## Preço e taxa da plataforma

- O preço mostrado no site tem um acréscimo (~7%, configurável) sobre o
  preço da loja física — cobre a manutenção da plataforma e a fatia da
  entrega, sem aparecer como taxa separada pro cliente.

## Segurança e acesso

- **Cliente**: login próprio (e-mail/senha ou Google).
- **Admin e motoboy**: contas criadas exclusivamente pelo IMS (o site nunca
  se auto-cadastra como admin ou motoboy) — um controla acesso ao painel,
  outro à área de entregas, sem que um consiga usar o acesso do outro.
- Site funciona 24h, ligado direto na rede da loja, com HTTPS automático no
  domínio próprio.
