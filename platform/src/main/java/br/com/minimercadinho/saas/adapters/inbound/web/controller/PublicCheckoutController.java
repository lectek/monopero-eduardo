package br.com.minimercadinho.saas.adapters.inbound.web.controller;

import br.com.minimercadinho.saas.application.service.checkout.CheckoutService;
import br.com.minimercadinho.saas.domain.enums.ModoEntrega;
import br.com.minimercadinho.saas.domain.enums.TipoPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class PublicCheckoutController {

    private final CheckoutService checkoutService;

    public PublicCheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/api/public/pedidos")
    public ResponseEntity<CheckoutService.CheckoutResultado> criarPedido(@Valid @RequestBody CriarPedidoRequest body) {
        CheckoutService.CriarPedidoRequest request = new CheckoutService.CriarPedidoRequest(
                body.customerNome(),
                body.customerEmail(),
                body.customerTelefone(),
                body.itens().stream()
                        .map(i -> new CheckoutService.ItemCarrinho(i.nome(), i.cor(), i.peso(), i.quantidade()))
                        .toList(),
                body.modoEntrega(),
                body.enderecoEntrega(),
                body.tipoPagamento()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.criarPedido(request));
    }

    /** Polling do site depois de mostrar o QR code Pix — confirma se o pagamento já caiu. */
    @PostMapping("/api/public/pedidos/{pedidoId}/sincronizar-pagamento")
    public ResponseEntity<Void> sincronizarPagamento(
            @PathVariable Long pedidoId,
            @RequestBody SincronizarPagamentoRequest body
    ) {
        checkoutService.confirmarPagamento(pedidoId, body.paymentId());
        return ResponseEntity.noContent().build();
    }

    public record ItemCarrinhoRequest(
            @NotBlank String nome,
            String cor,
            String peso,
            int quantidade
    ) {
    }

    public record CriarPedidoRequest(
            @NotBlank String customerNome,
            @NotBlank String customerEmail,
            String customerTelefone,
            @NotEmpty List<@Valid ItemCarrinhoRequest> itens,
            @NotNull ModoEntrega modoEntrega,
            String enderecoEntrega,
            @NotNull TipoPagamento tipoPagamento
    ) {
    }

    public record SincronizarPagamentoRequest(@NotBlank String paymentId) {
    }
}
