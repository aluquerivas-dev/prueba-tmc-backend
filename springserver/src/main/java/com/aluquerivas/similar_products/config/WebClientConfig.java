package com.aluquerivas.similar_products.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${external.api.base-url}")
    private String baseUrl;

    @Value("${external.api.connection-timeout}")
    private int connectionTimeout;

    @Value("${external.api.response-timeout}")
    private int responseTimeout;
    
    @Value("${external.api.max-connections:500}")
    private int maxConnections;

    @Bean
    public WebClient webClient() {
        logger.info("Configurando WebClient con baseUrl: {} y maxConnections: {}", baseUrl, maxConnections);
        
        // Configuración de un pool de conexiones optimizado
        ConnectionProvider connectionProvider = ConnectionProvider.builder("optimized-pool")
                .maxConnections(maxConnections)
                .maxIdleTime(Duration.ofSeconds(60))
                .build();
        
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(connectionTimeout, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(connectionTimeout, TimeUnit.MILLISECONDS)));

        // Aumentar el tamaño del buffer para mensajes más grandes
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
