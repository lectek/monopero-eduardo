package br.com.lojagenerica.core.estoque;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code movimentacao_estoque} é append-only por trigger de banco (não só
 * por convenção da camada de serviço) — ver V005__estoque.sql. Este teste
 * usa JDBC puro com schema qualificado explicitamente: {@link JdbcTemplate}
 * não passa pelo {@code SchemaMultiTenantConnectionProvider} (esse só está
 * amarrado ao Hibernate), então não há search_path de tenant implícito aqui.
 */
@SpringBootTest
@Testcontainers
class LedgerImutabilidadeIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updateNoLedgerDeEstoqueLancaExcecao() {
        String schema = provisionarEObterSchema();
        Long movimentacaoId = inserirMovimentacaoDeTeste(schema);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update \"" + schema + "\".movimentacao_estoque set quantidade = 999 where id = ?", movimentacaoId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void deleteNoLedgerDeEstoqueLancaExcecao() {
        String schema = provisionarEObterSchema();
        Long movimentacaoId = inserirMovimentacaoDeTeste(schema);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from \"" + schema + "\".movimentacao_estoque where id = ?", movimentacaoId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private String provisionarEObterSchema() {
        String sufixo = "estoque-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono-" + sufixo + "@ex.example", "senhaForte123"));
        return empresa.getSchemaNome();
    }

    private Long inserirMovimentacaoDeTeste(String schema) {
        Long unidadeId = jdbcTemplate.queryForObject(
                "insert into \"" + schema + "\".unidade_medida (codigo, descricao) values ('UN-TESTE', 'Unidade de teste') returning id",
                Long.class);
        Long localId = jdbcTemplate.queryForObject(
                "insert into \"" + schema + "\".local_estoque (nome) values ('Depósito de teste') returning id",
                Long.class);
        Long tipoId = jdbcTemplate.queryForObject(
                "select id from \"" + schema + "\".tipo_movimentacao where codigo = 'VENDA'", Long.class);
        Long produtoId = jdbcTemplate.queryForObject(
                "insert into \"" + schema + "\".produto (nome, unidade_estoque_id) values ('Produto de teste', ?) returning id",
                Long.class, unidadeId);

        return jdbcTemplate.queryForObject(
                "insert into \"" + schema + "\".movimentacao_estoque "
                        + "(produto_id, local_estoque_id, tipo_movimentacao_id, sentido, quantidade, unidade_id, quantidade_base, origem_tipo) "
                        + "values (?, ?, ?, 'SAIDA', 1, ?, -1, 'VENDA') returning id",
                Long.class, produtoId, localId, tipoId, unidadeId);
    }
}
