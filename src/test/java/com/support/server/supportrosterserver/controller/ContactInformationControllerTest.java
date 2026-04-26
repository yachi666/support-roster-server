package com.support.server.supportrosterserver.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationCreateRequest;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationDto;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationListResponse;
import com.support.server.supportrosterserver.service.contactinformation.ContactInformationService;

class ContactInformationControllerTest {

    @Test
    void shouldReturnPublicPagedContactInformation() {
        ContactInformationService service = mock(ContactInformationService.class);
        when(service.listContacts("payments", 1, 20)).thenReturn(
            new ContactInformationListResponse(List.of(), 1, 20, 0)
        );

        ContactInformationController controller = new ContactInformationController(service);

        var response = controller.listContacts("payments", 1, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().total());
    }

    @Test
    void shouldDelegateCreateRequest() {
        ContactInformationService service = mock(ContactInformationService.class);
        ContactInformationDto created = new ContactInformationDto(
            1L,
            "Payments Core",
            "payments-core@company.com",
            "XM-PAY-01",
            "GSD-PAY-882",
            "EIM-9331",
            List.of("Upstream"),
            List.of(),
            List.of()
        );
        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "payments-core@company.com",
            "XM-PAY-01",
            "GSD-PAY-882",
            "EIM-9331",
            List.of("Upstream"),
            List.of("S-10492"),
            List.of()
        );
        when(service.createContact(request)).thenReturn(created);

        ContactInformationController controller = new ContactInformationController(service);

        var response = controller.createContact(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1L, response.getBody().id());
    }
}
