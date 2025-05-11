# API de Productos Similares - Backend

## Descripción del Proyecto

Este proyecto implementa una API REST que devuelve productos similares a uno dado, siguiendo la especificación de contrato acordada. La aplicación está construida con Spring Boot y arquitectura reactiva para garantizar alto rendimiento, escalabilidad y resiliencia ante fallos.

## Tecnologías Utilizadas

- **Spring Boot 3.2.5**: Framework base para la aplicación
- **Spring WebFlux**: Para programación reactiva no bloqueante
- **Reactor**: Biblioteca para programación reactiva con soporte para operaciones asíncronas
- **Resilience4j**: Framework para implementar patrones de resiliencia
- **Caffeine Cache**: Sistema de caché de alto rendimiento con soporte reactivo
- **Micrometer**: Para métricas y monitorización
- **Prometheus**: Exposición de métricas para monitoreo
- **Lombok**: Para reducir código boilerplate

## Arquitectura y Componentes

La aplicación sigue una arquitectura por capas:

```
Controller → Service → Client → API Externa
```

- **Controller**: Expone el endpoint REST y maneja la respuesta HTTP
- **Service**: Contiene la lógica de negocio y orquesta las llamadas
- **Client**: Gestiona la comunicación con APIs externas con patrones de resiliencia
- **Config**: Configuraciones del sistema (WebClient, Cache, Resilience4j, etc.)
- **Model**: Objetos de dominio

## Decisiones Técnicas y Justificación

### 1. Programación Reactiva (WebFlux)

Elegí WebFlux en lugar de Spring MVC tradicional para:

- **Mayor Concurrencia**: Gestión eficiente de numerosas conexiones simultáneas
- **Operaciones No-bloqueantes**: Mejor utilización de recursos del sistema
- **Backpressure**: Control del flujo de datos cuando los consumidores están saturados

### 2. Sistema de Caché Optimizado

Implementé caché en dos niveles usando Caffeine:

```java
@Cacheable(value = "similarProductIds", key = "#productId")
public Flux<String> getSimilarProductIds(String productId) { ... }

@Cacheable(value = "productDetails", key = "#productId")
public Mono<ProductDetail> getProductDetail(String productId) { ... }
```

- **Caché Asíncrono**: Configurado específicamente para compatibilidad con operadores reactivos
- **Políticas de Expiración**: 5 minutos para equilibrar frescura de datos y eficiencia
- **Optimización de Memoria**: Límite de 1000 entradas para evitar problemas de memoria

### 3. Patrones de Resiliencia con Resilience4j

Implementé múltiples patrones de resiliencia para garantizar la estabilidad del sistema:

#### Circuit Breaker

Previene llamadas a servicios que están fallando repetidamente:

```java
CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
    .slidingWindowSize(10)
    .failureRateThreshold(50)
    .waitDurationInOpenState(Duration.ofSeconds(10))
    .build();
```

- **Ventana Deslizante**: Evalúa los últimos 10 llamados
- **Umbral de Fallos**: Si el 50% de las llamadas fallan, se abre el circuito
- **Tiempo de Espera en Estado Abierto**: 10 segundos antes de intentar llamadas nuevamente

#### Retry con Backoff Exponencial

Reintentos inteligentes para errores transitorios:

```java
RetryConfig retryConfig = RetryConfig.custom()
    .maxAttempts(3)
    .retryExceptions(IOException.class, ConnectException.class)
    .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 1.5, 5000))
    .build();
```

- **Máximo de Intentos**: 3 intentos antes de fallar
- **Backoff Exponencial**: Espera creciente entre intentos (1s, 1.5s, 2.25s)
- **Excepciones Específicas**: Solo reintenta errores de red, no errores de negocio

#### Time Limiter

Control estricto de timeouts:

```java
TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
    .timeoutDuration(Duration.ofSeconds(3))
    .cancelRunningFuture(true)
    .build();
```

- **Timeout Configurable**: 3 segundos por operación
- **Cancelación de Operaciones**: Libera recursos al cancelar operaciones con timeout

#### Bulkhead

Limita el número de llamadas concurrentes:

```java
BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
    .maxConcurrentCalls(20)
    .maxWaitDuration(Duration.ofMillis(500))
    .build();
```

- **Control de Concurrencia**: Máximo 20 llamadas simultáneas
- **Tiempo de Espera**: Si se alcanza el límite, las nuevas solicitudes esperan hasta 500ms

### 4. Paralelización y Concurrencia

Diseñé un sistema de procesamiento paralelo configurable:

```java
return Flux.fromIterable(ids)
    .flatMap(id -> getProductDetailWithFallback(id), concurrencyLevel);
```

- **Nivel de Paralelismo Configurable**: Ajustable mediante propiedades (`app.similar-products.concurrency`)
- **Procesamiento Independiente**: Cada producto se procesa de forma aislada para evitar que fallos únicos afecten al conjunto

