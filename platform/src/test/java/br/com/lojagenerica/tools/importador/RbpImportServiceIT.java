package br.com.lojagenerica.tools.importador;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.estoque.SaldoEstoqueRepository;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Usa um rbp.db sintético (criado aqui via sqlite-jdbc, mesma dependência
 * que o importador usa pra ler o real) em vez do sample-data do repo
 * (schema-only, zero linhas — não prova mapeamento de dado real).
 */
@SpringBootTest
@Testcontainers
class RbpImportServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RbpImportService rbpImportService;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private SaldoEstoqueRepository saldoEstoqueRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private IdentidadeUsuarioRepository identidadeUsuarioRepository;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void dryRunNaoEscreveNadaEImportacaoRealCriaProdutosEstoqueEAdmin(@org.junit.jupiter.api.io.TempDir Path tempDir)
            throws Exception {
        Path arquivoRbp = tempDir.resolve("rbp-teste.db");
        criarRbpSintetico(arquivoRbp);

        RelatorioImportacao dryRun = rbpImportService.analisar(arquivoRbp);
        assertThat(dryRun.totalProdutosLegado()).isEqualTo(2);
        assertThat(dryRun.totalUnidadesEmEstoque()).isEqualTo(15);
        assertThat(dryRun.produtosSemPreco()).isEqualTo(1);
        assertThat(dryRun.totalContasAdmin()).isEqualTo(1);

        String sufixo = "importa-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));

        TenantContext.set(empresa.getSchemaNome());
        try {
            RelatorioImportacao real = rbpImportService.importar(arquivoRbp, empresa.getId());
            assertThat(real.totalProdutosLegado()).isEqualTo(2);

            assertThat(produtoRepository.findAll()).hasSize(2);
            assertThat(produtoRepository.findAll().stream().map(p -> p.getNome()))
                    .contains("Cimento CP2 50kg", "Areia Fina");

            long totalEmEstoque = produtoRepository.findAll().stream()
                    .flatMap(p -> saldoEstoqueRepository.findByProdutoId(p.getId()).stream())
                    .map(s -> s.getQuantidade().longValueExact())
                    .mapToLong(Long::longValue).sum();
            assertThat(totalEmEstoque).isEqualTo(15);

            assertThat(usuarioRepository.findByEmailIgnoreCase("dono-legado@loja.example")).isPresent();
        } finally {
            TenantContext.clear();
        }

        assertThat(identidadeUsuarioRepository.findByEmailIgnoreCaseAndAtivoTrue("dono-legado@loja.example"))
                .hasSize(1);

        // Rodar de novo: produto COM código não duplica (idempotente por codigoInterno);
        // usuário não duplica (idempotente por e-mail); produto SEM código no legado
        // duplica — limitação conhecida e documentada em RbpImportService, verificada
        // aqui pra não virar surpresa silenciosa se o comportamento mudar sem querer.
        TenantContext.set(empresa.getSchemaNome());
        try {
            rbpImportService.importar(arquivoRbp, empresa.getId());
            assertThat(produtoRepository.findAll()).hasSize(3);
            assertThat(produtoRepository.findAll().stream().filter(p -> "CIM50".equals(p.getCodigoInterno())).count())
                    .isEqualTo(1);
            assertThat(usuarioRepository.findAll()).hasSize(2); // dono provisionado + dono-legado, sem duplicar
        } finally {
            TenantContext.clear();
        }
    }

    private void criarRbpSintetico(Path arquivo) throws IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + arquivo.toAbsolutePath());
             Statement st = conn.createStatement()) {
            st.execute("create table products (pname text, pclr text, pwt text, pqt text, pcode text, pdesc text, pprice real, primary key(pname, pclr, pwt))");
            st.execute("insert into products values ('Cimento CP2 50kg', '', '', '10', 'CIM50', 'Saco 50kg', 32.90)");
            st.execute("insert into products values ('Areia Fina', 'Amarela', '', '5', '', '', null)");

            st.execute("create table admin_users (id integer primary key, nome text, email text, senha_hash text, role text, ativo integer, percentual_comissao real)");
            // hash bcrypt válido de exemplo (não é senha real de produção nenhuma)
            st.execute("insert into admin_users (nome, email, senha_hash, role, ativo) values "
                    + "('Dono Legado', 'dono-legado@loja.example', "
                    + "'$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5b0i.MFqRZgFT/j.6TQ.i5t.C.0Nu', 'ADMIN', 1)");
        } catch (java.sql.SQLException e) {
            throw new IOException(e);
        }
    }
}
