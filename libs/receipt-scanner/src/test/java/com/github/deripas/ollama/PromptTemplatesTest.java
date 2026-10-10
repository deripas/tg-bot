package com.github.deripas.ollama;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplatesTest {

    private static final String TEMPLATE = """
            Header
            <if(!first)>
            Top is cut.
            <endif>
            <if(!last)>
            Bottom is cut.
            <endif>
            Request""";

    @Test
    void testConditionalLinesLeaveNoEmptyLines() {
        assertThat(PromptTemplates.render(TEMPLATE, Map.of("first", true, "last", true)))
                .isEqualTo("Header\nRequest");
        assertThat(PromptTemplates.render(TEMPLATE, Map.of("first", false, "last", true)))
                .isEqualTo("Header\nTop is cut.\nRequest");
        assertThat(PromptTemplates.render(TEMPLATE, Map.of("first", false, "last", false)))
                .isEqualTo("Header\nTop is cut.\nBottom is cut.\nRequest");
    }

    @Test
    void testVariableIsInserted() {
        assertThat(PromptTemplates.render("Country: <country>.", Map.of("country", "Serbia")))
                .isEqualTo("Country: Serbia.");
    }

    @Test
    void testMalformedTemplateFails() {
        assertThatThrownBy(() -> PromptTemplates.render("<if(first)>no end", Map.of("first", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
