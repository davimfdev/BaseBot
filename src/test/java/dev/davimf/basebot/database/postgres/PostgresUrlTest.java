// [OUTLINE START]
// Package: dev.davimf.basebot.database.postgres
// 
// Class: PostgresUrlTest
// [OUTLINE END]



package dev.davimf.basebot.database.postgres;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgresUrlTest {

    @Test
    void neonConnectionStringSplitsIntoJdbcUrlAndCredentials() {
        PostgresUrl.Parsed p = PostgresUrl.normalize(
                "postgresql://neon:secret@ep-x.us-east-2.aws.neon.tech/basebot?sslmode=require", "", "");
        assertEquals("jdbc:postgresql://ep-x.us-east-2.aws.neon.tech/basebot?sslmode=require", p.jdbcUrl());
        assertEquals("neon", p.user());
        assertEquals("secret", p.password());
    }

    @Test
    void postgresSchemeWithExplicitPortIsPreserved() {
        PostgresUrl.Parsed p = PostgresUrl.normalize("postgres://u:p@host:6543/db", null, null);
        assertEquals("jdbc:postgresql://host:6543/db", p.jdbcUrl());
        assertEquals("u", p.user());
        assertEquals("p", p.password());
    }

    @Test
    void explicitCredentialsOverrideEmbeddedOnes() {
        PostgresUrl.Parsed p = PostgresUrl.normalize(
                "postgresql://embedded:embpw@host/db", "override", "ovpw");
        assertEquals("override", p.user());
        assertEquals("ovpw", p.password());
    }

    @Test
    void jdbcUrlIsPassedThroughWithSeparateCredentials() {
        PostgresUrl.Parsed p = PostgresUrl.normalize("jdbc:postgresql://h:5432/db", "u", "p");
        assertEquals("jdbc:postgresql://h:5432/db", p.jdbcUrl());
        assertEquals("u", p.user());
        assertEquals("p", p.password());
    }

    @Test
    void blankOverridesBecomeNullWhenNoEmbeddedCredentials() {
        PostgresUrl.Parsed p = PostgresUrl.normalize("postgresql://host/db?sslmode=require", "", "");
        assertEquals("jdbc:postgresql://host/db?sslmode=require", p.jdbcUrl());
        assertNull(p.user());
        assertNull(p.password());
    }
}
