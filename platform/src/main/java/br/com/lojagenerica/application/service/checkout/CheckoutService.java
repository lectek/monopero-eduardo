package br.com.lojagenerica.application.service.checkout;

import br.com.lojagenerica.application.service.delivery.DeliveryPricingService;
import br.com.lojagenerica.application.view.DeliveryQuoteVM;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.NaturezaFormaPagamento;
import br.com.lojagenerica.core.parceiro.Cliente;
import br.com.lojagenerica.core.parceiro.ClienteRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.venda.CanalVenda;
import br.com.lojagenerica.core.venda.RegistrarVendaCommand;
import br.com.lojagenerica.core.venda.TipoPagamentoOnline;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaPagamentoGateway;
import br.com.lojagenerica.core.venda.VendaPagamentoGatewayRepository;
import br.com.lojagenerica.core.venda.VendaRepository;
import br.com.lojagenerica.core.venda.VendaService;
import br.com.lojagenerica.domain.enums.ModoEntrega;
import br.com.lojagenerica.domain.financeiro.mercadopago.MercadoPagoCheckoutService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o checkout do site — reescrito na Fase C pra montar
 * {@link Venda} (via {@link VendaService#registrarPendente}) em vez de
 * {@code PedidoEntity}. Continua reaproveitando
 * {@link DeliveryPricingService} (frete) e {@link MercadoPagoCheckoutService}
 * (pagamento). O módulo de roteirização de entrega (rotas/motoboy) foi
 * removido nesta mesma reescrita — dependia de {@code PedidoEntity} e de
 * {@code AdminUserEntity} (não migrado pro modelo novo de acesso ainda);
 * vira módulo próprio numa fase futura, contra Venda+Usuario (ver
 * docs/ROADMAP.md). Até lá, "modo ENTREGA" só cobra o frete calculado —
 * não gera rota nem código de confirmação.
 *
 * Baixa de estoque só acontece quando o pagamento é confirmado (a venda
 * fica em RASCUNHO até lá — ver VendaService), nunca na criação, pra
 * carrinho abandonado não travar estoque.
 */
@Service
public class CheckoutService {

    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final VendaRepository vendaRepository;
    private final VendaService vendaService;
    private final VendaPagamentoGatewayRepository gatewayRepository;
    private final DeliveryPricingService deliveryPricingService;
    private final MercadoPagoCheckoutService mercadoPagoCheckoutService;

    public CheckoutService(
            ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository,
            LocalEstoqueRepository localEstoqueRepository,
            VendaRepository vendaRepository,
            VendaService vendaService,
            VendaPagamentoGatewayRepository gatewayRepository,
            DeliveryPricingService deliveryPricingService,
            MercadoPagoCheckoutService mercadoPagoCheckoutService
    ) {
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.vendaRepository = vendaRepository;
        this.vendaService = vendaService;
        this.gatewayRepository = gatewayRepository;
        this.deliveryPricingService = deliveryPricingService;
        this.mercadoPagoCheckoutService = mercadoPagoCheckoutService;
    }

    public record ItemCarrinho(Long produtoId, BigDecimal quantidade) {
    }

    public record CriarPedidoRequest(
            String customerNome,
            String customerEmail,
            String customerTelefone,
            List<ItemCarrinho> itens,
            ModoEntrega modoEntrega,
            String enderecoEntrega,
            NaturezaFormaPagamento tipoPagamento
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

        Cliente cliente = resolverOuCriarCliente(request);
        LocalEstoque local = resolverLocalEstoquePadrao();

        List<RegistrarVendaCommand.ItemVendaCommand> itensCmd = new ArrayList<>();
        for (ItemCarrinho itemCarrinho : request.itens()) {
            if (itemCarrinho.quantidade() == null || itemCarrinho.quantidade().signum() <= 0) {
                throw new IllegalArgumentException("Quantidade inválida para o produto " + itemCarrinho.produtoId());
            }
            Produto produto = produtoRepository.findById(itemCarrinho.produtoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + itemCarrinho.produtoId()));
            if (produto.getStatus() != Produto.StatusProduto.ATIVO) {
                throw new IllegalArgumentException("Produto indisponível: " + produto.getNome());
            }
            if (produto.getPrecoVenda() == null || produto.getPrecoVenda().signum() <= 0) {
                throw new IllegalArgumentException("Produto sem preço de venda: " + produto.getNome());
            }
            itensCmd.add(new RegistrarVendaCommand.ItemVendaCommand(
                    produto.getId(), itemCarrinho.quantidade(), produto.getUnidadeEstoque().getId(),
                    produto.getPrecoVenda(), null));
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
        }

        Venda venda = vendaService.registrarPendente(new RegistrarVendaCommand(
                UUID.randomUUID(), CanalVenda.ONLINE, local.getId(), cliente.getId(), null, null, null,
                null, valorFrete, itensCmd, List.of()));

        MercadoPagoCheckoutService.CheckoutPreferenceResult checkout = null;
        if (isPagamentoOnline(request.tipoPagamento())) {
            VendaPagamentoGateway gateway = gatewayRepository.save(
                    new VendaPagamentoGateway(venda, mapParaTipoOnline(request.tipoPagamento())));
            checkout = mercadoPagoCheckoutService.ensureCheckoutForVenda(venda, gateway).orElse(null);
        }

        return new CheckoutResultado(
                venda.getId(),
                venda.getTotal(),
                valorFrete,
                venda.getStatus().name(),
                checkout == null ? null : checkout.checkoutUrl(),
                checkout == null ? null : checkout.pixQrCode(),
                checkout == null ? null : checkout.pixQrCodeBase64(),
                checkout == null ? null : checkout.pixTicketUrl()
        );
    }

    /** Chamado depois que o cliente paga (polling do site) ou pelo webhook do Mercado Pago. */
    @Transactional
    public void confirmarPagamento(Long vendaId, String paymentId) {
        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new NoSuchElementException("Venda não encontrada: " + vendaId));
        VendaPagamentoGateway gateway = gatewayRepository.findById(vendaId)
                .orElseThrow(() -> new IllegalStateException("Venda " + vendaId + " não tem pagamento online associado."));
        mercadoPagoCheckoutService.syncPayment(venda, gateway, paymentId);
    }

    /**
     * Confirma o recebimento de um pedido em DINHEIRO (staff bate na loja quando
     * o cliente paga na retirada/entrega) — sem isso, uma venda em DINHEIRO nunca
     * sai de RASCUNHO, porque só o Mercado Pago tem um evento automático que
     * dispara essa transição.
     */
    @Transactional
    public void confirmarRecebimentoDinheiro(Long vendaId) {
        if (gatewayRepository.existsById(vendaId)) {
            throw new IllegalStateException("Venda " + vendaId + " tem pagamento online associado — não é em dinheiro.");
        }
        vendaService.confirmarPagamento(vendaId);
    }

    @Transactional
    public void processarWebhook(String type, String topic, String paymentId) {
        mercadoPagoCheckoutService.handleWebhookNotification(type, topic, paymentId);
    }

    private Cliente resolverOuCriarCliente(CriarPedidoRequest request) {
        if (request.customerEmail() == null || request.customerEmail().isBlank()) {
            throw new IllegalArgumentException("E-mail do cliente é obrigatório.");
        }
        return clienteRepository.findAll().stream()
                .filter(c -> request.customerEmail().equalsIgnoreCase(c.getEmail()))
                .findFirst()
                .orElseGet(() -> {
                    Cliente novo = new Cliente(request.customerNome());
                    novo.setEmail(request.customerEmail());
                    novo.setTelefone(request.customerTelefone());
                    return clienteRepository.save(novo);
                });
    }

    private LocalEstoque resolverLocalEstoquePadrao() {
        return localEstoqueRepository.findAll().stream()
                .filter(LocalEstoque::isPrincipal)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum local de estoque principal cadastrado — configure um antes de vender online."));
    }

    private boolean isPagamentoOnline(NaturezaFormaPagamento tipoPagamento) {
        return tipoPagamento == NaturezaFormaPagamento.PIX
                || tipoPagamento == NaturezaFormaPagamento.CARTAO_CREDITO
                || tipoPagamento == NaturezaFormaPagamento.CARTAO_DEBITO
                || tipoPagamento == NaturezaFormaPagamento.BOLETO;
    }

    private TipoPagamentoOnline mapParaTipoOnline(NaturezaFormaPagamento tipoPagamento) {
        return switch (tipoPagamento) {
            case PIX -> TipoPagamentoOnline.PIX;
            case BOLETO -> TipoPagamentoOnline.BOLETO;
            case CARTAO_CREDITO -> TipoPagamentoOnline.CARTAO_CREDITO;
            case CARTAO_DEBITO -> TipoPagamentoOnline.CARTAO_DEBITO;
            default -> throw new IllegalStateException("Tipo de pagamento não é online: " + tipoPagamento);
        };
    }
}
