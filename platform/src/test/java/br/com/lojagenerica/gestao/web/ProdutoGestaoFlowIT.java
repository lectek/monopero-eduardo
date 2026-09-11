package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.Marca;
import br.com.lojagenerica.core.cadastro.MarcaRepository;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoPrecoHistoricoRepository;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prova o cadastro mais usado no dia a dia: criar produto mínimo (só
 * nome+unidade), criar com preço/custo iniciais, editar categoria/marca
 * sem tocar preço (não deve gerar histórico), e editar preço de verdade
 * (deve gerar exatamente uma linha em produto_preco_historico).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ProdutoGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private ProdutoPrecoHistoricoRepository precoHistoricoRepository;
    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired
    private MarcaRepository marcaRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void criarComPrecoEditarSemMudarPrecoDepoisComMudarPreco() throws Exception {
        String sufixo = "gestao-prod-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        Long unidadeId;
        Long marcaId;
        TenantContext.set(schema);
        try {
            unidadeId = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false)).getId();
            marcaId = marcaRepository.save(new Marca("Tramontina", "Tramontina S.A.")).getId();
        } finally {
            TenantContext.clear();
        }

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(post("/gestao/produtos").session(session).with(csrf())
                        .param("nome", "Parafuso 3/4")
                        .param("unidadeEstoqueId", String.valueOf(unidadeId))
                        .param("precoVenda", "10.00")
                        .param("custoAquisicao", "6.00"))
                .andExpect(redirectedUrl("/gestao/produtos"));

        mockMvc.perform(get("/gestao/produtos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Parafuso 3/4")));

        TenantContext.set(schema);
        Long produtoId;
        try {
            List<Produto> produtos = produtoRepository.findAll();
            assertThat(produtos).hasSize(1);
            Produto produto = produtos.get(0);
            assertThat(produto.getPrecoVenda()).isEqualByComparingTo("10.00");
            assertThat(produto.getCustoAquisicao()).isEqualByComparingTo("6.00");
            produtoId = produto.getId();
            assertThat(precoHistoricoRepository.findByProdutoIdOrderByVigenteDeDesc(produtoId)).hasSize(1);
        } finally {
            TenantContext.clear();
        }

        // Edita só a marca, mantendo o mesmo preço/custo — não deve criar histórico novo.
        mockMvc.perform(post("/gestao/produtos/" + produtoId).session(session).with(csrf())
                        .param("nome", "Parafuso 3/4")
                        .param("unidadeEstoqueId", String.valueOf(unidadeId))
                        .param("marcaId", String.valueOf(marcaId))
                        .param("precoVenda", "10.00")
                        .param("custoAquisicao", "6.00")
                        .param("status", "ATIVO"))
                .andExpect(redirectedUrl("/gestao/produtos"));

        TenantContext.set(schema);
        try {
            Produto produto = produtoRepository.findById(produtoId).orElseThrow();
            assertThat(produto.getMarca().getNome()).isEqualTo("Tramontina");
            assertThat(precoHistoricoRepository.findByProdutoIdOrderByVigenteDeDesc(produtoId)).hasSize(1);
        } finally {
            TenantContext.clear();
        }

        // Agora muda o preço de verdade — deve gerar uma segunda linha de histórico.
        mockMvc.perform(post("/gestao/produtos/" + produtoId).session(session).with(csrf())
                        .param("nome", "Parafuso 3/4")
                        .param("unidadeEstoqueId", String.valueOf(unidadeId))
                        .param("marcaId", String.valueOf(marcaId))
                        .param("precoVenda", "12.50")
                        .param("custoAquisicao", "6.00")
                        .param("motivoAlteracaoPreco", "reajuste de fornecedor")
                        .param("status", "ATIVO"))
                .andExpect(redirectedUrl("/gestao/produtos"));

        TenantContext.set(schema);
        try {
            Produto produto = produtoRepository.findById(produtoId).orElseThrow();
            assertThat(produto.getPrecoVenda()).isEqualByComparingTo("12.50");
            assertThat(precoHistoricoRepository.findByProdutoIdOrderByVigenteDeDesc(produtoId)).hasSize(2);
        } finally {
            TenantContext.clear();
        }
    }
}
