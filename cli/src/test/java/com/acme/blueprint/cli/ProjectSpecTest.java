package com.acme.blueprint.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectSpecTest {

    @Test
    void derivesEachRoleFromTheName() {
        ProjectSpec spec = ProjectSpec.defaultsFor(ProjectName.fromUserInput("my-shop"));

        assertThat(spec.artifact()).isEqualTo("my-shop");
        assertThat(spec.basePackage()).isEqualTo("com.myshop");
        assertThat(spec.packagePath()).isEqualTo("com/myshop");
        assertThat(spec.classPrefix()).isEqualTo("MyShop");
        assertThat(spec.shortName()).isEqualTo("myshop");
    }

    @Test
    void aNameWithoutHyphensIsOneWord() {
        ProjectSpec spec = ProjectSpec.defaultsFor(ProjectName.fromUserInput("shop"));

        assertThat(spec.basePackage()).isEqualTo("com.shop");
        assertThat(spec.classPrefix()).isEqualTo("Shop");
    }

    @Test
    void capitalisesTheFirstCharacterOfEachWordOnly() {
        assertThat(ProjectSpec.defaultsFor(ProjectName.fromUserInput("shop-2go")).classPrefix()).isEqualTo("Shop2go");
    }

    @Test
    void theBasePackageIsAnAnswerOfItsOwn() {
        ProjectSpec spec = new ProjectSpec(ProjectName.fromUserInput("my-shop"), "org.example.shop");

        assertThat(spec.packagePath()).isEqualTo("org/example/shop");
        assertThat(spec.shortName()).isEqualTo("myshop");
    }
}