### 5. Gestión de Errores y Degradación Elegante

Implementé múltiples niveles de fallback:

```java
return productApiClient.getProductDetail(productId)
    .onErrorResume(e -> {
        if (e instanceof ProductNotFoundException) {
            return Mono.error(e);  // Propagamos errores críticos
        } else {
            return getProductDetailFallback(productId);  // Fallback para otros errores
        }
    });
```

- **Degradación por Niveles**: Responde con resultados parciales si es posible
- **Fallbacks Personalizados**: Estrategias específicas por tipo de error
- **Registro Detallado**: Log de errores para diagnóstico posterior

### 6. Monitoreo y Métricas con Micrometer

Incluí métricas detalladas para observabilidad:

```java
@Timed(value = "get_similar_products_service")
public Flux<ProductDetail> getSimilarProducts(String productId) {
    meterRegistry.counter("similar_products.requests", "productId", productId).increment();
    // ...
}
```

- **Métricas de Rendimiento**: Tiempos de respuesta, tasas de éxito/error
- **Estado del Circuit Breaker**: Monitoreo del estado de resiliencia
- **Exposición Prometheus**: Endpoint para integración con sistemas de monitoreo
- **Anotaciones @Timed**: Medición automática de latencias

## Optimizaciones Implementadas

1. **Caché Reactivo**:
   - Reduce llamadas a servicios externos
   - Mejora drásticamente tiempo de respuesta para peticiones repetidas

2. **Procesamiento en Paralelo**:
   - Solicita detalles de múltiples productos simultáneamente
   - Nivel de paralelismo configurable según capacidad del sistema

3. **Manejo de Fallos**:
   - Circuit Breaker para prevenir cascadas de fallos
   - Degradación elegante ante fallos parciales
   - Timeouts individuales controlados con Time Limiter

4. **Optimización de Recursos**:
   - Bulkhead para controlar concurrencia
   - Gestión efectiva de conexiones HTTP

## Pruebas Unitarias y de Integración

El proyecto incluye tests unitarios utilizando StepVerifier para verificar el comportamiento reactivo:

```java
@Test
void getSimilarProducts_WithPartialFailure() {
    // Configuración...
    StepVerifier.create(productService.getSimilarProducts(productId))
        .expectNextMatches(product -> product.getId().equals("2"))
        .expectNextMatches(product -> product.getId().equals("4"))
        .verifyComplete();
}
```

- **Tests de Resiliencia**: Verificación de comportamiento ante fallos
- **StepVerifier**: Validación de flujos reactivos
- **Simulación de Timeouts**: Pruebas de comportamiento ante latencias elevadas

## Cómo Ejecutar la Aplicación

### Requisitos

- Java 17 o superior
- Maven 3.6+

### Pasos

1. Iniciar los servicios simulados:

   ```
   docker-compose up -d simulado influxdb grafana
   ```

2. Ejecutar la aplicación Spring Boot:

   ```
   cd springserver
   ./mvnw spring-boot:run
   ```

3. Probar la API:
   ```
   curl http://localhost:5000/product/1/similar
   ```
### Para construir el jar ejecutable
   Construye la aplicación
   ```bash
   
         # En Linux/Mac
      ./mvnw clean package

      # En Windows
      ./mvnw.cmd clean package

   ```
   Ejecuta el jar
   ```bash
      java -jar target/similar-products-0.0.1-SNAPSHOT.jar
   ```

### Monitoreo

La aplicación expone métricas en formato Prometheus:

```
http://localhost:5000/actuator/prometheus
```

También puedes ver el estado de los circuit breakers:

```
http://localhost:5000/actuator/circuitbreakers
```

### Pruebas de Rendimiento

Para ejecutar tests de carga:

```
docker-compose run --rm k6 run scripts/test.js
```

Visualizar resultados en: `http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`

## Desafíos Superados y Aprendizajes

1. **Integración de Resilience4j con WebFlux**:
   - Uso de operadores reactivos específicos (CircuitBreakerOperator, TimeLimiterOperator)
   - Configuración óptima para patrones de resiliencia en contextos reactivos

2. **Manejo Efectivo de Timeouts**:
   - Control multinivel (global, por servicio, por operación)
   - Cancelación apropiada de operaciones para liberar recursos

3. **Optimización de Rendimiento**:
   - Balance entre paralelismo y uso de recursos
   - Configuración de bulkhead para prevenir saturación

4. **Monitoreo Detallado**:
   - Diseño de métricas significativas para operaciones críticas
   - Exposición de datos de health para sistemas externos

### Monitoreo de patrones activos
```bash
# Ver estado actual de los circuit breakers
curl -X GET "http://localhost:5000/actuator/circuitbreakers"

# Ver métricas en formato Prometheus
curl -X GET "http://localhost:5000/actuator/prometheus" | grep resilience4j
```
