# API de Productos Similares - Backend

## Descripción del Proyecto

Este proyecto implementa una API REST que devuelve productos similares a uno dado, siguiendo la especificación de contrato acordada. La aplicación está construida con Spring Boot y arquitectura reactiva para garantizar alto rendimiento, escalabilidad y resiliencia ante fallos.

## Tecnologías Utilizadas

- **Spring Boot 3.2.5**: Framework base para la aplicación
- **Spring WebFlux**: Para programación reactiva no bloqueante
- **Reactor**: Biblioteca para programación reactiva con soporte para operaciones asíncronas
- **Caffeine Cache**: Sistema de caché de alto rendimiento con soporte reactivo
- **Lombok**: Para reducir código boilerplate

## Arquitectura y Componentes

La aplicación sigue una arquitectura por capas:

```
Controller → Service → Client → API Externa
```

- **Controller**: Expone el endpoint REST y maneja la respuesta HTTP
- **Service**: Contiene la lógica de negocio y orquesta las llamadas
- **Client**: Gestiona la comunicación con APIs externas
- **Config**: Configuraciones del sistema (WebClient, Cache, etc.)
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

### 3. Paralelización y Concurrencia

Diseñé un sistema de procesamiento paralelo configurable:

```java
return Flux.fromIterable(ids)
    .flatMap(id -> getProductDetailWithTimeout(id), concurrencyLevel);
```

- **Nivel de Paralelismo Configurable**: Ajustable mediante propiedades (`app.similar-products.concurrency`)
- **Procesamiento Independiente**: Cada producto se procesa de forma aislada para evitar que fallos únicos afecten al conjunto

### 4. Gestión de Timeouts y Resiliencia

Implementé un sistema de timeouts en varios niveles:

```java
return productApiClient.getProductDetail(id)
    .timeout(Duration.ofSeconds(3))
    .onErrorResume(e -> {
        if (e instanceof TimeoutException) {
            logger.warn("Timeout al obtener detalle para producto {}", id);
            return Mono.empty();
        }
        // Error handling...
    });
```

- **Resultados Parciales**: Si un producto falla, continúa con el resto
- **Timeouts Configurables**: Por producto y por operación completa
- **Reintentos Limitados**: Solo para errores recuperables

### 5. WebClient Optimizado

Configuré un WebClient con:

- **Pool de Conexiones Administrado**: Para evitar el agotamiento de conexiones
- **Timeouts Explícitos**: Para conexión y respuesta
- **Buffer Ampliado**: Para manejar respuestas más grandes

## Optimizaciones Implementadas

1. **Caché Reactivo**:

   - Reduce llamadas a servicios externos
   - Mejora drásticamente tiempo de respuesta para peticiones repetidas

2. **Procesamiento en Paralelo**:

   - Solicita detalles de múltiples productos simultáneamente
   - Nivel de paralelismo configurable según capacidad del sistema

3. **Manejo de Fallos**:

   - Degradación elegante ante fallos parciales
   - Continúa operando incluso cuando algunos productos no responden
   - Timeouts individuales para evitar bloqueos

4. **Optimización de Recursos**:
   - Uso de Scheduler específico para operaciones I/O
   - Control de conexiones máximas y tiempos de espera

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
### Pruebas de Rendimiento

Para ejecutar tests de carga:

```
docker-compose run --rm k6 run scripts/test.js
```

Visualizar resultados en: `http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`

## Desafíos Superados

1. **Compatibilidad Reactiva con Caché**: Configuración especial de caché asíncrono
2. **Timeouts Individuales**: Manejo de timeouts por producto sin afectar al resto
3. **Optimización de Rendimiento**: Balance entre paralelismo y uso de recursos
4. **Manejo de Errores Consistente**: Respuestas coherentes incluso con fallos parciales

---

Este proyecto demuestra mi enfoque en construir sistemas resilientes, de alto rendimiento y mantenibles, equilibrando la complejidad técnica con soluciones pragmáticas.
