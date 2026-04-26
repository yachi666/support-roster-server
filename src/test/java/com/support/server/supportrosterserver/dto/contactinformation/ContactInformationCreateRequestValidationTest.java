package com.support.server.supportrosterserver.dto.contactinformation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ContactInformationCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAllowCreateRequestWithOnlyTeamName() {
        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of()
        );

        Set<String> propertyPaths = validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of(), propertyPaths);
    }

    @Test
    void shouldAllowBlankEmail() {
        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "   ",
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of()
        );

        Set<String> propertyPaths = validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of(), propertyPaths);
    }

    @Test
    void shouldRejectInvalidNonEmptyEmail() {
        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "not-an-email",
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of()
        );

        Set<String> propertyPaths = validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of("email"), propertyPaths);
    }
}
