package com.marketmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmind.model.CompetitorSnapshot;
import com.marketmind.model.Product;
import com.marketmind.repository.CompetitorSnapshotRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigDecimal;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes competitor listings via the Bright Data Web Unlocker, accessed through
 * the Bright Data super-proxy. Requests for the target marketplace URL are routed
 * through the proxy (which unlocks + renders the page and returns HTML). Falls
 * back to the pre-baked competitor snapshots in the DB whenever the proxy is
 * unconfigured, fails, or returns nothing parseable. Always logs whether the data
 * was [LIVE] or [MOCK], and never throws into the agent cycle.
 */
@Service
public class BrightDataService {

    private static final Logger log = LoggerFactory.getLogger(BrightDataService.class);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final CompetitorSnapshotRepository competitorRepo;

    @Value("${marketmind.brightdata.host:}")
    private String proxyHost;
    @Value("${marketmind.brightdata.port:0}")
    private int proxyPort;
    @Value("${marketmind.brightdata.username:}")
    private String proxyUsername;
    @Value("${marketmind.brightdata.password:}")
    private String proxyPassword;
    @Value("${marketmind.brightdata.mock-image-url:}")
    private String mockImageUrl;

    /** Lazily-built, dedicated proxy client (trusts the proxy's MITM cert). */
    private volatile HttpClient proxyClient;

    public BrightDataService(HttpClient http, ObjectMapper mapper,
                             CompetitorSnapshotRepository competitorRepo) {
        this.http = http;
        this.mapper = mapper;
        this.competitorRepo = competitorRepo;
    }

    private boolean isConfigured() {
        return StringUtils.hasText(proxyHost) && proxyPort > 0
                && StringUtils.hasText(proxyUsername) && StringUtils.hasText(proxyPassword);
    }

    public List<CompetitorSnapshot> scrapeCompetitors(Product product) {
        if (isConfigured()) {
            try {
                List<CompetitorSnapshot> live = liveScrape(product);
                if (!live.isEmpty()) {
                    List<CompetitorSnapshot> saved = competitorRepo.saveAll(live);
                    log.info("[LIVE] Bright Data scraped {} competitor(s) for {} via proxy {}:{}",
                            saved.size(), product.getId(), proxyHost, proxyPort);
                    return saved;
                }
                log.warn("[LIVE] Bright Data proxy fetch succeeded but parsed 0 listings for {} — using mock fallback",
                        product.getId());
            } catch (Exception e) {
                log.warn("[LIVE] Bright Data scrape failed for {} ({}) — using mock fallback",
                        product.getId(), e.getMessage());
            }
        } else {
            log.info("[MOCK] Bright Data proxy not configured — using seed competitor snapshots for {}",
                    product.getId());
        }
        return mockFallback(product);
    }

    private List<CompetitorSnapshot> mockFallback(Product product) {
        List<CompetitorSnapshot> seed = competitorRepo.findByProductIdOrderByScrapedAtDesc(product.getId());
        // Give snapshots without an image one to analyse, so the live SenseNova
        // path still runs on the mock data path. In-memory only — not persisted.
        if (StringUtils.hasText(mockImageUrl)) {
            for (CompetitorSnapshot s : seed) {
                if (!StringUtils.hasText(s.getImageUrl())) {
                    s.setImageUrl(mockImageUrl);
                }
            }
        }
        log.info("[MOCK] Loaded {} seed competitor snapshot(s) for {}", seed.size(), product.getId());
        return seed;
    }

    private List<CompetitorSnapshot> liveScrape(Product product) throws Exception {
        String targetUrl = "https://shopee.sg/search?keyword="
                + URLEncoder.encode(product.getCategory(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MarketMind/1.0")
                .GET()
                .build();

        HttpResponse<String> response = proxyClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            String errBody = response.body();
            throw new RuntimeException("Bright Data proxy HTTP " + response.statusCode() + ": "
                    + (errBody != null && errBody.length() > 300 ? errBody.substring(0, 300) + "…" : errBody));
        }
        log.info("[LIVE] Bright Data proxy fetched {} bytes for {}",
                response.body() == null ? 0 : response.body().length(), product.getId());
        return parseListings(response.body(), product);
    }

    /**
     * Build (once) a dedicated HttpClient that routes through the Bright Data
     * super-proxy with Basic proxy auth. The proxy terminates TLS (MITM) to unlock
     * pages, so this client trusts all certs and skips hostname verification —
     * scoped to scraping only, never the shared application client.
     */
    private HttpClient proxyClient() throws Exception {
        HttpClient local = proxyClient;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (proxyClient != null) {
                return proxyClient;
            }
            // Allow Basic proxy auth over HTTPS CONNECT tunnels + skip hostname check.
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

            Authenticator proxyAuth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
                    }
                    return null;
                }
            };

            proxyClient = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)))
                    .authenticator(proxyAuth)
                    .sslContext(trustAllSslContext())
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            return proxyClient;
        }
    }

    private static SSLContext trustAllSslContext() throws Exception {
        TrustManager[] trustAll = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }

    /**
     * Best-effort parse of a Shopee/Lazada search results page. Marketplace DOMs
     * change often; failures here just yield an empty list and the mock fallback.
     */
    private List<CompetitorSnapshot> parseListings(String html, Product product) {
        List<CompetitorSnapshot> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements cards = doc.select("[data-sqe=item], .shopee-search-item-result__item, li.col-xs-2-4");
        for (Element card : cards) {
            String name = text(card, ".shopee-item-card__text-name, ._10Wbs-, [data-sqe=name]");
            String priceText = text(card, ".shopee-item-card__current-price, ._1xk7ak, [class*=price]");
            String stockText = text(card, "[class*=sold], [class*=stock]");
            String imageUrl = card.select("img").stream().map(e -> e.absUrl("src"))
                    .filter(s -> !s.isBlank()).findFirst().orElse(null);

            BigDecimal price = parsePrice(priceText);
            if (name == null || price == null) {
                continue;
            }
            CompetitorSnapshot snap = new CompetitorSnapshot();
            snap.setProductId(product.getId());
            snap.setCompetitorName(name);
            snap.setPrice(price);
            snap.setStockIndicator(stockText);
            snap.setStockLevel(inferStockLevel(stockText));
            snap.setVisualSignals(new String[0]);
            snap.setImageUrl(imageUrl);
            snap.setDataSource("LIVE");
            results.add(snap);
        }
        return results;
    }

    private static String text(Element parent, String selector) {
        Element el = parent.selectFirst(selector);
        return el != null ? el.text().trim() : null;
    }

    private static BigDecimal parsePrice(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String inferStockLevel(String stockText) {
        if (stockText == null) {
            return "NORMAL";
        }
        String t = stockText.toLowerCase();
        if (t.contains("out of stock") || t.contains("sold out")) {
            return "OUT_OF_STOCK";
        }
        if (t.matches(".*only\\s*\\d+\\s*left.*") || t.contains("low stock")) {
            return "CRITICALLY_LOW";
        }
        return "NORMAL";
    }
}
