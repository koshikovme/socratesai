package com.masters.socratesai.websocket.config;

import com.masters.socratesai.config.FrontendOriginConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final FrontendOriginConfig frontendOriginConfig;
    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    public WebSocketConfig(
            FrontendOriginConfig frontendOriginConfig,
            WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor
    ) {
        this.frontendOriginConfig = frontendOriginConfig;
        this.webSocketAuthChannelInterceptor = webSocketAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(frontendOriginConfig.allowedOriginPatterns())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
