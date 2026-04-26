package com.support.server.supportrosterserver.service.contactinformation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationCreateRequest;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationDto;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationLinkDto;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationListResponse;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationStaffDto;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactLinkEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactStaffEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactTagEntity;
import com.support.server.supportrosterserver.entity.workspace.StaffEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.StaffMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactLinkMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactStaffMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactTagMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactInformationService {

    private final SupportTeamContactMapper contactMapper;
    private final SupportTeamContactTagMapper tagMapper;
    private final SupportTeamContactStaffMapper staffBindingMapper;
    private final SupportTeamContactLinkMapper linkMapper;
    private final StaffMapper staffMapper;
    private final AuthContextService authContextService;

    public ContactInformationListResponse listContacts(String keyword, long page, long pageSize) {
        long normalizedPage = page <= 0 ? 1 : page;
        long normalizedPageSize = pageSize <= 0 ? 20 : pageSize;
        long offset = (normalizedPage - 1) * normalizedPageSize;
        String normalizedKeyword = normalizeOptional(keyword);
        List<SupportTeamContactEntity> contacts = contactMapper.searchContacts(normalizedKeyword, normalizedPageSize, offset);
        List<ContactInformationDto> items = aggregateContacts(contacts);
        return new ContactInformationListResponse(items, normalizedPage, normalizedPageSize, contactMapper.countContacts(normalizedKeyword));
    }

    @Transactional
    public ContactInformationDto createContact(ContactInformationCreateRequest request) {
        authContextService.requireAdmin();
        String name = normalizeRequired(request.name(), "Team name is required.");
        String email = normalizeRequired(request.email(), "Team email is required.");
        List<String> roles = normalizeDistinctValues(request.roles(), "At least one tag is required.");
        List<String> staffIds = normalizeDistinctValues(request.staffIds(), "At least one staff ID is required.");
        List<ContactInformationLinkDto> links = request.links() == null ? List.of() : request.links().stream()
            .filter(Objects::nonNull)
            .map(link -> new ContactInformationLinkDto(normalizeOptional(link.label()), normalizeOptional(link.url())))
            .filter(link -> link.label() != null && link.url() != null)
            .toList();

        ensureEmailUnique(email);
        Map<String, StaffEntity> staffByCode = ensureStaffIdsExist(staffIds);

        SupportTeamContactEntity entity = new SupportTeamContactEntity();
        entity.setTeamName(name);
        entity.setTeamEmail(email);
        entity.setXmatterGroup(normalizeOptional(request.xMatter()));
        entity.setGsdGroup(normalizeOptional(request.gsd()));
        entity.setEimId(normalizeOptional(request.eim()));
        entity.setOtherInfo(extractOtherInfo(links));
        contactMapper.insert(entity);

        insertTags(entity.getId(), roles);
        insertStaffBindings(entity.getId(), staffIds);
        List<ContactInformationLinkDto> persistedLinks = insertLinks(entity.getId(), links);

        return toDto(entity, roles, toStaffDtos(staffIds, staffByCode), mergeLinks(persistedLinks, entity.getOtherInfo()));
    }

    private void ensureEmailUnique(String email) {
        SupportTeamContactEntity existing = contactMapper.selectOne(Wrappers.<SupportTeamContactEntity>lambdaQuery()
            .eq(SupportTeamContactEntity::getTeamEmail, email)
            .last("limit 1"));
        if (existing != null) {
            throw new BadRequestException("Team email already exists.");
        }
    }

    private Map<String, StaffEntity> ensureStaffIdsExist(List<String> staffIds) {
        Map<String, StaffEntity> staffByCode = new LinkedHashMap<>();
        for (String staffId : staffIds) {
            StaffEntity entity = staffMapper.selectOne(Wrappers.<StaffEntity>lambdaQuery()
                .eq(StaffEntity::getStaffCode, staffId)
                .last("limit 1"));
            if (entity == null) {
                throw new BadRequestException("Unknown staff ID: " + staffId);
            }
            staffByCode.put(staffId, entity);
        }
        return staffByCode;
    }

    private List<ContactInformationDto> aggregateContacts(List<SupportTeamContactEntity> contacts) {
        if (contacts.isEmpty()) {
            return List.of();
        }
        List<Long> contactIds = contacts.stream()
            .map(SupportTeamContactEntity::getId)
            .filter(Objects::nonNull)
            .toList();

        Map<Long, List<String>> rolesByContactId = tagMapper.selectList(Wrappers.<SupportTeamContactTagEntity>lambdaQuery()
                .in(SupportTeamContactTagEntity::getContactId, contactIds)
                .orderByAsc(SupportTeamContactTagEntity::getSortOrder)
                .orderByAsc(SupportTeamContactTagEntity::getId))
            .stream()
            .collect(Collectors.groupingBy(
                SupportTeamContactTagEntity::getContactId,
                LinkedHashMap::new,
                Collectors.mapping(SupportTeamContactTagEntity::getTag, Collectors.toCollection(ArrayList::new))
            ));

        List<SupportTeamContactStaffEntity> bindings = staffBindingMapper.selectList(Wrappers.<SupportTeamContactStaffEntity>lambdaQuery()
            .in(SupportTeamContactStaffEntity::getContactId, contactIds)
            .orderByAsc(SupportTeamContactStaffEntity::getId));
        LinkedHashSet<String> staffCodes = bindings.stream()
            .map(SupportTeamContactStaffEntity::getStaffCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, StaffEntity> staffByCode = staffCodes.isEmpty()
            ? Map.of()
            : staffMapper.selectList(Wrappers.<StaffEntity>lambdaQuery()
                    .in(StaffEntity::getStaffCode, staffCodes))
                .stream()
                .collect(Collectors.toMap(StaffEntity::getStaffCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<ContactInformationStaffDto>> staffByContactId = new LinkedHashMap<>();
        for (SupportTeamContactStaffEntity binding : bindings) {
            StaffEntity staff = staffByCode.get(binding.getStaffCode());
            staffByContactId.computeIfAbsent(binding.getContactId(), ignored -> new ArrayList<>())
                .add(toStaffDto(binding.getStaffCode(), staff));
        }

        Map<Long, List<ContactInformationLinkDto>> linksByContactId = linkMapper.selectList(Wrappers.<SupportTeamContactLinkEntity>lambdaQuery()
                .in(SupportTeamContactLinkEntity::getContactId, contactIds)
                .orderByAsc(SupportTeamContactLinkEntity::getSortOrder)
                .orderByAsc(SupportTeamContactLinkEntity::getId))
            .stream()
            .collect(Collectors.groupingBy(
                SupportTeamContactLinkEntity::getContactId,
                LinkedHashMap::new,
                Collectors.mapping(entity -> new ContactInformationLinkDto(entity.getLabel(), entity.getUrl()), Collectors.toCollection(ArrayList::new))
            ));

        return contacts.stream()
            .map(entity -> toDto(
                entity,
                rolesByContactId.getOrDefault(entity.getId(), List.of()),
                staffByContactId.getOrDefault(entity.getId(), List.of()),
                mergeLinks(linksByContactId.getOrDefault(entity.getId(), List.of()), entity.getOtherInfo())
            ))
            .toList();
    }

    private void insertTags(Long contactId, List<String> roles) {
        for (int index = 0; index < roles.size(); index++) {
            SupportTeamContactTagEntity tag = new SupportTeamContactTagEntity();
            tag.setContactId(contactId);
            tag.setTag(roles.get(index));
            tag.setSortOrder(index);
            tagMapper.insert(tag);
        }
    }

    private void insertStaffBindings(Long contactId, List<String> staffIds) {
        for (String staffId : staffIds) {
            SupportTeamContactStaffEntity binding = new SupportTeamContactStaffEntity();
            binding.setContactId(contactId);
            binding.setStaffCode(staffId);
            staffBindingMapper.insert(binding);
        }
    }

    private List<ContactInformationLinkDto> insertLinks(Long contactId, List<ContactInformationLinkDto> links) {
        List<ContactInformationLinkDto> persistedLinks = new ArrayList<>();
        int sortOrder = 0;
        for (ContactInformationLinkDto link : links) {
            if (isOtherLink(link)) {
                continue;
            }
            SupportTeamContactLinkEntity entity = new SupportTeamContactLinkEntity();
            entity.setContactId(contactId);
            entity.setLabel(link.label());
            entity.setUrl(link.url());
            entity.setSortOrder(sortOrder++);
            linkMapper.insert(entity);
            persistedLinks.add(link);
        }
        return persistedLinks;
    }

    private List<ContactInformationStaffDto> toStaffDtos(List<String> staffIds, Map<String, StaffEntity> staffByCode) {
        return staffIds.stream()
            .map(staffId -> toStaffDto(staffId, staffByCode.get(staffId)))
            .toList();
    }

    private ContactInformationStaffDto toStaffDto(String staffCode, StaffEntity staff) {
        return new ContactInformationStaffDto(
            staffCode,
            staff == null ? null : staff.getName(),
            staff == null ? null : staff.getEmail(),
            staff == null ? null : staff.getAvatar()
        );
    }

    private List<ContactInformationLinkDto> mergeLinks(List<ContactInformationLinkDto> links, String otherInfo) {
        List<ContactInformationLinkDto> merged = new ArrayList<>(links);
        if (normalizeOptional(otherInfo) != null) {
            merged.add(new ContactInformationLinkDto("Other", normalizeOptional(otherInfo)));
        }
        return merged;
    }

    private String extractOtherInfo(List<ContactInformationLinkDto> links) {
        return links.stream()
            .filter(this::isOtherLink)
            .map(ContactInformationLinkDto::url)
            .map(this::normalizeOptional)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private boolean isOtherLink(ContactInformationLinkDto link) {
        return link.label() != null && "other".equalsIgnoreCase(link.label().trim());
    }

    private List<String> normalizeDistinctValues(List<String> values, String emptyMessage) {
        if (values == null) {
            throw new BadRequestException(emptyMessage);
        }
        List<String> normalized = values.stream()
            .filter(Objects::nonNull)
            .map(this::normalizeOptional)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            throw new BadRequestException(emptyMessage);
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ContactInformationDto toDto(
            SupportTeamContactEntity entity,
            List<String> roles,
            List<ContactInformationStaffDto> staff,
            List<ContactInformationLinkDto> links) {
        return new ContactInformationDto(
            entity.getId(),
            entity.getTeamName(),
            entity.getTeamEmail(),
            entity.getXmatterGroup(),
            entity.getGsdGroup(),
            entity.getEimId(),
            roles,
            staff,
            links
        );
    }
}
