package com.github.deripas.ollama;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import lombok.experimental.UtilityClass;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON schema of a model answer generated from its class, passed as Ollama {@code format}: Ollama turns it into
 * a grammar that constrains the answer.
 * <ul>
 *     <li>every property is required: the model fills the properties one by one, and may stop at an optional one;
 *     a value that is not in the image is asked for in the description;</li>
 *     <li>properties keep the order of declaration: the model fills them in this order;</li>
 *     <li>{@code @JsonPropertyDescription} gives the description of a property;</li>
 *     <li>{@code @Size} gives the bounds of an array or a string.</li>
 * </ul>
 */
@UtilityClass
public class JsonSchemas {

    private static final SchemaGenerator GENERATOR = new SchemaGenerator(generatorConfig());

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Map<Class<?>, Map<String, Object>> CACHE = new ConcurrentHashMap<>();

    /**
     * @return the schema as a map, to be sent within a JSON request
     */
    public static Map<String, Object> of(Class<?> type) {
        return CACHE.computeIfAbsent(type, JsonSchemas::generate);
    }

    private static SchemaGeneratorConfig generatorConfig() {
        final SchemaGeneratorConfigBuilder builder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .with(new JacksonSchemaModule())
                        .with(new JakartaValidationModule())
                        .without(Option.SCHEMA_VERSION_INDICATOR);
        // The sort is stable, so the properties keep the order of declaration instead of the alphabetical one
        builder.forTypesInGeneral().withPropertySorter((a, b) -> 0);
        builder.forFields().withRequiredCheck(field -> true);
        return builder.build();
    }

    private static Map<String, Object> generate(Class<?> type) {
        return MAPPER.convertValue(GENERATOR.generateSchema(type), new TypeReference<>() {
        });
    }
}
