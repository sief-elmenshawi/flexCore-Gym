package com.flexcore.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the outbox → broker → consumer flow:
 *
 * <pre>
 * OutboxRabbitPublisher ──► flexcore.events (direct) ──booking.confirmed──► flexcore.notification.queue
 *                                                                                     │ on repeated failure
 *                                                                                     ▼
 *                                                            flexcore.events.dlx ──► flexcore.notification.dead.queue
 * </pre>
 *
 * The notification queue is registered on the dead-letter exchange, so a consumer that
 * keeps rejecting a message after spring's retry interceptor (max-attempts) has exhausted
 * moves it to the dead-letter queue for inspection instead of losing it. Declarations are
 * created lazily by {@code RabbitAdmin} once the first connection succeeds.
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RabbitConfig {

    @Bean
    public DirectExchange eventExchange(@Value("${app.rabbit.exchange:flexcore.events}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(@Value("${app.rabbit.dead-letter-exchange:flexcore.events.dlx}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue notificationQueue(
            @Value("${app.rabbit.notification-queue:flexcore.notification.queue}") String name,
            @Value("${app.rabbit.dead-letter-exchange:flexcore.events.dlx}") String dlx,
            @Value("${app.rabbit.dead-letter-routing-key:notification.dead}") String dlxRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(dlxRoutingKey)
                .build();
    }

    @Bean
    public Queue deadLetterQueue(@Value("${app.rabbit.dead-letter-queue:flexcore.notification.dead.queue}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange eventExchange,
                                       @Value("${app.rabbit.routing-key:booking.confirmed}") String routingKey) {
        return BindingBuilder.bind(notificationQueue).to(eventExchange).with(routingKey);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange,
                                     @Value("${app.rabbit.dead-letter-routing-key:notification.dead}") String routingKey) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(routingKey);
    }

    /**
     * Uses the Spring-managed mapper (JSR-310 module registered) so the record's
     * {@code LocalDateTime} field survives JSON serialization.
     */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate the outbox publisher sends through. {@code mandatory=true} +
     * {@code publisher-returns} make an unroutable message come back to the
     * {@code returnsCallback} (logged) instead of being silently discarded, and the
     * publisher couples it with a {@code CorrelationData} future so the outbox only
     * marks a row PUBLISHED once the broker has confirmed it accepted the message.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> log.warn(
                "Unroutable RabbitMQ message for exchange '{}' routing key '{}': {}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}