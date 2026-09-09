package br.com.lojagenerica;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Regras arquiteturais de longo prazo, verificadas a cada build. Esta é a
 * base do arcabouço de testes da Fase 0 — regras específicas por módulo
 * (ex.: "core não pode importar modules") entram junto com esses pacotes,
 * a partir da Fase A.
 */
class ArchitectureRulesTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.lojagenerica");

    @Test
    void sqliteMustNotBeUsedAnymore() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAPackage("org.sqlite..")
                .because("o pivot 'Loja Genérica' substitui o SQLite compartilhado com o IMS "
                        + "por Postgres com schema por tenant (ver plano de fases, Fase 0)");
        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void hibernateCommunityDialectsMustNotBeUsedAnymore() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAPackage("org.hibernate.community..")
                .because("dialeto SQLite comunitário não é mais necessário — Postgres usa o "
                        + "dialeto padrão do Hibernate");
        rule.check(PRODUCTION_CLASSES);
    }
}
