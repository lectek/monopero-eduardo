# Funcionalidades — Loja Genérica (plataforma multiempresa)

Resumo do que o sistema faz hoje, por área, do ponto de vista de quem usa
(dono, funcionário, motoboy, cliente do site) — não de como o código está
organizado. Para a estrutura técnica (módulos, classes, banco de dados),
ver `docs/MAPA-APLICACAO.md`; para por quê o projeto existe e as decisões
de arquitetura, ver `docs/CONTEXTO.md`, ambos na raiz do repositório.

> Esta é uma reescrita completa: a versão anterior deste arquivo descrevia
> o antigo "Mini Mercadinho Rota" (SaaS de uma loja só, banco SQLite
> compartilhado com um IMS Swing) — um projeto anterior a este pivot pra
> multiempresa. Nada daquilo se aplica mais.

## Multiempresa

O sistema serve várias empresas ao mesmo tempo, cada uma com seus
próprios dados completamente isolados (schema de banco separado por
empresa) — não é uma cópia de software por cliente, é uma instância só
com várias contas independentes por baixo. Cada empresa tem seu(s)
próprio(s) usuário(s), cadastros, estoque, vendas e configurações; nada é
compartilhado entre empresas.

## Cadastros

Marca, unidade de medida (com conversão entre unidades — ex.: "1 caixa =
12 unidades"), forma de pagamento, local de estoque, condição de
pagamento, tipo de movimentação de estoque, categoria (hierárquica) e
definição de atributo. Nenhum vem pré-cadastrado com exemplo — uma
empresa nova começa com essas listas vazias.

## Produtos

Cadastro só exige nome e unidade de medida — cor, peso, marca, código de
barras são opcionais, não uma estrutura fixa. Todo ajuste de preço fica
registrado no histórico (quem mudou, de quanto pra quanto, quando).

## Estoque

Toda entrada e saída de estoque fica registrada permanentemente — nunca é
possível editar ou apagar uma movimentação já feita; uma correção sempre
gera uma nova movimentação compensatória. O saldo atual é sempre a soma
de tudo que já aconteceu, nunca um número solto que alguém possa
"ajustar" por fora.

## Fornecedores e compras

Cadastro de fornecedores e registro de compras (com rateio de frete e
outros custos entre os itens). Confirmar uma compra dá entrada no
estoque e atualiza o custo médio do produto numa operação só.

## Clientes e vendas

Cadastro de cliente exige só o nome (cliente de balcão não precisa de
e-mail nem documento). Uma venda pode ser paga com mais de uma forma ao
mesmo tempo (parte em dinheiro, parte no cartão, por exemplo).

## Loja online

Vitrine, carrinho e checkout pro cliente comprar pela internet — retirada
na loja ou entrega em casa, pagamento via Pix/cartão (Mercado Pago) ou em
dinheiro (confirmado manualmente por um funcionário quando o pagamento
chega). Cliente pode se cadastrar com e-mail/senha ou entrar com a conta
Google, e acompanhar o histórico dos próprios pedidos.

## Entrega e motoboy

Quando uma venda é feita em modo entrega, ela fica disponível pra um
funcionário organizar numa **rota**: escolhe várias vendas prontas pra
sair no mesmo dia, o sistema calcula sozinho a melhor ordem de visita
(geocodifica os endereços e resolve a rota mais curta) e mostra a
distância e o mapa antes de confirmar.

Um motoboy vê as rotas disponíveis e assume uma (só um consegue pegar
cada rota, mesmo que dois cliquem ao mesmo tempo) — antes de começar, já
sabe quanto vai ganhar de comissão sobre o frete daquela rota inteira.
Durante a rota:

- Só consegue agir na parada da vez, em ordem — não dá pra pular ou
  confirmar entregas fora de sequência.
- Vê um mapa com todas as paradas numeradas e a própria posição em tempo
  real.
- Pode relatar um imprevisto a qualquer momento (trânsito, endereço não
  encontrado, veículo com problema) ou uma situação de segurança de
  verdade (cliente agressivo, ameaça, local perigoso, roubo, acidente) —
  isso não atrasa nem cancela a entrega; ocorrências graves aparecem na
  hora como alerta pro admin, até serem resolvidas.
- Ao entregar, registra a forma de pagamento recebida (o sistema avisa
  se for diferente do combinado) e, se recebeu em dinheiro, já sabe
  quanto fica de comissão e quanto precisa devolver pra loja.

O cliente que está esperando a entrega recebe um link (pra mandar por
WhatsApp) onde acompanha, sem precisar de login, quantas entregas faltam
antes da dele e um tempo estimado de chegada.

## Usuários, papéis e permissões

Cada funcionário tem um papel (ex.: "Vendedor", "Motoboy") com um
conjunto específico de permissões — o que ele pode ver e fazer no
sistema é exatamente o que o papel permite, nada além. O dono
(papel "Administrador") sempre tem acesso a tudo. Papéis e permissões são
configuráveis por empresa; nenhum papel de exemplo vem criado
automaticamente.

## Caixa (PDV) offline

O caixa físico da loja roda como um programa próprio no computador,
continua funcionando mesmo sem internet, e sincroniza as vendas com o
servidor assim que a conexão volta — nenhuma venda feita no caixa se
perde por causa de uma queda de internet.

## O que ainda não existe

Login de operador no caixa (toda venda do PDV ainda não identifica quem
vendeu), emissão de nota fiscal, contas a pagar/receber, orçamentos,
devoluções, e relatórios de rentabilidade/dashboard. Lista completa e
priorizada em `docs/ROADMAP.md`.
