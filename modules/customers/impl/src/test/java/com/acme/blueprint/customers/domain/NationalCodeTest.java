package com.acme.blueprint.customers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NationalCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"0012345679", "1234567891"})
    void acceptsCodesWithAValidCheckDigit(String value) {
        assertThat(NationalCode.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0012345678", "123456789", "12345678901", "12345abcde", "1111111111"})
    void rejectsMalformedCodes(String value) {
        assertThatThrownBy(() -> NationalCode.of(value))
                .isInstanceOf(InvalidNationalCodeException.class)
                .extracting("code").isEqualTo("customers.invalid-national-code");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> NationalCode.of(null)).isInstanceOf(InvalidNationalCodeException.class);
    }
}
