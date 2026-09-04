package com.biasharaai.desktop.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyncInboxServiceTest {
    private final SyncInboxService service = new SyncInboxService(new ObjectMapper());

    @Test
    void replaysSameOperationAfterSessionKeyChanges() {
        var entries = new ArrayList<SyncInboxEntry>();
        String first = "{\"sessionKey\":\"old\",\"operationId\":\"mobile-transaction:phone:42\",\"totalCents\":500}";
        String retry = "{\"totalCents\":500,\"operationId\":\"mobile-transaction:phone:42\",\"sessionKey\":\"new\"}";
        SyncInboxService.Operation firstOperation = service.inspect(entries, first, "TRANSACTION_SYNC", "Phone");
        assertNull(firstOperation.replay());
        entries.add(service.outcome(firstOperation, 200, "{\"accepted\":true}"));

        SyncInboxEntry replay = service.inspect(entries, retry, "TRANSACTION_SYNC", "Phone").replay();

        assertNotNull(replay);
        assertEquals("{\"accepted\":true}", replay.responseJson);
    }

    @Test
    void rejectsOperationIdReusedWithDifferentBusinessPayload() {
        var entries = new ArrayList<SyncInboxEntry>();
        String first = "{\"operationId\":\"stock:42\",\"quantity\":5}";
        entries.add(service.outcome(service.inspect(entries, first, "STOCK_INTAKE", "Phone"), 200, "{}"));

        PhoneAuthenticationException error = assertThrows(
            PhoneAuthenticationException.class,
            () -> service.inspect(entries, "{\"operationId\":\"stock:42\",\"quantity\":7}", "STOCK_INTAKE", "Phone")
        );

        assertEquals(409, error.status());
    }

    @Test
    void rejectsUnsafeOrUnboundedOperationIds() {
        String body = "{\"operationId\":\"not valid/with spaces\"}";
        PhoneAuthenticationException error = assertThrows(
            PhoneAuthenticationException.class,
            () -> service.operationId(body)
        );
        assertEquals(400, error.status());
    }

    @Test
    void canonicalizesNestedObjectKeysButPreservesArrayOrder() {
        var entries = new ArrayList<SyncInboxEntry>();
        String first = "{\"operationId\":\"reconcile:1\",\"stock\":[{\"id\":1,\"value\":7}]}";
        SyncInboxService.Operation operation = service.inspect(entries, first, "RECONCILE", "Phone");
        entries.add(service.outcome(operation, 200, "{}"));

        assertNotNull(service.inspect(
            entries,
            "{\"stock\":[{\"value\":7,\"id\":1}],\"operationId\":\"reconcile:1\"}",
            "RECONCILE",
            "Phone"
        ).replay());
        assertThrows(
            PhoneAuthenticationException.class,
            () -> service.inspect(
                entries,
                "{\"operationId\":\"reconcile:1\",\"stock\":[{\"value\":8,\"id\":1}]}",
                "RECONCILE",
                "Phone"
            )
        );
    }
}
