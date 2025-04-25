package com.aluquerivas.similar_products.controller;

import com.aluquerivas.similar_products.client.ProductApiClient;
import com.aluquerivas.similar_products.model.ProductDetail;
import com.aluquerivas.similar_products.service.ProductService;
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
    private final Duration globalRequestTimeout;

    @Autowired
    public ProductController(
            ProductService productService,
            @Value("${app.similar-products.timeout.global-request:10000}") int globalRequestTimeoutMs) {
        this.productService = productService;
        this.globalRequestTimeout = Duration.ofMillis(globalRequestTimeoutMs);
        logger.info("Controlador de productos inicializado con timeout global: {}ms", globalRequestTimeoutMs);
    }

    @GetMapping("/{productId}/similar")
    public Flux<ProductDetail> getSimilarProducts(@PathVariable String productId) {
        logger.debug("Recibida solicitud para productos similares a: {}", productId);
        
        return productService.getSimilarProducts(productId)
                // Aplicamos timeout global configurable
                .timeout(globalRequestTimeout)
                .onErrorResume(e -> {
                    if (e instanceof TimeoutException) {
                        logger.warn("Timeout global al procesar solicitud para productId: {}", productId);
                        return Flux.empty(); // Devolvemos lo que tengamos hasta ahora
                    } else if (e instanceof ProductApiClient.ProductNotFoundException) {
                        logger.warn("Producto no encontrado: {}", productId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
                    } else {
                        logger.error("Error procesando solicitud para {}: {}", productId, e.getMessage());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request", e);
                    }
                });
    }
    
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<String>> handleResponseStatusException(ResponseStatusException ex) {
        return Mono.just(
            ResponseEntity.status(ex.getStatusCode())
                .body(ex.getReason())
        );
    }
}
