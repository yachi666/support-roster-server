package com.support.server.supportrosterserver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationCreateRequest;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationDto;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationListResponse;
import com.support.server.supportrosterserver.service.contactinformation.ContactInformationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/contact-information")
@RequiredArgsConstructor
public class ContactInformationController {

    private final ContactInformationService contactInformationService;

    @GetMapping
    public ResponseEntity<ContactInformationListResponse> listContacts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(contactInformationService.listContacts(keyword, page, pageSize));
    }

    @PostMapping
    public ResponseEntity<ContactInformationDto> createContact(@Valid @RequestBody ContactInformationCreateRequest request) {
        return ResponseEntity.ok(contactInformationService.createContact(request));
    }
}
