package br.com.minimercadinho.saas.application.service.checkout;

import br.com.minimercadinho.saas.adapters.outbound.ims.ImsProdutoRepository;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.CustomerEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.ItemPedidoEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.PedidoEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.CustomerRepository;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.PedidoRepository;
import br.com.minimercadinho.saas.application.service.delivery.DeliveryPricingService;
import br.com.minimercadinho.saas.application.support.DeliveryCodeGenerator;
import br.com.minimercadinho.saas.application.view.DeliveryQuoteVM;
import br.com.minimercadinho.saas.domain.catalogo.Produto;
import br.com.minimercadinho.saas.domain.enums.ModoEntrega;
import br.com.minimercadinho.saas.domain.enums.StatusPedido;
import br.com.minimercadinho.saas.domain.enums.TipoPagamento;
import br.com.minimercadinho.saas.domain.financeiro.mercadopago.MercadoPagoCheckoutService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o fluxo de compra do site: valida o carrinho contra o mesmo
 * banco (rbp.db) que o IMS usa, cria o pedido e aciona o Mercado Pago.
 *
 * Não tem equivalente direto pra "portar" do ParaisoPet — o CheckoutController
 * de lá é baseado em sessão HTTP com carrinho salvo em atributos de sessão
 * (858 linhas, muito específico daquele fluxo MVC/Thymeleaf); aqui o
 * checkout é uma API JSON stateless, então essa orquestração é nova. O que
 * É reaproveitado: DeliveryPricingService (frete) e MercadoPagoCheckoutService
 * (pagamento), ambos portados do ParaisoPet.
 *
 * Baixa de estoque só acontece quando o pagamento é confirmado (mesma regra
 * que o IMS já usa no Pix dele: "só dá baixa após o pagamento confirmado"),
 * nunca na criação do pedido — assim carrinho abandonado não trava estoque.
 */
@Service
public class CheckoutService {

    private final ImsProdutoRepository imsProdutoRepository;
    private final CustomerRepository customerRepository;
    private final PedidoRepository pedidoRepository;
    private final DeliveryPricingService deliveryPricingService;
    private final MercadoPagoCheckoutService mercadoPagoCheckoutService;

    public CheckoutService(
            ImsProdutoRepository imsProdutoRepository,
            CustomerRepository customerRepository,
            PedidoRepository pedidoRepository,
            DeliveryPricingService deliveryPricingService,
            MercadoPagoCheckoutService mercadoPagoCheckoutService
    ) {
        this.imsProdutoRepository = imsProdutoRepository;
        this.customerRepository = customerRepository;
        this.pedidoRepository = pedidoRepository;
        this.deliveryPricingService = deliveryPricingService;
        this.mercadoPagoCheckoutService = mercadoPagoCheckoutService;
    }

    public record ItemCarrinho(String nome, String cor, String peso, int quantidade) {
    }

    public record CriarPedidoRequest(
            String customerNome,
            String customerEmail,
            String customerTelefone,
            List<ItemCarrinho> itens,
            ModoEntrega modoEntrega,
            String enderecoEntrega,
            TipoPagamento tipoPagamento
    ) {
    }

    public record CheckoutResultado(
            Long pedidoId,
            BigDecimal total,
            BigDecimal valorFrete,
            String status,
            String checkoutUrl,
            String pixQrCode,
            String pixQrCodeBase64,
            String pixTicketUrl
    ) {
    }

