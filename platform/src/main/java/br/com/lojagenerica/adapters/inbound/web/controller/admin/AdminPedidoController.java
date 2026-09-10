package br.com.lojagenerica.adapters.inbound.web.controller.admin;

import br.com.lojagenerica.application.service.checkout.CheckoutService;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painel admin do canal ONLINE — reescrito na Fase C sobre {@link Venda}
 * (era {@code PedidoEntity}). Vendas de PDV/balcão já aparecem no
 * {@code VendaController} genérico; este endpoint é específico do fluxo
 * de checkout do site (confirmar dinheiro recebido).
 */
@RestController
@RequestMapping("/api/admin/pedidos")
public class AdminPedidoController {

    private final CheckoutService checkoutService;
    private final VendaRepository vendaRepository;

    public AdminPedidoController(CheckoutService checkoutService, VendaRepository vendaRepository) {
        this.checkoutService = checkoutService;
        this.vendaRepository = vendaRepository;
    }

    public record PedidoResumo(Long id, Instant data, String clienteNome, String clienteEmail,
                                BigDecimal total, String status, String canal) {
        static PedidoResumo de(Venda v) {
            return new PedidoResumo(
                    v.getId(), v.getData(),
                    v.getCliente() != null ? v.getCliente().getNome() : null,
                    v.getCliente() != null ? v.getCliente().getEmail() : null,
                    v.getTotal(), v.getStatus().name(), v.getCanal().name()
            );
        }
    }

    /** Painel admin: últimos 200 pedidos online, mais recentes primeiro. */
    @GetMapping
    public List<PedidoResumo> listar() {
        return vendaRepository.findAll(PageRequest.of(0, 200))
                .stream().map(PedidoResumo::de).toList();
    }

    /** Staff confirma na loja que recebeu o dinheiro de um pedido em DINHEIRO — dá baixa no estoque. */
    @PostMapping("/{pedidoId}/confirmar-recebimento-dinheiro")
    public ResponseEntity<Void> confirmarRecebimentoDinheiro(@PathVariable Long pedidoId) {
        checkoutService.confirmarRecebimentoDinheiro(pedidoId);
        return ResponseEntity.noContent().build();
    }
}
