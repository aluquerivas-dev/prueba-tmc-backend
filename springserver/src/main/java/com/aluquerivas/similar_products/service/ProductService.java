package com.aluquerivas.similar_products.service;

import com.aluquerivas.similar_products.client.ProductApiClient;
import com.aluquerivas.similar_products.model.ProductDetail;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry meterRegistry;
    
    private final int concurrencyLevel;
    private final Duration productDetailTimeout;

    @Autowired
    public ProductService(
            ProductApiClient productApiClient,
            MeterRegistry meterRegistry,
            @Value("${app.similar-products.concurrency:8}") int concurrencyLevel,
            @Value("${app.similar-products.timeout.product-detail:3000}") int productDetailTimeoutMs) {
        this.productApiClient = productApiClient;
        this.meterRegistry = meterRegistry;
        this.concurrencyLevel = concurrencyLevel;
        this.productDetailTimeout = Duration.ofMillis(productDetailTimeoutMs);
        
        logger.info("Servicio de productos inicializado con nivel de concurrencia: {} y timeout de detalle: {}ms", 
                   concurrencyLevel, productDetailTimeoutMs);
    }

    @Timed(value = "get_similar_products_service", description = "Tiempo total para obtener productos similares")
    public Flux<ProductDetail> getSimilarProducts(String productId) {
        logger.debug("Buscando productos similares para productId: {}", productId);
        
        // Registrar métrica de solicitud entrante
        meterRegistry.counter("similar_products.requests", "productId", productId).increment();
        
        // Timer para medir el tiempo total de procesamiento
        long startTime = System.currentTimeMillis();
        
        return productApiClient.getSimilarProductIds(productId)
                .collectList()
                .flatMapMany(ids -> {
                    logger.debug("IDs de productos similares obtenidos: {}", ids);
                    
                    if (ids.isEmpty()) {
                        logger.debug("No se encontraron productos similares para {}", productId);
                        return Flux.empty();
                    }
                    
                    // Registrar métrica de cantidad de IDs encontrados
                    meterRegistry.gauge("similar_products.count", ids.size());
                    
                    // Procesamos cada ID de forma independiente para que un fallo no afecte a los demás
                    return Flux.fromIterable(ids)
                            .flatMap(id -> 
                                // Tratamiento individualizado con timeout por producto
                                getProductDetailWithFallback(id)
                                    .timeout(productDetailTimeout)
                                    .onErrorResume(e -> {
                                        if (e instanceof TimeoutException) {
                                            logger.warn("Timeout al obtener el producto {}, continuando con el resto", id);
                                        } else {
                                            logger.warn("Error al obtener producto {}: {}, continuando con el resto", id, e.getMessage());
                                        }
                                        // Registrar métricas de errores
                                        String errorType = e instanceof TimeoutException ? "timeout" : "error";
                                        meterRegistry.counter("similar_products.errors", "type", errorType).increment();
                                        return Mono.empty(); // Ignoramos este producto y continuamos con el resto
                                    }), 
                                concurrencyLevel
                            );
                })
                .doOnComplete(() -> {
                    logger.debug("Completada búsqueda de productos similares para productId: {}", productId);
                    // Registrar tiempo total de procesamiento
                    long processingTime = System.currentTimeMillis() - startTime;
                    meterRegistry.timer("similar_products.processing_time").record(Duration.ofMillis(processingTime));
                })
                .doOnError(e -> {
                    logger.error("Error general al buscar productos similares para productId {}: {}", productId, e.getMessage());
                    meterRegistry.counter("similar_products.general_errors").increment();
                });
    }
    
    /**
     * Obtiene el detalle de un producto con una estrategia de fallback en caso de error.
     */
    private Mono<ProductDetail> getProductDetailWithFallback(String productId) {
        return productApiClient.getProductDetail(productId)
                .onErrorResume(e -> {
                    if (e instanceof ProductApiClient.ProductNotFoundException) {
                        // Si el producto no existe, no hay fallback posible
                        logger.info("Producto {} no encontrado, sin fallback disponible", productId);
                        return Mono.empty();
                    } else {
                        // Para otros errores, podríamos intentar recuperar datos parciales de un caché secundario
                        logger.info("Error al obtener producto {}, intentando fallback", productId);
                        return getProductDetailFallback(productId);
                    }
                });
    }
    
    /**
     * Método de fallback para intentar obtener información mínima del producto.
     * En un escenario real, esto podría ser una consulta a una caché secundaria o una base de datos local.
     */
    private Mono<ProductDetail> getProductDetailFallback(String productId) {
        // Esta es una implementación simplificada de fallback
        // En un escenario real, podríamos consultar una base de datos local o un caché secundario
        logger.debug("Aplicando fallback para producto {}", productId);
        return Mono.empty(); // Por ahora simplemente retornamos vacío
        
        // Ejemplo de un fallback real:
        // return Mono.fromCallable(() -> secondaryRepository.findBasicProductInfo(productId))
        //    .subscribeOn(Schedulers.boundedElastic());
    }
}