    @Transactional
    public CheckoutResultado criarPedido(CriarPedidoRequest request) {
        if (request.itens() == null || request.itens().isEmpty()) {
            throw new IllegalArgumentException("O carrinho está vazio.");
        }
        // Dinheiro na entrega É permitido nesta loja (decisão do dono, diferente da regra
        // original do ParaisoPet que bloqueava isso) — o motoboy coleta o dinheiro e a
        // conciliação (quanto é comissão dele, quanto volta pro caixa) é calculada em
        // AdminEntregaRouteService.calcularGanhoMotoboy.

        CustomerEntity cliente = resolverOuCriarCliente(request);

        PedidoEntity pedido = new PedidoEntity();
        pedido.setCliente(cliente);
        pedido.setData(LocalDateTime.now());
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setTipoPagamento(request.tipoPagamento());
        pedido.setModoEntrega(request.modoEntrega());

        BigDecimal totalItens = BigDecimal.ZERO;
        for (ItemCarrinho itemCarrinho : request.itens()) {
            if (itemCarrinho.quantidade() < 1) {
                throw new IllegalArgumentException("Quantidade inválida para " + itemCarrinho.nome());
            }
            Produto produto = imsProdutoRepository
                    .fetchPorChave(itemCarrinho.nome(), nullToEmpty(itemCarrinho.cor()), nullToEmpty(itemCarrinho.peso()))
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + itemCarrinho.nome()));
            if (!produto.disponivelNaVitrine()) {
                throw new IllegalArgumentException("Produto indisponível: " + itemCarrinho.nome());
            }
            if (produto.quantidade() < itemCarrinho.quantidade()) {
                throw new IllegalArgumentException("Estoque insuficiente para " + itemCarrinho.nome());
            }

            ItemPedidoEntity item = new ItemPedidoEntity();
            item.setProdutoNome(produto.nome());
            item.setProdutoCor(produto.cor());
            item.setProdutoPeso(produto.peso());
            item.setProdutoCodigoBarras(produto.codigoBarras());
            item.setQuantidade(itemCarrinho.quantidade());
            item.setPrecoUnitario(BigDecimal.valueOf(produto.preco()));
            pedido.addItem(item);

