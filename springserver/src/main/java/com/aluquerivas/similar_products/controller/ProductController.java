package com.aluquerivas.similar_products.controller;

import com.aluquerivas.similar_products.client.ProductApiClient;
import com.aluquerivas.similar_products.model.ProductDetail;
import com.aluquerivas.similar_products.service.ProductService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/product")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;
    private final MeterRegistry meterRegistry;
    private final Duration globalRequestTimeout;

    @Autowired
    public ProductController(
            ProductService productService,
            MeterRegistry meterRegistry,
            @Value("${app.similar-products.timeout.global-request:10000}") int globalRequestTimeoutMs) {
        this.productService = productService;
        this.meterRegistry = meterRegistry;
        this.globalRequestTimeout = Duration.ofMillis(globalRequestTimeoutMs);
        logger.info("Controlador de productos inicializado con timeout global: {}ms", globalRequestTimeoutMs);
    }

    @GetMapping("/{productId}/similar")
    @Timed(value = "get_similar_products_endpoint", description = "Tiempo de respuesta del endpoint de productos similares")
    public Flux<ProductDetail> getSimilarProducts(@PathVariable String productId) {
        logger.debug("Recibida solicitud para productos similares a: {}", productId);
        
        // Crear timer para medir duración total de la solicitud
        long startTime = System.currentTimeMillis();
        
        // Registrar métrica de solicitud entrante
        meterRegistry.counter("http.requests", "endpoint", "getSimilarProducts").increment();
        
        return productService.getSimilarProducts(productId)
                // Aplicamos timeout global configurable
                .timeout(globalRequestTimeout)
                .doFinally(signalType -> {
                    // Registrar tiempo de respuesta total
                    long duration = System.currentTimeMillis() - startTime;
                    meterRegistry.timer("http.response.time", "endpoint", "getSimilarProducts").record(Duration.ofMillis(duration));
                    logger.debug("Solicitud para productos similares a {} completada con señal {} en {}ms", 
                                productId, signalType, duration);
                })
                .onErrorResume(e -> {
                    if (e instanceof TimeoutException) {
                        logger.warn("Timeout global al procesar solicitud para productId: {}", productId);
                        meterRegistry.counter("http.errors", "type", "timeout").increment();
                        return Flux.empty(); // Devolvemos lo que tengamos hasta ahora
                    } else if (e instanceof ProductApiClient.ProductNotFoundException) {
                        logger.warn("Producto no encontrado: {}", productId);
                        meterRegistry.counter("http.errors", "type", "not_found").increment();
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
                    } else {
                        logger.error("Error procesando solicitud para {}: {}", productId, e.getMessage());
                        meterRegistry.counter("http.errors", "type", "server_error").increment();
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request", e);
                    }
                });
    }
    
    /**
     * Endpoint de health check para verificar la disponibilidad del servicio.
     * Útil para pruebas de humo y verificaciones de Kubernetes/contenedores.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> healthCheck() {
        return Mono.just(ResponseEntity.ok("Service is healthy"));
    }
    
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<String>> handleResponseStatusException(ResponseStatusException ex) {
        return Mono.just(
            ResponseEntity.status(ex.getStatusCode())
                .body(ex.getReason())
        );
    }
}
