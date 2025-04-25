# Backend dev technical test
We want to offer a new feature to our customers showing similar products to the one they are currently seeing. To do this we agreed with our front-end applications to create a new REST API operation that will provide them the product detail of the similar products for a given one. [Here](./similarProducts.yaml) is the contract we agreed.

We already have an endpoint that provides the product Ids similar for a given one. We also have another endpoint that returns the product detail by product Id. [Here](./existingApis.yaml) is the documentation of the existing APIs.

**Create a Spring boot application that exposes the agreed REST API on port 5000.**

![Diagram](./assets/diagram.jpg "Diagram")

Note that _Test_ and _Mocks_ components are given, you must only implement _yourApp_.

## Testing and Self-evaluation
You can run the same test we will put through your application. You just need to have docker installed.

First of all, you may need to enable file sharing for the `shared` folder on your docker dashboard -> settings -> resources -> file sharing.

Then you can start the mocks and other needed infrastructure with the following command.
```
docker-compose up -d simulado influxdb grafana
```
Check that mocks are working with a sample request to [http://localhost:3001/product/1/similarids](http://localhost:3001/product/1/similarids).

To execute the test run:
```
docker-compose run --rm k6 run scripts/test.js
```
Browse [http://localhost:3000/d/Le2Ku9NMk/k6-performance-test](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test) to view the results.

## Evaluation
The following topics will be considered:
- Code clarity and maintainability
- Performance
- Resilience

## Ejecutar la aplicación Spring Boot

### Opción 1: Usando Maven desde la línea de comandos

1. Navega al directorio del proyecto Spring Boot:
   ```
   cd springserver
   ```

2. Compila y ejecuta la aplicación:
   ```
   ./mvnw spring-boot:run
   ```
   
   En Windows usar:
   ```
   mvnw.cmd spring-boot:run
   ```

### Opción 2: Usando el JAR generado

1. Compila y empaqueta la aplicación:
   ```
   cd springserver
   ./mvnw clean package
   ```
   
   En Windows:
   ```
   mvnw.cmd clean package
   ```

2. Ejecuta el JAR generado:
   ```
   java -jar target/similar-products-0.0.1-SNAPSHOT.jar
   ```

### Verificar que la aplicación está funcionando

Una vez que la aplicación esté ejecutándose, puedes verificar que funciona correctamente haciendo una solicitud a:

```
http://localhost:5000/product/1/similar
```

Esta petición debería devolver una lista de productos similares al producto con ID 1.

## Solución de problemas comunes

### Problema: La API devuelve un array vacío

Si al hacer una solicitud como `http://localhost:5000/product/2/similar` obtienes un array vacío `[]`, verifica lo siguiente:

1. Asegúrate que el servicio de mocks esté funcionando correctamente:
   ```
   curl http://localhost:3001/product/2/similarids
   curl http://localhost:3001/product/2
   ```

2. Verifica los logs de la aplicación para identificar posibles errores en el procesamiento de las respuestas.

3. Si los datos están llegando correctamente del mock pero la API sigue devolviendo un array vacío, puede haber un problema con la deserialización de la respuesta JSON. Revisa la implementación de `ProductApiClient` para asegurar que está procesando correctamente los arrays de IDs.
