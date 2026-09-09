package com.flexcore.outbox;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class OutboxEventHandlerRegistry {

    private final Map<String, OutboxEventHandler<?>> handlers;

    public OutboxEventHandlerRegistry(List<OutboxEventHandler<?>> handlers) {
        this.handlers = Collections.unmodifiableMap(handlers.stream().collect(Collectors.toMap(
                OutboxEventHandler::eventType,
                handler -> handler,
                (left, right) -> {
                    throw new IllegalStateException("Duplicate outbox handler for event type " + left.eventType());
                },
                LinkedHashMap::new)));
    }

    public Optional<OutboxEventHandler<?>> find(String eventType) {
        return Optional.ofNullable(handlers.get(eventType));
    }
}