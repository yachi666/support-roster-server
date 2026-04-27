package com.support.server.supportrosterserver.service.contactinformation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationCreateRequest;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationDto;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationLinkDto;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationListResponse;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationStaffDto;
import com.support.server.supportrosterserver.dto.employee.EmployeeDirectoryLookupResponse;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactLinkEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactStaffEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactTagEntity;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactLinkMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactStaffMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactTagMapper;
import com.support.server.supportrosterserver.service.auth.AuthContextService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceStaffProfileSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactInformationService {

    private final SupportTeamContactMapper contactMapper;
    private final SupportTeamContactTagMapper tagMapper;
    private final SupportTeamContactStaffMapper staffBindingMapper;
    private final SupportTeamContactLinkMapper linkMapper;
    private final WorkspaceStaffProfileSupport workspaceStaffProfileSupport;
    private final AvatarUrlResolver avatarUrlResolver;
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
        String email = normalizeOptional(request.email());
        List<String> roles = normalizeDistinctOptionalValues(request.roles());
        List<String> staffIds = normalizeDistinctOptionalValues(request.staffIds());
        List<ContactInformationLinkDto> links = request.links() == null ? List.of() : request.links().stream()
            .filter(Objects::nonNull)
            .map(link -> new ContactInformationLinkDto(normalizeOptional(link.label()), normalizeOptional(link.url())))
            .filter(link -> link.label() != null && link.url() != null)
            .toList();

        if (email != null) {
            ensureEmailUnique(email);
        }
        Map<String, ContactInformationStaffDto> staffByCode = staffIds.isEmpty()
            ? Map.of()
            : resolveStaffProfiles(staffIds);

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
        SupportTeamContactEntity existing = contactMapper.selectOne(Wrappers.<SupportTeamContactEntity>query()
            .apply("LOWER(BTRIM(team_email)) = {0}", normalizeEmailKey(email))
            .last("limit 1"));
        if (existing != null) {
            throw new BadRequestException("Team email already exists.");
        }
    }

    private String normalizeEmailKey(String email) {
        return normalizeRequired(email, "Team email is required.").toLowerCase(Locale.ROOT);
    }

    private Map<String, ContactInformationStaffDto> resolveStaffProfiles(List<String> staffIds) {
        Map<String, ContactInformationStaffDto> staffByCode = new LinkedHashMap<>();
        for (String staffId : staffIds) {
            EmployeeDirectoryLookupResponse employee = workspaceStaffProfileSupport.lookupEmployeeSafely(staffId);
            staffByCode.put(staffId, toStaffDto(staffId, employee));
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
        LinkedHashSet<String> staffIds = bindings.stream()
            .map(SupportTeamContactStaffEntity::getStaffId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ContactInformationStaffDto> staffByCode = staffIds.isEmpty()
            ? Map.of()
            : resolveStaffProfiles(List.copyOf(staffIds));
        Map<Long, List<ContactInformationStaffDto>> staffByContactId = new LinkedHashMap<>();
        for (SupportTeamContactStaffEntity binding : bindings) {
            staffByContactId.computeIfAbsent(binding.getContactId(), ignored -> new ArrayList<>())
                .add(staffByCode.getOrDefault(binding.getStaffId(), toStaffDto(binding.getStaffId(), (EmployeeDirectoryLookupResponse) null)));
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
            binding.setStaffId(staffId);
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

    private List<ContactInformationStaffDto> toStaffDtos(List<String> staffIds, Map<String, ContactInformationStaffDto> staffByCode) {
        return staffIds.stream()
            .map(staffId -> staffByCode.getOrDefault(staffId, toStaffDto(staffId, (EmployeeDirectoryLookupResponse) null)))
            .toList();
    }

    private ContactInformationStaffDto toStaffDto(String staffId, EmployeeDirectoryLookupResponse employee) {
        return new ContactInformationStaffDto(
            staffId,
            workspaceStaffProfileSupport.resolveEmployeeName(staffId, employee),
            employee == null ? null : workspaceStaffProfileSupport.normalizeOptionalText(employee.emailAddress()),
            avatarUrlResolver.resolve(staffId)
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

    private List<String> normalizeDistinctOptionalValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(this::normalizeOptional)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
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
