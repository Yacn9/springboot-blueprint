package com.acme.blueprint.users.domain;

import java.util.regex.Pattern;

/**
 * Iranian national identification code (کد ملی): ten digits, the last being a
 * mod-11 check digit. Constructing one validates it, so an invalid code cannot
 * exist inside the domain.
 */
public record NationalCode(String value) {

    /** The format alone; the checksum is verified on construction. */
    public static final String FORMAT = "\\d{10}";

    private static final Pattern DIGITS = Pattern.compile(FORMAT);
    private static final int LENGTH = 10;

    public NationalCode {
        if (!isValid(value)) {
            throw new InvalidNationalCodeException(value);
        }
    }

    public static NationalCode of(String value) {
        return new NationalCode(value);
    }

    private static boolean isValid(String value) {
        if (value == null || !DIGITS.matcher(value).matches()) {
            return false;
        }
        if (value.chars().distinct().count() == 1) {
            return false; // 0000000000, 1111111111, … pass the checksum but are never issued
        }
        int sum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            sum += Character.digit(value.charAt(i), 10) * (LENGTH - i);
        }
        int remainder = sum % 11;
        int checkDigit = Character.digit(value.charAt(LENGTH - 1), 10);
        return remainder < 2 ? checkDigit == remainder : checkDigit == 11 - remainder;
    }
}
