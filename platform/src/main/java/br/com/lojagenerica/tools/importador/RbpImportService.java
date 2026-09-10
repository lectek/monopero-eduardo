package br.com.lojagenerica.tools.importador;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.produto.ProdutoService;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.domain.IdentidadeUsuario;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Migra o essencial de um {@code rbp.db} real (produtos + estoque inicial +
 * contas admin) pra um tenant Postgres já provisionado. NÃO migra histórico
 * de vendas ({@code sold_records}) — o agregado Venda só existe a partir da
 * Fase C; rodar de novo neste tenant depois que Vendas existir é a forma
 * planejada de completar a migração (ver docs/ROADMAP.md).
 *
 * <p>{@link #analisar} nunca escreve nada — é o dry-run que tem que rodar
 * antes de qualquer {@link #importar} real, e o resultado das duas chamadas
 * deve reconciliar (mesmas contagens).
 */
@Service
public class RbpImportService {

    private static final Logger log = LoggerFactory.getLogger(RbpImportService.class);
    private static final String UNIDADE_PADRAO = "UN";
    private static final String LOCAL_PADRAO = "Depósito Principal (importado)";

    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final UsuarioRepository usuarioRepository;
    private final PapelRepository papelRepository;
    private final IdentidadeUsuarioRepository identidadeUsuarioRepository;

    public RbpImportService(
            UnidadeMedidaRepository unidadeMedidaRepository, LocalEstoqueRepository localEstoqueRepository,
            ProdutoRepository produtoRepository, ProdutoService produtoService,
            MovimentacaoEstoqueService movimentacaoEstoqueService,
            UsuarioRepository usuarioRepository, PapelRepository papelRepository,
            IdentidadeUsuarioRepository identidadeUsuarioRepository) {
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.produtoService = produtoService;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.usuarioRepository = usuarioRepository;
        this.papelRepository = papelRepository;
        this.identidadeUsuarioRepository = identidadeUsuarioRepository;
    }

    /** Só leitura do rbp.db — nenhuma escrita no Postgres. Sempre rodar antes de {@link #importar}. */
    public RelatorioImportacao analisar(Path arquivoRbp) {
        try (Connection conn = LegacyRbpReader.abrir(arquivoRbp)) {
            List<ProdutoLegado> produtos = LegacyRbpReader.lerProdutos(conn);
            List<AdminUserLegado> admins = LegacyRbpReader.lerAdminUsers(conn);
            return construirRelatorio(produtos, admins);
        } catch (SQLException e) {
            throw new ImportacaoException("Falha lendo " + arquivoRbp + ": " + e.getMessage(), e);
        }
    }

    /**
     * Escreve no schema do tenant já resolvido em {@link TenantContext}
     * (o chamador decide qual empresa — normalmente logo após provisionar
     * uma nova, ver ProvisionamentoTenantService). Idempotente por
     * {@code codigoInterno}/e-mail quando eles existem; produtos sem
     * código podem duplicar numa segunda execução (limitação conhecida,
     * ver docs/ROADMAP.md).
     *
     * <p>Deliberadamente NÃO é {@code @Transactional}: escreve tanto no
     * schema do tenant (produto/estoque/usuario, TenantContext já setado
     * pelo chamador) quanto no schema "plataforma" (identidade_usuario, ver
     * {@link #importarAdminUser}) — uma única transação ambiente
     * reaproveitaria a mesma sessão Hibernate (e o schema resolvido no
     * início) pros dois, que é exatamente o bug que
     * TenantBootstrapService/ProvisionamentoTenantService já evitam do
     * mesmo jeito (ver docs/CONTEXTO.md). Cada chamada de repositório aqui
     * já é transacional por si (Spring Data).
     */
    public RelatorioImportacao importar(Path arquivoRbp, Long empresaId) {
        TenantContext.require(); // precisa já estar setado pelo chamador
        List<ProdutoLegado> produtosLegado;
        List<AdminUserLegado> adminsLegado;
        try (Connection conn = LegacyRbpReader.abrir(arquivoRbp)) {
            produtosLegado = LegacyRbpReader.lerProdutos(conn);
            adminsLegado = LegacyRbpReader.lerAdminUsers(conn);
        } catch (SQLException e) {
            throw new ImportacaoException("Falha lendo " + arquivoRbp + ": " + e.getMessage(), e);
        }

        List<String> avisos = new ArrayList<>();
        UnidadeMedida unidade = unidadeMedidaRepository.findByCodigo(UNIDADE_PADRAO)
                .orElseGet(() -> unidadeMedidaRepository.save(new UnidadeMedida(UNIDADE_PADRAO, "Unidade", (short) 0, false)));
        LocalEstoque local = localEstoqueRepository.findAll().stream().filter(LocalEstoque::isPrincipal).findFirst()
                .orElseGet(() -> localEstoqueRepository.save(new LocalEstoque(LOCAL_PADRAO, "DEPOSITO", true)));

        for (ProdutoLegado legado : produtosLegado) {
            importarProduto(legado, unidade, local, avisos);
        }
        for (AdminUserLegado legado : adminsLegado) {
            importarAdminUser(legado, empresaId, avisos);
        }

        RelatorioImportacao relatorio = construirRelatorio(produtosLegado, adminsLegado);
        log.info("Importação de {} concluída: {} produtos, {} contas admin, {} avisos.",
                arquivoRbp, relatorio.totalProdutosLegado(), relatorio.totalContasAdmin(), avisos.size());
        return new RelatorioImportacao(relatorio.totalProdutosLegado(), relatorio.totalUnidadesEmEstoque(),
                relatorio.produtosSemPreco(), relatorio.produtosComCorOuPeso(), relatorio.totalContasAdmin(), avisos);
    }

    private void importarProduto(ProdutoLegado legado, UnidadeMedida unidade, LocalEstoque local, List<String> avisos) {
        String codigoInterno = valorOuNulo(legado.codigo());
        if (codigoInterno != null && produtoRepository.findAll().stream()
                .anyMatch(p -> codigoInterno.equals(p.getCodigoInterno()))) {
            avisos.add("Produto com código '" + codigoInterno + "' já existe — ignorado (re-execução).");
            return;
        }

        BigDecimal preco = (legado.preco() != null && legado.preco() > 0)
                ? BigDecimal.valueOf(legado.preco()) : null;
        if (preco == null) {
            avisos.add("Produto '" + legado.nome() + "' sem preço cadastrado no legado.");
        }
        Produto produto = produtoService.criarComDadosIniciais(
                legado.nome(), unidade.getId(), codigoInterno, descricaoComAtributosLegados(legado), preco);

        int quantidade = legado.quantidadeOuZero();
        if (quantidade > 0) {
            movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                    produto.getId(), local.getId(), "INVENTARIO", SentidoMovimentacao.ENTRADA,
                    BigDecimal.valueOf(quantidade), unidade.getId(), null,
                    OrigemMovimentacao.IMPORTACAO, produto.getId(), null, null, null,
                    "Estoque inicial importado do rbp.db"));
        }
    }

    private void importarAdminUser(AdminUserLegado legado, Long empresaId, List<String> avisos) {
        if (legado.email() == null || legado.email().isBlank()) {
            avisos.add("Conta admin sem e-mail no legado — ignorada.");
            return;
        }
        if (usuarioRepository.findByEmailIgnoreCase(legado.email()).isPresent()) {
            avisos.add("Usuário '" + legado.email() + "' já existe neste tenant — ignorado (re-execução).");
            return;
        }

        String nomePapel = "ADMIN".equalsIgnoreCase(legado.role()) ? "ADMINISTRADOR" : "MOTOBOY";
        Papel papel = papelRepository.findByNome(nomePapel)
                .orElseGet(() -> papelRepository.save(new Papel(nomePapel, "Importado do legado", false)));

        Usuario usuario = new Usuario(legado.nome() != null ? legado.nome() : legado.email(), legado.email());
        usuario.setAtivo(legado.ativo());
        usuario.getPapeis().add(papel);
        usuario = usuarioRepository.save(usuario);

        // identidade_usuario vive no schema "plataforma", não no do tenant — sai
        // do TenantContext do tenant só pra esta chamada e volta em seguida,
        // senão a sessão Hibernate já aberta pro schema do tenant reaproveitaria
        // o schema errado (ver docs/CONTEXTO.md e a nota em importar()).
        String schemaTenant = TenantContext.get();
        TenantContext.clear();
        try {
            if (identidadeUsuarioRepository.findByEmailIgnoreCaseAndAtivoTrue(legado.email()).isEmpty()
                    && legado.senhaHashBcrypt() != null) {
                identidadeUsuarioRepository.save(new IdentidadeUsuario(
                        legado.email(), empresaId, usuario.getId(), legado.senhaHashBcrypt()));
            }
        } finally {
            TenantContext.set(schemaTenant);
        }
    }

    private RelatorioImportacao construirRelatorio(List<ProdutoLegado> produtos, List<AdminUserLegado> admins) {
        long totalEstoque = produtos.stream().mapToLong(ProdutoLegado::quantidadeOuZero).sum();
        int semPreco = (int) produtos.stream().filter(p -> p.preco() == null || p.preco() <= 0).count();
        int comCorOuPeso = (int) produtos.stream()
                .filter(p -> valorOuNulo(p.cor()) != null || valorOuNulo(p.peso()) != null).count();
        return new RelatorioImportacao(produtos.size(), totalEstoque, semPreco, comCorOuPeso, admins.size(), List.of());
    }

    private String descricaoComAtributosLegados(ProdutoLegado legado) {
        StringBuilder sb = new StringBuilder();
        if (legado.descricao() != null && !legado.descricao().isBlank()) {
            sb.append(legado.descricao());
        }
        // TODO(Fase A, continuação): virar produto_atributo estruturado
        // (Cor/Peso) em vez de texto solto, quando o CRUD de atributos existir.
        if (valorOuNulo(legado.cor()) != null) {
            sb.append(sb.isEmpty() ? "" : " — ").append("Cor: ").append(legado.cor());
        }
        if (valorOuNulo(legado.peso()) != null) {
            sb.append(sb.isEmpty() ? "" : " — ").append("Peso: ").append(legado.peso());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String valorOuNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }
}
