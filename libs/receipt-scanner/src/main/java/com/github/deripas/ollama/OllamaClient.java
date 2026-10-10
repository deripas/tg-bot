package com.github.deripas.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Minimal client of the Ollama chat API: one user message with images. Only the transport: the model and its
 * parameters come with each request.
 */
public class OllamaClient {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final OllamaClient DEFAULT = new OllamaClient(DEFAULT_BASE_URL, DEFAULT_TIMEOUT);

    /**
     * Characters of a prompt per token, with a margin: 4.4 measured on the English receipt prompts
     * with qwen2.5vl:7b.
     */
    static final int PROMPT_CHARS_PER_TOKEN = 3;
    /**
     * Tokens of the chat template around the prompt and the images.
     */
    static final int TEMPLATE_TOKENS = 32;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HttpClient http;
    private final URI chatUri;
    private final Duration timeout;

    public OllamaClient(String baseUrl, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.chatUri = URI.create(baseUrl.replaceAll("/+$", "") + "/api/chat");
        this.timeout = timeout;
    }

    /**
     * @param type class of the answer, annotated with {@link ModelAnswer}; gives the JSON schema of the answer,
     *             passed as Ollama {@code format}, and its limit
     * @return answer parsed into {@code type}
     * @throws IllegalArgumentException if the images, the prompt and the answer may not fit the model context:
     *                                  Ollama would silently drop the beginning of the request
     */
    public <T> T ask(OllamaRequest request, Class<T> type) throws IOException, InterruptedException {
        final int maxTokens = maxTokens(type);
        checkContext(request, maxTokens);
        final VisionModel model = request.model();
        final ChatRequest chat = new ChatRequest(
                model.name(),
                List.of(new Message("user", request.prompt(), jpegs(request.images()))),
                false,
                JsonSchemas.of(type),
                new Options(0, maxTokens, model.contextTokens()));
        return mapper.readValue(chat(chat), type);
    }

    private static int maxTokens(Class<?> type) {
        final ModelAnswer answer = type.getAnnotation(ModelAnswer.class);
        if (answer == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @"
                    + ModelAnswer.class.getSimpleName());
        }
        return answer.maxTokens();
    }

    static void checkContext(OllamaRequest request, int maxTokens) {
        final VisionModel model = request.model();
        int imageTokens = 0;
        for (BufferedImage image : request.images()) {
            imageTokens += model.imageTokens(new Dimension(image.getWidth(), image.getHeight()));
        }
        final int promptTokens = TEMPLATE_TOKENS + Math.ceilDiv(request.prompt().length(), PROMPT_CHARS_PER_TOKEN);
        final int context = imageTokens + promptTokens + maxTokens;
        if (context > model.contextTokens()) {
            throw new IllegalArgumentException("Request to " + model.name() + " may need " + context
                    + " tokens of context, more than " + model.contextTokens() + ": images " + imageTokens
                    + ", prompt about " + promptTokens + ", answer up to " + maxTokens);
        }
    }

    private static List<String> jpegs(List<BufferedImage> images) throws IOException {
        final List<String> jpegs = new ArrayList<>(images.size());
        for (BufferedImage image : images) {
            jpegs.add(Base64.getEncoder().encodeToString(toJpeg(image)));
        }
        return jpegs;
    }

    /**
     * A copy of {@code ImageUtils.toJpeg}: the client does not depend on the image package.
     */
    private static byte[] toJpeg(BufferedImage image) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // there is no JPEG writer for images with alpha, and write returns false instead of failing
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IOException("Failed to encode JPEG from image type " + image.getType());
        }
        return out.toByteArray();
    }

    /**
     * @return content of the answer message
     */
    private String chat(ChatRequest chat) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(chatUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(chat)))
                .build();
        final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ollama responded " + response.statusCode() + ": " + response.body());
        }
        final ChatResponse answer = mapper.readValue(response.body(), ChatResponse.class);
        if (answer.message() == null || answer.message().content() == null) {
            throw new IOException("Ollama response has no message content: " + response.body());
        }
        return answer.message().content();
    }

    /**
     * Body of {@code POST /api/chat}; the fields are those of the Ollama API, the same for all models.
     *
     * @param format JSON schema the answer is constrained to
     */
    record ChatRequest(
            String model,
            List<Message> messages,
            boolean stream,
            Map<String, Object> format,
            Options options
    ) {
    }

    /**
     * @param images base64 JPEG images; none in an answer
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Message(String role, String content, List<String> images) {
    }

    /**
     * @param numPredict limit of the answer in tokens
     * @param numCtx     context of the request in tokens
     */
    record Options(
            double temperature,
            @JsonProperty("num_predict") int numPredict,
            @JsonProperty("num_ctx") int numCtx
    ) {
    }

    /**
     * Answer of {@code /api/chat} without streaming; timings and token counts are skipped.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(Message message) {
    }
}
