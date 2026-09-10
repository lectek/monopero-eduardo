package br.com.lojagenerica.core.compra.web;

import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.auditoria.AuditoriaContext;
import br.com.lojagenerica.core.compra.Compra;
import br.com.lojagenerica.core.compra.CompraRepository;
import br.com.lojagenerica.core.compra.CompraService;
import br.com.lojagenerica.core.compra.ItemCompra;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
@RequestMapping("/api/v1/compras")
public class CompraController {

    private final CompraService compraService;
    private final CompraRepository compraRepository;
    private final UsuarioRepository usuarioRepository;

    public CompraController(CompraService compraService, CompraRepository compraRepository,
                             UsuarioRepository usuarioRepository) {
        this.compraService = compraService;
        this.compraRepository = compraRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPRA_LER')")
    public CompraResponse buscar(@PathVariable Long id) {
        return compraRepository.findByIdComItens(id).map(CompraResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra não encontrada"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPRA_CRIAR')")
    public ResponseEntity<CompraResponse> criar(@RequestBody CriarCompraRequest request) {
        Long usuarioId = usuarioRepository.findByEmailIgnoreCase(AuditoriaContext.get().usuarioEmail())
                .map(u -> u.getId()).orElse(null);
        Compra compra = compraService.criar(request.fornecedorId(), request.localEstoqueId(), usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CompraResponse.from(compra));
    }

    @PostMapping("/{id}/itens")
    @PreAuthorize("hasAuthority('COMPRA_CRIAR')")
    public CompraResponse adicionarItem(@PathVariable Long id, @RequestBody AdicionarItemRequest request) {
        Compra compra = compraService.adicionarItem(id, request.produtoId(), request.quantidade(),
                request.unidadeId(), request.precoUnitario(), request.descontoValor());
        return CompraResponse.from(compra);
    }

    @PostMapping("/{id}/condicoes")
    @PreAuthorize("hasAuthority('COMPRA_CRIAR')")
    public CompraResponse definirCondicoes(@PathVariable Long id, @RequestBody DefinirCondicoesRequest request) {
        Compra compra = compraService.definirCondicoes(id, request.condicaoPagamentoId(), request.formaPagamentoId(),
                request.frete(), request.outrosCustos(), request.descontoValor());
        return CompraResponse.from(compra);
    }

    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAuthority('COMPRA_CONFIRMAR')")
    public CompraResponse confirmar(@PathVariable Long id) {
        return CompraResponse.from(compraService.confirmar(id));
    }

    public record CriarCompraRequest(@NotNull Long fornecedorId, @NotNull Long localEstoqueId) {
    }

    public record AdicionarItemRequest(@NotNull Long produtoId, @NotNull BigDecimal quantidade,
                                        @NotNull Long unidadeId, @NotNull BigDecimal precoUnitario,
                                        BigDecimal descontoValor) {
    }

    public record DefinirCondicoesRequest(Long condicaoPagamentoId, Long formaPagamentoId, BigDecimal frete,
                                           BigDecimal outrosCustos, BigDecimal descontoValor) {
    }

    public record ItemCompraResponse(Long id, Long produtoId, BigDecimal quantidade, BigDecimal precoUnitario,
                                      BigDecimal totalLinha) {
        static ItemCompraResponse from(ItemCompra item) {
            return new ItemCompraResponse(item.getId(), item.getProduto().getId(), item.getQuantidade(),
                    item.getPrecoUnitario(), item.getTotalLinha());
        }
    }

    public record CompraResponse(Long id, Long fornecedorId, String status, BigDecimal subtotal, BigDecimal frete,
                                  BigDecimal outrosCustos, BigDecimal total, List<ItemCompraResponse> itens,
                                  Instant confirmadaEm) {
        static CompraResponse from(Compra c) {
            return new CompraResponse(c.getId(), c.getFornecedor().getId(), c.getStatus().name(), c.getSubtotal(),
                    c.getFrete(), c.getOutrosCustos(), c.getTotal(),
                    c.getItens().stream().map(ItemCompraResponse::from).toList(), c.getConfirmadaEm());
        }
    }
}
