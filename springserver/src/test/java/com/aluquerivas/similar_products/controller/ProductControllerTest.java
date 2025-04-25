package com.aluquerivas.similar_products.controller;

import com.aluquerivas.similar_products.model.ProductDetail;
import com.aluquerivas.similar_products.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ProductController.class)
public class ProductControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProductService productService;

    @Test
    void testGetSimilarProducts() {
        // Datos de prueba
        String productId = "1";
        List<ProductDetail> expectedProducts = Arrays.asList(
                new ProductDetail("2", "Dress", 19.99, true),
                new ProductDetail("3", "Blazer", 29.99, false),
                new ProductDetail("4", "Boots", 39.99, true)
        );

        // Configurar mock
        when(productService.getSimilarProducts(productId)).thenReturn(Flux.fromIterable(expectedProducts));

        // Realizar petición HTTP
        webTestClient.get()
                .uri("/product/{productId}/similar", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProductDetail.class)
                .hasSize(3)
                .contains(expectedProducts.toArray(new ProductDetail[0]));
    }
}
