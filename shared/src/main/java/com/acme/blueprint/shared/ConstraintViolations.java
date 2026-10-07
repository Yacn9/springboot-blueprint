package com.acme.blueprint.shared;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Recognises which database constraint a failed statement violated, so a
 * persistence adapter can turn it into a business exception.
 *
 * <p>This is the one place that knows how databases spell a constraint's name:
 * PostgreSQL reports {@code customer_national_code_uk}, Oracle
 * {@code CUSTOMERS.CUSTOMER_NATIONAL_CODE_UK}. Names are compared without any
 * schema qualifier and ignoring case, so adapters pass the name exactly as the
 * migration declares it.
 *
 * <p>Needs Hibernate at runtime, which every {@code -impl} project has through
 * Spring Data JPA; it is only a compile-time dependency of {@code :shared}.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** True if {@code failure} or any of its causes is a violation of the named constraint. */
    public static boolean isViolationOf(Throwable failure, String constraintName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(withoutSchema(violation.getConstraintName()))) {
                return true;
            }
        }
        return false;
    }

    private static String withoutSchema(String reportedName) {
        return reportedName == null ? null : reportedName.substring(reportedName.lastIndexOf('.') + 1);
    }
}
