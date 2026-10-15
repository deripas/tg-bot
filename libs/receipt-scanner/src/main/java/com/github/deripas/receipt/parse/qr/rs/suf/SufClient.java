package com.github.deripas.receipt.parse.qr.rs.suf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client of the verification service of Serbian fiscal receipts (SUF of the Tax Administration). Every receipt
 * has a QR code with a link to the service, {@code https://suf.purs.gov.rs/v/?vl=...}:
 * <ul>
 *     <li>the link asked for JSON gives the seller, the total, the time and the text of the receipt;</li>
 *     <li>the page of the link has a token for the specification: the items with their numbers.
 *     The specification is kept for recent receipts only.</li>
 * </ul>
 */
public class SufClient {

    public static final URI DEFAULT_BASE_URI = URI.create("https://suf.purs.gov.rs/");
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final String VERIFICATION_PATH = "/v/";
    private static final Pattern TOKEN = Pattern.compile("viewModel\\.Token\\('([^']+)'\\)");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HttpClient http;
    private final URI baseUri;
    private final Duration timeout;

    public SufClient() {
        this(DEFAULT_BASE_URI, DEFAULT_TIMEOUT);
    }

    /**
     * @param baseUri address of the service, also the only one the links are accepted from
     */
    public SufClient(URI baseUri, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUri = baseUri;
        this.timeout = timeout;
    }

    /**
     * @param text text of a QR code
     * @return whether the text is a link to a receipt in this service; other links are not followed
     */
    public boolean isVerificationLink(String text) {
        try {
            final URI uri = new URI(text.strip());
            return Objects.equals(uri.getScheme(), baseUri.getScheme())
                    && Objects.equals(uri.getHost(), baseUri.getHost())
                    && uri.getPort() == baseUri.getPort()
                    && VERIFICATION_PATH.equals(uri.getPath())
                    && uri.getRawQuery() != null && uri.getRawQuery().startsWith("vl=");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param link link of the QR code, see {@link #isVerificationLink(String)}
     */
    public SufInvoice invoice(URI link) throws IOException, InterruptedException {
        return mapper.readValue(get(link, "application/json"), SufInvoice.class);
    }

    /**
     * @param link          link of the QR code
     * @param invoiceNumber number of the receipt, see {@link SufInvoice.InvoiceResult#invoiceNumber()}
     * @return items of the receipt
     * @throws IOException if the service has no specification of the receipt, e.g. the receipt is old
     */
    public List<SufItem> specification(URI link, String invoiceNumber) throws IOException, InterruptedException {
        final String page = get(link, "text/html");
        final Matcher token = TOKEN.matcher(page);
        if (!token.find()) {
            throw new IOException("No specification token on the page of receipt " + invoiceNumber);
        }
        final String form = "invoiceNumber=" + URLEncoder.encode(invoiceNumber, StandardCharsets.UTF_8)
                + "&token=" + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
        final HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("specifications"))
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        final Specification specification = mapper.readValue(send(request), Specification.class);
        if (!specification.success() || specification.items() == null) {
            throw new IOException("The service has no specification of receipt " + invoiceNumber);
        }
        return specification.items();
    }

    private String get(URI link, String accept) throws IOException, InterruptedException {
        if (!isVerificationLink(link.toString())) {
            throw new IllegalArgumentException("Not a receipt link of " + baseUri + ": " + link);
        }
        return send(HttpRequest.newBuilder(link).timeout(timeout).header("Accept", accept).GET().build());
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Receipt service responded " + response.statusCode() + " to " + request.uri());
        }
        return response.body();
    }

    /**
     * Answer of {@code POST /specifications}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Specification(boolean success, List<SufItem> items) {
    }
}
