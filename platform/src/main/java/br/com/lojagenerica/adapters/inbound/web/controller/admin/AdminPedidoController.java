package br.com.lojagenerica.adapters.inbound.web.controller.admin;

import br.com.lojagenerica.adapters.outbound.persistence.entity.PedidoEntity;
import br.com.lojagenerica.adapters.outbound.persistence.repository.PedidoRepository;
import br.com.lojagenerica.application.service.checkout.CheckoutService;
import br.com.lojagenerica.domain.enums.ModoEntrega;
import br.com.lojagenerica.domain.enums.StatusPedido;
import br.com.lojagenerica.domain.enums.TipoPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pedidos")
public class AdminPedidoController {

    private final CheckoutService checkoutService;
    private final PedidoRepository pedidoRepository;

    public AdminPedidoController(CheckoutService checkoutService, PedidoRepository pedidoRepository) {
        this.checkoutService = checkoutService;
        this.pedidoRepository = pedidoRepository;
    }

    public record PedidoResumo(
            Long id,
            LocalDateTime data,
            String clienteNome,
            String clienteEmail,
            BigDecimal total,
            StatusPedido status,
            TipoPagamento tipoPagamento,
            ModoEntrega modoEntrega
    ) {
        static PedidoResumo de(PedidoEntity p) {
            return new PedidoResumo(
                    p.getId(), p.getData(),
                    p.getCliente().getNome(), p.getCliente().getEmail(),
                    p.getTotal(), p.getStatus(), p.getTipoPagamento(), p.getModoEntrega()
            );
        }
    }

    /** Painel admin: últimos 200 pedidos, mais recentes primeiro. */
    @GetMapping
    public List<PedidoResumo> listar() {
        return pedidoRepository.listarTodosComClienteOrderByDataDesc(PageRequest.of(0, 200))
                .stream().map(PedidoResumo::de).toList();
    }

    /** Staff confirma na loja que recebeu o dinheiro de um pedido em DINHEIRO — dá baixa no estoque. */
    @PostMapping("/{pedidoId}/confirmar-recebimento-dinheiro")
    public ResponseEntity<Void> confirmarRecebimentoDinheiro(@PathVariable Long pedidoId) {
        checkoutService.confirmarRecebimentoDinheiro(pedidoId);
        return ResponseEntity.noContent().build();
    }
}
