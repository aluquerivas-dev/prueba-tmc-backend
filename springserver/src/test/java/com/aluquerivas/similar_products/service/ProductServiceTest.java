package com.aluquerivas.similar_products.service;

import com.aluquerivas.similar_products.client.ProductApiClient;
import com.aluquerivas.similar_products.model.ProductDetail;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductApiClient productApiClient;

    private MeterRegistry meterRegistry;
    private ProductService productService;
    
    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        productService = new ProductService(productApiClient, meterRegistry, 5, 3000);
    }
    
    @Test
    void getSimilarProducts_Success() {
        // Preparar datos
        String productId = "1";
        when(productApiClient.getSimilarProductIds(productId))
                .thenReturn(Flux.fromIterable(Arrays.asList("2", "3", "4")));
        
        when(productApiClient.getProductDetail("2"))
                .thenReturn(Mono.just(new ProductDetail("2", "Dress", 19.99, true)));
        
        when(productApiClient.getProductDetail("3"))
                .thenReturn(Mono.just(new ProductDetail("3", "Blazer", 29.99, false)));
        
        when(productApiClient.getProductDetail("4"))
                .thenReturn(Mono.just(new ProductDetail("4", "Boots", 39.99, true)));
        
        // Ejecutar y verificar
        StepVerifier.create(productService.getSimilarProducts(productId))
                .expectNextMatches(product -> product.getId().equals("2") && product.getName().equals("Dress"))
                .expectNextMatches(product -> product.getId().equals("3") && product.getName().equals("Blazer"))
                .expectNextMatches(product -> product.getId().equals("4") && product.getName().equals("Boots"))
                .verifyComplete();
        
        // Verificar que se hicieron todas las llamadas necesarias
        verify(productApiClient).getSimilarProductIds(productId);
        verify(productApiClient).getProductDetail("2");
        verify(productApiClient).getProductDetail("3");
        verify(productApiClient).getProductDetail("4");
    }
    
    @Test
    void getSimilarProducts_WithPartialFailure() {
        // Preparar datos - un producto falla pero el resto son correctos
        String productId = "1";
        when(productApiClient.getSimilarProductIds(productId))
                .thenReturn(Flux.fromIterable(Arrays.asList("2", "3", "4")));
        
        when(productApiClient.getProductDetail("2"))
                .thenReturn(Mono.just(new ProductDetail("2", "Dress", 19.99, true)));
        
        when(productApiClient.getProductDetail("3"))
                .thenReturn(Mono.error(new RuntimeException("API Error")));
        
        when(productApiClient.getProductDetail("4"))
                .thenReturn(Mono.just(new ProductDetail("4", "Boots", 39.99, true)));
        
        // Ejecutar y verificar - debería funcionar a pesar del error en un producto
        StepVerifier.create(productService.getSimilarProducts(productId))
                .expectNextMatches(product -> product.getId().equals("2") && product.getName().equals("Dress"))
                .expectNextMatches(product -> product.getId().equals("4") && product.getName().equals("Boots"))
                .verifyComplete();
    }
    
    @Test
    void getSimilarProducts_WithTimeout() {
        // Preparar datos - un producto tiene timeout
        String productId = "1";
        when(productApiClient.getSimilarProductIds(productId))
                .thenReturn(Flux.fromIterable(Arrays.asList("2", "3")));
        
        when(productApiClient.getProductDetail("2"))
                .thenReturn(Mono.just(new ProductDetail("2", "Dress", 19.99, true)));
        
        when(productApiClient.getProductDetail("3"))
                .thenReturn(Mono.defer(() -> {
                    try {
                        Thread.sleep(5000); // Simular timeout
                        return Mono.just(new ProductDetail("3", "Blazer", 29.99, false));
                    } catch (InterruptedException e) {
                        return Mono.error(e);
                    }
                }));
        
        // Ejecutar y verificar - el producto con timeout debería ser omitido
        StepVerifier.create(productService.getSimilarProducts(productId))
                .expectNextMatches(product -> product.getId().equals("2") && product.getName().equals("Dress"))
                .verifyComplete();
    }
    
    @Test
    void getSimilarProducts_EmptyResult() {
        // Preparar datos - no hay productos similares
        String productId = "999";
        when(productApiClient.getSimilarProductIds(productId))
                .thenReturn(Flux.empty());
        
        // Ejecutar y verificar
        StepVerifier.create(productService.getSimilarProducts(productId))
                .verifyComplete();
        
        // Verificar que no se intentó obtener detalles
        verify(productApiClient, never()).getProductDetail(anyString());
    }
}
