package br.com.lojagenerica.core.produto;

import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.auditoria.AuditoriaContext;
import br.com.lojagenerica.core.auditoria.AuditoriaService;
import br.com.lojagenerica.core.auditoria.EventoAuditoria;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoPrecoHistoricoRepository precoHistoricoRepository;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository, ProdutoPrecoHistoricoRepository precoHistoricoRepository,
                           UnidadeMedidaRepository unidadeMedidaRepository, UsuarioRepository usuarioRepository,
                           AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.precoHistoricoRepository = precoHistoricoRepository;
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * O JWT hoje só carrega o e-mail do usuário (ver AuthController/
     * AuthTokenFacade — não há claim de id numérico). Resolve o id real
     * pelo e-mail em vez de deixar {@code usuario_id} nulo — "quem alterou
     * o preço" é exigido pelo spec, não um extra.
     */
    private Long usuarioIdAtual() {
        String email = AuditoriaContext.get().usuarioEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        return usuarioRepository.findByEmailIgnoreCase(email).map(u -> u.getId()).orElse(null);
    }

    @Transactional
    public Produto criar(String nome, Long unidadeEstoqueId) {
        var unidade = unidadeMedidaRepository.findById(unidadeEstoqueId)
                .orElseThrow(() -> new NoSuchElementException("Unidade de medida " + unidadeEstoqueId + " não encontrada"));
        return produtoRepository.save(new Produto(nome, unidade));
    }

    /**
     * Usado só por criação com dado já conhecido de fonte externa (ex.:
     * {@code tools.importador}) — define preço/custo iniciais sem passar
     * pelo fluxo de auditoria de {@link #alterarPreco}, porque não existe
     * "preço anterior" pra comparar num produto que acabou de nascer.
     */
    @Transactional
    public Produto criarComDadosIniciais(String nome, Long unidadeEstoqueId, String codigoInterno,
                                          String descricao, BigDecimal precoVenda) {
        var unidade = unidadeMedidaRepository.findById(unidadeEstoqueId)
                .orElseThrow(() -> new NoSuchElementException("Unidade de medida " + unidadeEstoqueId + " não encontrada"));
        Produto produto = new Produto(nome, unidade);
        produto.setCodigoInterno(codigoInterno);
        produto.setDescricao(descricao);
        if (precoVenda != null) {
            produto.definirPrecoECusto(precoVenda, null);
        }
        return produtoRepository.save(produto);
    }

    /**
     * Único caminho pra mudar preço/custo — grava histórico e publica
     * evento de auditoria (item #1 da lista obrigatória do spec). Escrever
     * direto em {@code produto.precoVenda} fora daqui é o que o
     * {@code PreUpdateEventListener} de rede de segurança (Fase G) existe
     * pra pegar.
     */
    @Transactional
    public Produto alterarPreco(Long produtoId, BigDecimal novoPreco, BigDecimal novoCusto, String motivo) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new NoSuchElementException("Produto " + produtoId + " não encontrado"));

        BigDecimal precoAnterior = produto.getPrecoVenda();
        BigDecimal custoAnterior = produto.getCustoAquisicao();

        produto.definirPrecoECusto(novoPreco, novoCusto);
        produtoRepository.save(produto);

        precoHistoricoRepository.save(new ProdutoPrecoHistorico(
                produto, precoAnterior, novoPreco, custoAnterior, novoCusto,
                usuarioIdAtual(), motivo));

        Map<String, Object> antes = new HashMap<>();
        antes.put("precoVenda", precoAnterior);
        antes.put("custoAquisicao", custoAnterior);
        Map<String, Object> depois = new HashMap<>();
        depois.put("precoVenda", novoPreco);
        depois.put("custoAquisicao", novoCusto);
        auditoriaService.registrar(EventoAuditoria.de(
                "PRODUTO_PRECO_ALTERADO", "produto", produtoId, antes, depois, motivo));

        return produto;
    }
}
