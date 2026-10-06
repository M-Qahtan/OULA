package com.oula.platform.idempotency;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private static final long DEFAULT_TTL_HOURS = 24;

    private final IdempotencyRepository repository;
    private final JsonMapper jsonMapper;

    public IdempotencyService(IdempotencyRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    public <T> IdempotentResult<T> execute(
            UUID workspaceId,
            String key,
            String operation,
            String requestHash,
            int responseStatus,
            Class<T> responseType,
            Supplier<T> action
    ) {
        validateKey(key);

        boolean claimed = repository.claim(
                workspaceId,
                key,
                operation,
                requestHash,
                repository.expiresInHours(DEFAULT_TTL_HOURS)
        );

        if (!claimed) {
            StoredIdempotencyRecord existing = repository.find(workspaceId, key)
                    .orElseThrow(() -> new IllegalStateException("idempotency claim disappeared"));

            if (!Objects.equals(existing.operation(), operation)
                    || !Objects.equals(existing.requestHash(), requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key was already used for a different request"
                );
            }

            if ("COMPLETED".equals(existing.state()) && existing.responseBody() != null) {
                return new IdempotentResult<>(
                        read(existing.responseBody(), responseType),
                        true
                );
            }

            throw new IdempotencyInProgressException(key);
        }

        try {
            T response = action.get();
            repository.complete(workspaceId, key, responseStatus, write(response));
            return new IdempotentResult<>(response, false);
        } catch (RuntimeException ex) {
            repository.release(workspaceId, key);
            throw ex;
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (key.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key exceeds 200 characters");
        }
    }

    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize idempotent response", ex);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return jsonMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize idempotent response", ex);
        }
    }
}
