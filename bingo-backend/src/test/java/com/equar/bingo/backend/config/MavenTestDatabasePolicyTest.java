package com.equar.bingo.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MavenTestDatabasePolicyTest {

    @Test
    void acceptsOnlyLocalDisposableMavenSchemaAndDedicatedAccount() {
        MavenTestDatabasePolicy.Target target = MavenTestDatabasePolicy.validate(
                "jdbc:mysql://127.0.0.1:3306/bingo_qa_maven_test?createDatabaseIfNotExist=false",
                MavenTestDatabasePolicy.USERNAME);

        assertEquals("127.0.0.1", target.host());
        assertEquals(3306, target.port());
        assertEquals("bingo_qa_maven_test", target.schema());
        assertEquals("bingo_qa_maven_runner", target.username());
    }

    @Test
    void rejectsPersistentQaAndAndroidQaSchemas() {
        assertThrows(IllegalStateException.class, () -> validateSchema("bingo_qa"));
        assertThrows(IllegalStateException.class, () -> validateSchema("bingo_qa_test"));
    }

    @Test
    void rejectsRemoteHostWrongPortAndWrongAccount() {
        assertThrows(IllegalStateException.class, () -> MavenTestDatabasePolicy.validate(
                "jdbc:mysql://db.example.test:3306/bingo_qa_maven_test", MavenTestDatabasePolicy.USERNAME));
        assertThrows(IllegalStateException.class, () -> MavenTestDatabasePolicy.validate(
                "jdbc:mysql://127.0.0.1:3307/bingo_qa_maven_test", MavenTestDatabasePolicy.USERNAME));
        assertThrows(IllegalStateException.class, () -> MavenTestDatabasePolicy.validate(
                "jdbc:mysql://127.0.0.1:3306/bingo_qa_maven_test", "bingo_qa_runner"));
    }

    @Test
    void refusesAutomaticDatabaseCreation() {
        assertThrows(IllegalStateException.class, () -> MavenTestDatabasePolicy.validate(
                "jdbc:mysql://localhost:3306/bingo_qa_maven_test?createDatabaseIfNotExist=true",
                MavenTestDatabasePolicy.USERNAME));
    }

    private void validateSchema(String schema) {
        MavenTestDatabasePolicy.validate("jdbc:mysql://localhost:3306/" + schema, MavenTestDatabasePolicy.USERNAME);
    }
}
