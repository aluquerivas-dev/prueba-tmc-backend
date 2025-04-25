package com.aluquerivas.similar_products;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SimilarProductsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimilarProductsApplication.class, args);
	}

}
