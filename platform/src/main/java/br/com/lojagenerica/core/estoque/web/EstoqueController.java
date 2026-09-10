package br.com.lojagenerica.core.estoque.web;

import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.auditoria.AuditoriaContext;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoque;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueRepository;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.estoque.SaldoEstoque;
import br.com.lojagenerica.core.estoque.SaldoEstoqueRepository;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/estoque")
public class EstoqueController {

    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final UsuarioRepository usuarioRepository;

    public EstoqueController(MovimentacaoEstoqueService movimentacaoEstoqueService,
                              MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
                              SaldoEstoqueRepository saldoEstoqueRepository,
                              UsuarioRepository usuarioRepository) {
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
        this.saldoEstoqueRepository = saldoEstoqueRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/movimentacoes")
    @PreAuthorize("hasAuthority('ESTOQUE_AJUSTAR')")
    public ResponseEntity<MovimentacaoResponse> registrarAjusteManual(@RequestBody AjusteManualRequest request) {
        Long usuarioId = usuarioRepository.findByEmailIgnoreCase(emailAtual()).map(u -> u.getId()).orElse(null);

        MovimentacaoEstoque movimentacao = movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                request.produtoId(), request.localEstoqueId(), request.tipoMovimentacaoCodigo(), request.sentido(),
                request.quantidade(), request.unidadeId(), null, OrigemMovimentacao.AJUSTE_MANUAL,
                null, null, null, usuarioId, request.motivo()));

        return ResponseEntity.status(HttpStatus.CREATED).body(MovimentacaoResponse.from(movimentacao));
    }

    @GetMapping("/saldos")
    @PreAuthorize("hasAuthority('ESTOQUE_LER')")
    public List<SaldoResponse> saldos(@RequestParam Long produtoId) {
        return saldoEstoqueRepository.findByProdutoId(produtoId).stream().map(SaldoResponse::from).toList();
    }

    @GetMapping("/produtos/{produtoId}/movimentacoes")
    @PreAuthorize("hasAuthority('ESTOQUE_LER')")
    public List<MovimentacaoResponse> movimentacoesDoProduto(@PathVariable Long produtoId) {
        return movimentacaoEstoqueRepository.findByProdutoIdOrderByOcorridoEmDesc(produtoId).stream()
                .map(MovimentacaoResponse::from).toList();
    }

    private String emailAtual() {
        return AuditoriaContext.get().usuarioEmail();
    }

    public record AjusteManualRequest(@NotNull Long produtoId, @NotNull Long localEstoqueId,
                                       @NotBlank String tipoMovimentacaoCodigo, @NotNull SentidoMovimentacao sentido,
                                       @NotNull BigDecimal quantidade, @NotNull Long unidadeId,
                                       @NotBlank String motivo) {
    }

    public record MovimentacaoResponse(Long id, Long produtoId, String tipoMovimentacaoCodigo,
                                        SentidoMovimentacao sentido, BigDecimal quantidade,
                                        BigDecimal quantidadeBase, BigDecimal saldoApos,
                                        OrigemMovimentacao origemTipo, Instant ocorridoEm) {
        static MovimentacaoResponse from(MovimentacaoEstoque m) {
            return new MovimentacaoResponse(m.getId(), m.getProduto().getId(), m.getTipoMovimentacao().getCodigo(),
                    m.getSentido(), m.getQuantidade(), m.getQuantidadeBase(), m.getSaldoApos(),
                    m.getOrigemTipo(), m.getOcorridoEm());
        }
    }

    public record SaldoResponse(Long produtoId, Long localEstoqueId, BigDecimal quantidade, BigDecimal custoMedio) {
        static SaldoResponse from(SaldoEstoque s) {
            return new SaldoResponse(s.getProduto().getId(), s.getLocalEstoque().getId(), s.getQuantidade(), s.getCustoMedio());
        }
    }
}
