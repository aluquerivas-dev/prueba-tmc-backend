package com.aluquerivas.similar_products.client;

import com.aluquerivas.similar_products.model.ProductDetail;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
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

import java.util.Collections;
import java.util.List;

@Component
public class ProductApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ProductApiClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "productApiClient";
    private static final String RETRY_NAME = "productApiClient";
    private static final String BULKHEAD_NAME = "productApiClient";
    private static final String TIME_LIMITER_NAME = "productApiClient";
    
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ProductApiClient(
            WebClient webClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            MeterRegistry meterRegistry,
            @Value("${app.similar-products.timeout.api-client:2000}") int apiTimeoutMs) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        this.retry = retryRegistry.retry(RETRY_NAME);
        this.bulkhead = bulkheadRegistry.bulkhead(BULKHEAD_NAME);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(TIME_LIMITER_NAME);
        this.meterRegistry = meterRegistry;
        
        // Registrar estado del circuit breaker para métricas
        circuitBreaker.getEventPublisher()
            .onSuccess(event -> incrementCounter("circuit_breaker.success", event.getCircuitBreakerName()))
            .onError(event -> incrementCounter("circuit_breaker.error", event.getCircuitBreakerName()))
            .onStateTransition(event -> logger.info("Circuit breaker '{}' cambió de {} a {}", 
                event.getCircuitBreakerName(), event.getStateTransition().getFromState(), 
                event.getStateTransition().getToState()));
        
        logger.info("Cliente API inicializado con timeout: {}ms y patrones de resiliencia configurados", apiTimeoutMs);
    }

    private void incrementCounter(String name, String circuitBreakerName) {
        meterRegistry.counter(name, List.of(Tag.of("name", circuitBreakerName))).increment();
    }

    @Cacheable(value = "similarProductIds", key = "#productId")
    @Timed(value = "get_similar_product_ids", description = "Tiempo para obtener IDs de productos similares")
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
                .transform(TimeLimiterOperator.of(timeLimiter))
                .transform(BulkheadOperator.of(bulkhead))
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .transform(RetryOperator.of(retry))
                .onErrorResume(e -> {
                    if (e instanceof ProductNotFoundException) {
                        logger.warn("Productos similares no encontrados para {}", productId);
                    } else {
                        logger.error("Error al obtener IDs de productos similares para {}: {}", 
                                    productId, e.getMessage());
                    }
                    return Flux.fromIterable(Collections.emptyList());
                });
    }

    @Cacheable(value = "productDetails", key = "#productId")
    @Timed(value = "get_product_detail", description = "Tiempo para obtener detalle de producto")
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
                .transform(TimeLimiterOperator.of(timeLimiter))
                .transform(BulkheadOperator.of(bulkhead))
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .transform(RetryOperator.of(retry))
                .onErrorResume(e -> {
                    if (e instanceof ProductNotFoundException) {
                        logger.warn("Producto no encontrado: {}", productId);
                    } else {
                        logger.error("Error al obtener detalles del producto {}: {}", 
                                    productId, e.getMessage());
                    }
                    // Para fallos graves, podríamos devolver un producto degradado
                    return fallbackProductDetail(productId, e);
                });
    }
    
    /**
     * Proporciona un producto de respaldo en caso de fallo en la obtención del detalle.
     * Este es un ejemplo de degradación de servicio elegante.
     */
    private Mono<ProductDetail> fallbackProductDetail(String productId, Throwable e) {
        if (e instanceof ProductNotFoundException) {
            // Si el producto no existe, propagamos el error
            return Mono.error(e);
        }
        
        // Para otros errores, podemos proporcionar un producto genérico 
        // o datos parciales almacenados en caché persistente
        logger.info("Aplicando fallback para producto {}", productId);
        
        // En un escenario real podrías obtener datos parciales de otro almacén
        // o construir una respuesta degradada con información mínima
        return Mono.empty();
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
}
