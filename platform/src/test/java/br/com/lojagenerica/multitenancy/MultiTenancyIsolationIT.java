package br.com.lojagenerica.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.cadastro.Categoria;
import br.com.lojagenerica.core.cadastro.CategoriaRepository;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O maior risco do projeto (ver docs/CONTEXTO.md): uma conexão devolvida ao
 * pool com o search_path de um tenant ainda setado respondendo
 * silenciosamente à próxima query de outro tenant. O loop de 50 iterações
 * força reciclagem de conexão o suficiente pra pegar esse vazamento se ele
 * existir — sem o loop, um teste de 1 tiro passaria mesmo com o bug, só por
 * sorte de pool sizing.
 */
@SpringBootTest
@Testcontainers
class MultiTenancyIsolationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private CategoriaRepository categoriaRepository;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void dadosDeUmaEmpresaJamaisSaoVisiveisPelaOutra() {
        Empresa empresaA = provisionar("empresa-a");
        Empresa empresaB = provisionar("empresa-b");

        criarCategoria(empresaA.getSchemaNome(), "Categoria-Exclusiva-A");
        criarCategoria(empresaB.getSchemaNome(), "Categoria-Exclusiva-B");

        for (int i = 0; i < 50; i++) {
            TenantContext.set(empresaA.getSchemaNome());
            try {
                assertThat(nomesDeCategorias()).containsExactly("Categoria-Exclusiva-A");
            } finally {
                TenantContext.clear();
            }

            TenantContext.set(empresaB.getSchemaNome());
            try {
                assertThat(nomesDeCategorias()).containsExactly("Categoria-Exclusiva-B");
            } finally {
                TenantContext.clear();
            }
        }
    }

    private Empresa provisionar(String prefixo) {
        String sufixo = prefixo + "-" + System.nanoTime();
        return provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono-" + sufixo + "@ex.example", "senhaForte123"));
    }

    void criarCategoria(String schema, String nome) {
        TenantContext.set(schema);
        try {
            categoriaRepository.save(new Categoria(nome, null));
        } finally {
            TenantContext.clear();
        }
    }

    private java.util.List<String> nomesDeCategorias() {
        return categoriaRepository.findAll().stream().map(Categoria::getNome).toList();
    }
}
