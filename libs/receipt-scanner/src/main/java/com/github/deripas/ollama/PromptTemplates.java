package com.github.deripas.ollama;

import lombok.experimental.UtilityClass;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STErrorListener;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.misc.STMessage;

import java.util.Map;

/**
 * Renders a prompt written as a StringTemplate 4 template: expressions in angle brackets, e.g.
 * {@code <if(!first)>...<endif>}. A line holding only {@code <if(...)>}, {@code <else>} or {@code <endif>}
 * leaves no empty line, so conditional lines can be written one per line.
 */
@UtilityClass
public class PromptTemplates {

    private static final STGroup GROUP = new STGroup('<', '>');

    static {
        GROUP.setListener(new FailingListener());
    }

    /**
     * @throws IllegalArgumentException if the template is malformed
     */
    public static String render(String template, Map<String, ?> variables) {
        final ST st = new ST(GROUP, template);
        variables.forEach(st::add);
        return st.render();
    }

    /**
     * ST reports errors to the console by default and renders what it can.
     */
    private static final class FailingListener implements STErrorListener {

        @Override
        public void compileTimeError(STMessage msg) {
            throw new IllegalArgumentException("Malformed prompt template: " + msg);
        }

        @Override
        public void runTimeError(STMessage msg) {
            throw new IllegalArgumentException("Failed to render prompt template: " + msg);
        }

        @Override
        public void IOError(STMessage msg) {
            throw new IllegalStateException(msg.toString());
        }

        @Override
        public void internalError(STMessage msg) {
            throw new IllegalStateException(msg.toString());
        }
    }
}
