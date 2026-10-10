package com.github.deripas.tgbot.receipt;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The subpackages of the skill are Spring Modulith modules: a module uses only the classes of the base package
 * of another, there are no cycles.
 */
class ModulesTest {

    private final ApplicationModules modules = ApplicationModules.of(ReceiptAutoConfiguration.class.getPackageName());

    @Test
    void modulesAreKnown() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("store", "bot", "web");
    }

    @Test
    void dependenciesAreValid() {
        modules.verify();
    }
}
