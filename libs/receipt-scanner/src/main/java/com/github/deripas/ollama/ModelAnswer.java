package com.github.deripas.ollama;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class the model answers with, see {@link OllamaClient#ask}: the JSON schema of the answer is generated
 * from the class by {@link JsonSchemas}, and the limit of the answer is set here.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelAnswer {

    /**
     * Limit of the answer in tokens ({@code num_predict}): a looping model is cut off at it, and the answer
     * then fails to parse.
     */
    int maxTokens();
}
