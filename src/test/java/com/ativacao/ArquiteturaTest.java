package com.ativacao;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArquiteturaTest {

    private static com.tngtech.archunit.core.domain.JavaClasses classes;

    @BeforeAll
    static void importarClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ativacao");
    }

    @Test
    void dominioNaoDeveDependerDeAplicacaoOuApi() {
        ArchRule regra = noClasses()
                .that().resideInAPackage("com.ativacao.dominio..")
                .should().dependOnClassesThat(resideInAPackage("com.ativacao.aplicacao.."))
                .orShould().dependOnClassesThat(resideInAPackage("com.ativacao.api.."));

        regra.check(classes);
    }

    @Test
    void aplicacaoNaoDeveDependerDeApi() {
        ArchRule regra = noClasses()
                .that().resideInAPackage("com.ativacao.aplicacao..")
                .should().dependOnClassesThat(resideInAPackage("com.ativacao.api.."));

        regra.check(classes);
    }

    @Test
    void controllersNaoDevemAcessarRepositorioDiretamente() {
        ArchRule regra = noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAssignableTo(com.ativacao.dominio.SolicitacaoRepository.class);

        regra.check(classes);
    }

    @Test
    void dominioNaoDeveDependerDeFrameworksWeb() {
        ArchRule regra = noClasses()
                .that().resideInAPackage("com.ativacao.dominio..")
                .should().dependOnClassesThat(resideInAPackage("org.springframework.web.."))
                .orShould().dependOnClassesThat(resideInAPackage("org.springframework.http.."));

        regra.check(classes);
    }

    @Test
    void excecaoNaoDeveDependerDeDominioAplicacaoOuApi() {
        ArchRule regra = noClasses()
                .that().resideInAPackage("com.ativacao.excecao..")
                .should().dependOnClassesThat(resideInAPackage("com.ativacao.aplicacao.."))
                .orShould().dependOnClassesThat(resideInAPackage("com.ativacao.api.."))
                .orShould().dependOnClassesThat(resideInAPackage("com.ativacao.integracao.."));

        regra.check(classes);
    }
}
