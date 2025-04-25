package com.aluquerivas.similar_products.service;

import com.aluquerivas.similar_products.client.ProductApiClient;
import com.aluquerivas.similar_products.model.ProductDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private final ProductApiClient productApiClient;
    
    private final int concurrencyLevel;
    private final Duration productDetailTimeout;

    @Autowired
    public ProductService(
            ProductApiClient productApiClient,
            @Value("${app.similar-products.concurrency:8}") int concurrencyLevel,
            @Value("${app.similar-products.timeout.product-detail:3000}") int productDetailTimeoutMs) {
        this.productApiClient = productApiClient;
        this.concurrencyLevel = concurrencyLevel;
        this.productDetailTimeout = Duration.ofMillis(productDetailTimeoutMs);
        
        logger.info("Servicio de productos inicializado con nivel de concurrencia: {} y timeout de detalle: {}ms", 
                   concurrencyLevel, productDetailTimeoutMs);
    }

    public Flux<ProductDetail> getSimilarProducts(String productId) {
        logger.debug("Buscando productos similares para productId: {}", productId);
        
        return productApiClient.getSimilarProductIds(productId)
                .collectList()
                .flatMapMany(ids -> {
                    logger.debug("IDs de productos similares obtenidos: {}", ids);
                    
                    if (ids.isEmpty()) {
                        logger.debug("No se encontraron productos similares para {}", productId);
                        return Flux.empty();
                    }
                    
                    // Procesamos cada ID de forma independiente para que un fallo no afecte a los demás
                    return Flux.fromIterable(ids)
                            .flatMap(id -> 
                                // Aquí tratamos cada producto por separado para que los timeouts sean independientes
                                productApiClient.getProductDetail(id)
                                    .timeout(productDetailTimeout)
                                    .onErrorResume(e -> {
                                        if (e instanceof TimeoutException) {
                                            logger.warn("Timeout al obtener el producto {}, continuando con el resto", id);
                                        } else {
                                            logger.warn("Error al obtener producto {}: {}, continuando con el resto", id, e.getMessage());
                                        }
                                        return Mono.empty(); // Ignoramos este producto y continuamos con el resto
                                    }), 
                                concurrencyLevel
                            );
                })
                .doOnComplete(() -> logger.debug("Completada búsqueda de productos similares para productId: {}", productId))
                .doOnError(e -> logger.error("Error general al buscar productos similares para productId {}: {}", productId, e.getMessage()));
    }
}
