package com.acme.blueprint.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/** The spellings each database reports for the same constraint — see {@link ConstraintViolations}. */
class ConstraintViolationsTest {

    private static final String CONSTRAINT = "user_national_code_uk";

    @ParameterizedTest
    @ValueSource(strings = {
            "user_national_code_uk",                    // PostgreSQL
            "USERS.USER_NATIONAL_CODE_UK"})         // Oracle: schema-qualified, upper case
    void recognisesTheConstraintHoweverTheDatabaseNamesIt(String reportedName) {
        assertThat(ConstraintViolations.isViolationOf(wrapped(reportedName), CONSTRAINT)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_pkey", "USERS.SYS_C0012345", "user_national_code_uk_old"})
    void ignoresAnyOtherConstraint(String reportedName) {
        assertThat(ConstraintViolations.isViolationOf(wrapped(reportedName), CONSTRAINT)).isFalse();
    }

    @Test
    void ignoresAViolationThatReportsNoName() {
        assertThat(ConstraintViolations.isViolationOf(wrapped(null), CONSTRAINT)).isFalse();
    }

    @Test
    void ignoresAFailureThatIsNotAConstraintViolation() {
        assertThat(ConstraintViolations.isViolationOf(new IllegalStateException("boom"), CONSTRAINT)).isFalse();
    }

    private static DataIntegrityViolationException wrapped(String reportedName) {
        var violation = new ConstraintViolationException("violation", new SQLException("violation"), reportedName);
        return new DataIntegrityViolationException("violation", violation);
    }
}
