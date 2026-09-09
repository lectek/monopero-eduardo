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
 * Critério de saída da Fase 0: a aplicação sobe contra um Postgres vazio.
 * Critério de saída da Fase A: sobe também com os runners de migration
 * (control plane "plataforma") já registrados, contra 0 empresas.
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
