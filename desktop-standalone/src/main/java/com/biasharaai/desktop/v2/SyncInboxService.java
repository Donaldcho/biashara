package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SyncInboxService {
    record Operation(String id, String type, String sourceDevice, String payloadHash, SyncInboxEntry replay) {
    }

    private final ObjectMapper objectMapper;

    SyncInboxService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Operation inspect(List<SyncInboxEntry> entries, String body, String operationType, String fallbackDevice) {
        ParsedPayload payload = parse(body);
        String operationId = validateOperationId(payload.operationId());
        if (operationId.isBlank()) {
            return new Operation("", operationType, fallback(payload.sourceDevice(), fallbackDevice), "", null);
        }
        String payloadHash = payload.hash();
        SyncInboxEntry existing = entries.stream()
            .filter(entry -> operationId.equals(entry.operationId))
            .findFirst()
            .orElse(null);
        if (existing == null) {
            return new Operation(operationId, operationType, fallback(payload.sourceDevice(), fallbackDevice), payloadHash, null);
        }
        if (!operationType.equals(existing.operationType) || !payloadHash.equals(existing.payloadHash)) {
            throw new PhoneAuthenticationException(409, "Sync operation ID was already used with different data.");
        }
        return new Operation(operationId, operationType, fallback(payload.sourceDevice(), fallbackDevice), payloadHash, existing);
    }

    SyncInboxEntry outcome(
        Operation operation,
        int httpStatus,
        String responseJson
    ) {
        if (operation.id().isBlank()) {
            return null;
        }
        return new SyncInboxEntry(
            operation.id(),
            operation.type(),
            operation.sourceDevice(),
            Instant.now(),
            operation.payloadHash(),
            httpStatus,
            responseJson
        );
    }

    String operationId(String body) {
        return validateOperationId(parse(body).operationId());
    }

    private String validateOperationId(String raw) {
        String operationId = raw.trim();
        if (!operationId.isBlank() && (operationId.length() > 200 || !operationId.matches("[A-Za-z0-9._:-]+"))) {
            throw new PhoneAuthenticationException(400, "Invalid sync operation ID.");
        }
        return operationId;
    }

    private ParsedPayload parse(String body) {
        try {
            var payload = objectMapper.readTree(body);
            String operationId = payload.path("operationId").asText("");
            String sourceDevice = payload.path("deviceName").asText("");
            if (payload.isObject()) {
                ((ObjectNode) payload).remove("sessionKey");
            }
            JsonNode canonical = canonicalize(payload);
            return new ParsedPayload(operationId, sourceDevice, sha256(objectMapper.writeValueAsString(canonical)));
        } catch (Exception ignored) {
            return new ParsedPayload("", "", sha256(body.replaceAll("\"sessionKey\"\\s*:\\s*\"[^\"]*\"", "\"sessionKey\":\"\"")));
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            ObjectNode sorted = objectMapper.createObjectNode();
            for (String name : names) {
                sorted.set(name, canonicalize(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode values = objectMapper.createArrayNode();
            node.forEach(value -> values.add(canonicalize(value)));
            return values;
        }
        return node;
    }

    private String fallback(String value, String alternative) {
        return value == null || value.isBlank() ? (alternative == null ? "" : alternative) : value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                out.append(String.format("%02x", item & 0xff));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record ParsedPayload(String operationId, String sourceDevice, String hash) {
    }
}
