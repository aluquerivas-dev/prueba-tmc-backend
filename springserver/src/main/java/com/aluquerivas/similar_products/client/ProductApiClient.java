package com.aluquerivas.similar_products.client;

import com.aluquerivas.similar_products.model.ProductDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class ProductApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ProductApiClient.class);
    private final WebClient webClient;
    private final Duration apiTimeout;

    @Autowired
    public ProductApiClient(
            WebClient webClient,
            @Value("${app.similar-products.timeout.api-client:2000}") int apiTimeoutMs) {
        this.webClient = webClient;
        this.apiTimeout = Duration.ofMillis(apiTimeoutMs);
        logger.info("Cliente API inicializado con timeout: {}ms", apiTimeoutMs);
    }

    @Cacheable(value = "similarProductIds", key = "#productId")
    public Flux<String> getSimilarProductIds(String productId) {
        logger.debug("Obteniendo IDs de productos similares para productId: {}", productId);
        return webClient.get()
                .uri("/product/{productId}/similarids", productId)
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    logger.warn("Error al obtener IDs similares. Status: {}", response.statusCode());
                    if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return Mono.error(new ProductNotFoundException("Similar products not found for product: " + productId));
                    }
                    return Mono.error(new RuntimeException("Error fetching similar product ids: " + response.statusCode()));
                })
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .flatMapMany(Flux::fromIterable)
                .doOnNext(id -> logger.debug("ID similar obtenido: {}", id))
                .doOnComplete(() -> logger.debug("Completada la obtención de IDs similares"))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(100))
                        .filter(throwable -> !(throwable instanceof ProductNotFoundException))
                        .maxBackoff(Duration.ofSeconds(1)));
    }

    @Cacheable(value = "productDetails", key = "#productId")
    public Mono<ProductDetail> getProductDetail(String productId) {
        logger.debug("Obteniendo detalles del producto para productId: {}", productId);
        return webClient.get()
                .uri("/product/{productId}", productId)
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    logger.warn("Error al obtener detalles. Status: {}", response.statusCode());
                    if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return Mono.error(new ProductNotFoundException("Product not found: " + productId));
                    }
                    return Mono.error(new RuntimeException("Error fetching product details: " + response.statusCode()));
                })
                .bodyToMono(ProductDetail.class)
                .doOnSuccess(product -> logger.debug("Detalles obtenidos para producto: {}", productId))
                .timeout(apiTimeout)
                .onErrorResume(TimeoutException.class, e -> {
                    logger.warn("Timeout al obtener detalles del producto {}", productId);
                    return Mono.empty();
                })
                .retryWhen(Retry.backoff(1, Duration.ofMillis(100))
                        .filter(throwable -> !(throwable instanceof ProductNotFoundException || 
                                              throwable instanceof TimeoutException))
                        .maxBackoff(Duration.ofSeconds(1))
                        .maxAttempts(1));
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
}