            totalItens = totalItens.add(item.getSubtotal());
        }

        BigDecimal valorFrete = BigDecimal.ZERO;
        if (request.modoEntrega() == ModoEntrega.ENTREGA) {
            if (request.enderecoEntrega() == null || request.enderecoEntrega().isBlank()) {
                throw new IllegalArgumentException("Endereço de entrega é obrigatório para modo ENTREGA.");
            }
            DeliveryQuoteVM cotacao = deliveryPricingService.quoteForAddress(request.enderecoEntrega());
            if (!cotacao.available()) {
                throw new IllegalArgumentException(cotacao.summary());
            }
            valorFrete = cotacao.standardShippingAmount();
            pedido.setEnderecoEntrega(request.enderecoEntrega());
            pedido.setCodigoEntrega(DeliveryCodeGenerator.nextCode());
            pedido.setCodigoEntregaGeradoEm(LocalDateTime.now());
        }

        pedido.setTotal(totalItens.add(valorFrete));
        pedido.setValorFrete(valorFrete);
        pedidoRepository.save(pedido);

        MercadoPagoCheckoutService.CheckoutPreferenceResult checkout = null;
        if (isPagamentoOnline(request.tipoPagamento())) {
            checkout = mercadoPagoCheckoutService.ensureCheckoutForPedido(pedido).orElse(null);
        }

        return new CheckoutResultado(
                pedido.getId(),
                pedido.getTotal(),
                valorFrete,
                pedido.getStatus().name(),
                checkout == null ? null : checkout.checkoutUrl(),
                checkout == null ? null : checkout.pixQrCode(),
                checkout == null ? null : checkout.pixQrCodeBase64(),
                checkout == null ? null : checkout.pixTicketUrl()
        );
    }

    /** Chamado depois que o cliente paga (polling do site) ou pelo webhook do Mercado Pago. */
    @Transactional
    public void confirmarPagamento(Long pedidoId, String paymentId) {
        PedidoEntity pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));
        mercadoPagoCheckoutService.syncPaymentForPedido(pedido, paymentId);
        baixarEstoqueSeNecessario(pedido);
    }

    /**
     * Confirma o recebimento de um pedido em DINHEIRO (staff bate na loja quando
     * o cliente paga na retirada/entrega) — sem isso, um pedido em DINHEIRO
     * nunca sai de ABERTO, porque só o Mercado Pago (Pix/cartão) tem um evento
     * de pagamento automático que dispara essa transição.
     */
    @Transactional
    public void confirmarRecebimentoDinheiro(Long pedidoId) {
        PedidoEntity pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + pedidoId));
        if (pedido.getTipoPagamento() != TipoPagamento.DINHEIRO) {
            throw new IllegalStateException("Pedido " + pedidoId + " não é pagamento em dinheiro.");
        }
        if (pedido.getStatus() == StatusPedido.ABERTO) {
            pedido.setStatus(StatusPedido.PAGO);
            pedido.setPagamentoRecebidoEm(LocalDateTime.now());
        }
        baixarEstoqueSeNecessario(pedido);
    }

    @Transactional
    public void processarWebhook(String type, String topic, String paymentId) {
        MercadoPagoCheckoutService.PaymentSyncResult resultado =
                mercadoPagoCheckoutService.handleWebhookNotification(type, topic, paymentId);
        if (!resultado.updated() || resultado.pedidoId() == null) {
            return;
        }
        pedidoRepository.findById(resultado.pedidoId()).ifPresent(this::baixarEstoqueSeNecessario);
    }

    /**
     * Idempotente: {@code PedidoEntity.estoqueBaixado} evita descontar duas vezes se o webhook repetir.
     *
     * Também avança pedidos em ENTREGA de PAGO para PRONTO_PARA_ENTREGA — sem isso,
     * eles nunca apareciam nos "pedidos elegíveis" do módulo de entrega
     * (AdminEntregaRouteService.ROUTABLE_ORDER_STATUSES exige PRONTO_PARA_ENTREGA/
     * SAIU_PARA_ENTREGA, e nada mais fazia essa transição). Pedidos em RETIRADA
     * ficam em PAGO mesmo — retirada não passa pelo módulo de rota.
     */
    private void baixarEstoqueSeNecessario(PedidoEntity pedido) {
        if (pedido.isEstoqueBaixado() || pedido.getStatus() != StatusPedido.PAGO) {
            return;
        }
        for (ItemPedidoEntity item : pedido.getItens()) {
            imsProdutoRepository.venderEstoque(
                    item.getProdutoNome(),
                    nullToEmpty(item.getProdutoCor()),
                    nullToEmpty(item.getProdutoPeso()),
                    item.getQuantidade(),
                    item.getPrecoUnitario().doubleValue()
            );
        }
        pedido.setEstoqueBaixado(true);
        if (pedido.getModoEntrega() == ModoEntrega.ENTREGA) {
            pedido.setStatus(StatusPedido.PRONTO_PARA_ENTREGA);
        }
        pedidoRepository.save(pedido);
    }

    private CustomerEntity resolverOuCriarCliente(CriarPedidoRequest request) {
        if (request.customerEmail() == null || request.customerEmail().isBlank()) {
            throw new IllegalArgumentException("E-mail do cliente é obrigatório.");
        }
        return customerRepository.findByEmailIgnoreCase(request.customerEmail().trim())
                .orElseGet(() -> {
                    CustomerEntity novo = new CustomerEntity();
                    novo.setNome(request.customerNome());
                    novo.setEmail(request.customerEmail());
                    novo.setTelefone(request.customerTelefone());
                    // Cliente criado no checkout ainda não tem senha própria — define login futuro,
                    // não bloqueia a compra. Uma senha aleatória evita null em senha_hash NOT NULL.
                    novo.setSenhaHash(CustomerEntity.SENHA_PLACEHOLDER_PREFIX + java.util.UUID.randomUUID());
                    return customerRepository.save(novo);
                });
    }

    private boolean isPagamentoOnline(TipoPagamento tipoPagamento) {
        return tipoPagamento == TipoPagamento.PIX
                || tipoPagamento == TipoPagamento.CARTAO_CREDITO
                || tipoPagamento == TipoPagamento.CARTAO_DEBITO
                || tipoPagamento == TipoPagamento.BOLETO;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
