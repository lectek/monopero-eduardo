package br.com.lojagenerica;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Critério de saída da Fase 0: a aplicação sobe contra um Postgres vazio
 * (sem nenhuma migration ainda — isso é Fase A). Prova que o pivot pra
 * Postgres + schema-por-tenant não quebrou o boot da aplicação.
 */
@SpringBootTest
@Testcontainers
class LojaGenericaApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsAgainstEmptyPostgres() {
        assertThat(context).isNotNull();
    }
}
