package com.irs.mef.filing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientReturnTypeTest {

    /** A form with no schema root is only warned about by the validator, even in ENFORCE, so it would send unchecked. */
    @Test
    void everyReturnTypeNamesASchemaRoot() {
        List<ClientReturnType> unvalidated = Arrays.stream(ClientReturnType.values())
                .filter(type -> type.formType().schemaRoot().isEmpty())
                .toList();

        assertEquals(List.of(), unvalidated);
    }

    @Test
    void returnVersionComesFromTheFormsSchemaRelease() {
        assertEquals("2026v2.0", ClientReturnType.F940.returnVersion());
        assertEquals("2026Q1v4.0", ClientReturnType.F941.returnVersion());
        assertEquals("2026Q1v4.0", ClientReturnType.F941X.returnVersion());
    }
}
