package br.com.lojagenerica.core.venda.web;

import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.auditoria.AuditoriaContext;
import br.com.lojagenerica.core.venda.CanalVenda;
import br.com.lojagenerica.core.venda.RegistrarVendaCommand;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaRepository;
import br.com.lojagenerica.core.venda.VendaService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/vendas")
public class VendaController {

    private final VendaService vendaService;
    private final VendaRepository vendaRepository;
    private final UsuarioRepository usuarioRepository;

    public VendaController(VendaService vendaService, VendaRepository vendaRepository,
                            UsuarioRepository usuarioRepository) {
        this.vendaService = vendaService;
        this.vendaRepository = vendaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VENDA_LER')")
    public VendaResponse buscar(@PathVariable Long id) {
        return vendaRepository.findByIdComItensEPagamentos(id).map(VendaResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda não encontrada"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VENDA_CRIAR')")
    public ResponseEntity<VendaResponse> registrar(@RequestBody RegistrarVendaRequest request) {
        String email = AuditoriaContext.get().usuarioEmail();
        Long usuarioId = usuarioRepository.findByEmailIgnoreCase(email).map(u -> u.getId()).orElse(null);

        Venda venda = vendaService.registrar(new RegistrarVendaCommand(
                request.uuid() != null ? request.uuid() : UUID.randomUUID(),
                request.canal(), request.localEstoqueId(), request.clienteId(), request.terminalId(),
                usuarioId, email, request.descontoValor(), null,
                request.itens().stream().map(i -> new RegistrarVendaCommand.ItemVendaCommand(
                        i.produtoId(), i.quantidade(), i.unidadeId(), i.precoUnitario(), i.descontoValor())).toList(),
                request.pagamentos() == null ? List.of() : request.pagamentos().stream()
                        .map(p -> new RegistrarVendaCommand.PagamentoVendaCommand(
                                p.formaPagamentoId(), p.valor(), p.valorRecebido(), p.troco())).toList()));

        return ResponseEntity.status(HttpStatus.CREATED).body(VendaResponse.from(venda));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('VENDA_CANCELAR')")
    public VendaResponse cancelar(@PathVariable Long id, @RequestBody CancelarVendaRequest request) {
        Long usuarioId = usuarioRepository.findByEmailIgnoreCase(AuditoriaContext.get().usuarioEmail())
                .map(u -> u.getId()).orElse(null);
        return VendaResponse.from(vendaService.cancelar(id, request.motivo(), usuarioId));
    }

    public record ItemVendaRequest(@NotNull Long produtoId, @NotNull BigDecimal quantidade, @NotNull Long unidadeId,
                                    @NotNull BigDecimal precoUnitario, BigDecimal descontoValor) {
    }

    public record PagamentoVendaRequest(@NotNull Long formaPagamentoId, @NotNull BigDecimal valor,
                                         BigDecimal valorRecebido, BigDecimal troco) {
    }

    public record RegistrarVendaRequest(UUID uuid, @NotNull CanalVenda canal, @NotNull Long localEstoqueId,
                                         Long clienteId, Long terminalId, BigDecimal descontoValor,
                                         @NotEmpty List<ItemVendaRequest> itens,
                                         List<PagamentoVendaRequest> pagamentos) {
    }

    public record CancelarVendaRequest(@jakarta.validation.constraints.NotBlank String motivo) {
    }

    public record ItemVendaResponse(Long id, Long produtoId, String descricaoSnapshot, BigDecimal quantidade,
                                     BigDecimal precoUnitario, BigDecimal totalLinha) {
    }

    public record VendaResponse(Long id, UUID uuid, String canal, String status, BigDecimal subtotal,
                                 BigDecimal descontoValor, BigDecimal total, List<ItemVendaResponse> itens,
                                 Instant canceladaEm) {
        static VendaResponse from(Venda v) {
            return new VendaResponse(v.getId(), v.getUuid(), v.getCanal().name(), v.getStatus().name(),
                    v.getSubtotal(), v.getDescontoValor(), v.getTotal(),
                    v.getItens().stream().map(i -> new ItemVendaResponse(i.getId(), i.getProduto().getId(),
                            i.getDescricaoSnapshot(), i.getQuantidade(), i.getPrecoUnitario(), i.getTotalLinha())).toList(),
                    v.getCanceladaEm());
        }
    }
}
