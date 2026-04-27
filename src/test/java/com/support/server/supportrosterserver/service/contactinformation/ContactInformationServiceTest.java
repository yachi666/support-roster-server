package com.support.server.supportrosterserver.service.contactinformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationCreateRequest;
import com.support.server.supportrosterserver.dto.contactinformation.ContactInformationListResponse;
import com.support.server.supportrosterserver.dto.employee.EmployeeDirectoryLookupResponse;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactLinkEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactStaffEntity;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactTagEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;
import com.support.server.supportrosterserver.exception.ForbiddenException;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactLinkMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactStaffMapper;
import com.support.server.supportrosterserver.mapper.contactinformation.SupportTeamContactTagMapper;
import com.support.server.supportrosterserver.service.AvatarUrlResolver;
import com.support.server.supportrosterserver.service.EmployeeDirectoryClient;
import com.support.server.supportrosterserver.service.auth.AuthContextService;
import com.support.server.supportrosterserver.service.workspace.WorkspaceStaffProfileSupport;

class ContactInformationServiceTest {

    @Test
    void shouldReturnPagedAggregatedContactInformation() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        SupportTeamContactEntity entity = new SupportTeamContactEntity();
        entity.setId(1L);
        entity.setTeamName("Payments Core");
        entity.setTeamEmail("payments-core@company.com");
        entity.setXmatterGroup("XM-PAY-01");
        entity.setGsdGroup("GSD-PAY-882");
        entity.setEimId("EIM-9331");
        entity.setOtherInfo("https://example.com/wiki");

        SupportTeamContactTagEntity tag = new SupportTeamContactTagEntity();
        tag.setContactId(1L);
        tag.setTag("Upstream");

        SupportTeamContactStaffEntity binding = new SupportTeamContactStaffEntity();
        binding.setContactId(1L);
        binding.setStaffId("S-10492");

        SupportTeamContactLinkEntity link = new SupportTeamContactLinkEntity();
        link.setContactId(1L);
        link.setLabel("Docs");
        link.setUrl("https://example.com/docs");

        when(contactMapper.searchContacts("payments", 20, 0)).thenReturn(List.of(entity));
        when(contactMapper.countContacts("payments")).thenReturn(1L);
        when(tagMapper.selectList(any())).thenReturn(List.of(tag));
        when(staffBindingMapper.selectList(any())).thenReturn(List.of(binding));
        when(employeeDirectoryClient.getEmployee("S-10492")).thenReturn(
            new EmployeeDirectoryLookupResponse("xian", "China", "Alex Chen", "alex.c@company.com", "scheduler")
        );
        when(linkMapper.selectList(any())).thenReturn(List.of(link));

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
        );

        ContactInformationListResponse response = service.listContacts("payments", 1, 20);

        assertEquals(1L, response.total());
        assertEquals(1, response.items().size());
        assertEquals("Payments Core", response.items().get(0).name());
        assertEquals(List.of("Upstream"), response.items().get(0).roles());
        assertEquals("Alex Chen", response.items().get(0).staff().get(0).name());
        assertEquals(2, response.items().get(0).links().size());
        assertEquals("Other", response.items().get(0).links().get(1).label());
    }

    @Test
    void shouldCreateContactWithoutWorkspaceStaffRecordForStaffId() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        when(contactMapper.selectOne(any())).thenReturn(null);

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
        );

        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "payments-core@company.com",
            "XM-PAY-01",
            "GSD-PAY-882",
            "EIM-9331",
            List.of("Upstream"),
            List.of("S-404"),
            List.of()
        );

        var response = assertDoesNotThrow(() -> service.createContact(request));

        verify(contactMapper).insert(any(SupportTeamContactEntity.class));
        assertEquals("S-404", response.staff().get(0).id());
        assertEquals("https://avatar.example/S-40/S-404.jpg", response.staff().get(0).avatar());
    }

    @Test
    void shouldCreateContactWhenOnlyTeamNameProvided() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
        );

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

        var response = assertDoesNotThrow(() -> service.createContact(request));

        verify(contactMapper).insert(any(SupportTeamContactEntity.class));
        verify(contactMapper, never()).selectOne(any());
        verify(tagMapper, never()).insert(any(SupportTeamContactTagEntity.class));
        verify(staffBindingMapper, never()).insert(any(SupportTeamContactStaffEntity.class));
        verify(linkMapper, never()).insert(any(SupportTeamContactLinkEntity.class));
        assertEquals(List.of(), response.roles());
        assertEquals(List.of(), response.staff());
        assertEquals(List.of(), response.links());
    }

    @Test
    void shouldRequireAdminToCreateContactInformation() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
        );

        doThrow(new ForbiddenException("Admin permission is required.")).when(authContextService).requireAdmin();

        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "payments-core@company.com",
            null,
            null,
            null,
            List.of("Upstream"),
            List.of("S-10492"),
            List.of()
        );

        assertThrows(ForbiddenException.class, () -> service.createContact(request));
    }

    @Test
    void shouldRejectCreateWhenTeamEmailAlreadyExists() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        SupportTeamContactEntity existing = new SupportTeamContactEntity();
        existing.setId(8L);
        existing.setTeamEmail("payments-core@company.com");
        when(contactMapper.selectOne(any())).thenReturn(existing);

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
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

        assertThrows(BadRequestException.class, () -> service.createContact(request));
    }

    @Test
    void shouldCheckTeamEmailUniquenessUsingNormalizedEmailRule() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        when(contactMapper.selectOne(any())).thenReturn(null);
        when(employeeDirectoryClient.getEmployee("S-10492")).thenReturn(
            new EmployeeDirectoryLookupResponse("xian", "China", "Alex Chen", "alex.c@company.com", "scheduler")
        );

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
        );

        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "  PAYMENTS-CORE@COMPANY.COM  ",
            "XM-PAY-01",
            "GSD-PAY-882",
            "EIM-9331",
            List.of("Upstream"),
            List.of("S-10492"),
            List.of()
        );

        service.createContact(request);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> queryCaptor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(contactMapper).selectOne(queryCaptor.capture());
        AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("LOWER(BTRIM(team_email))"));
    }

    @Test
    void shouldCreateContactAndPersistChildRows() {
        SupportTeamContactMapper contactMapper = mock(SupportTeamContactMapper.class);
        SupportTeamContactTagMapper tagMapper = mock(SupportTeamContactTagMapper.class);
        SupportTeamContactStaffMapper staffBindingMapper = mock(SupportTeamContactStaffMapper.class);
        SupportTeamContactLinkMapper linkMapper = mock(SupportTeamContactLinkMapper.class);
        EmployeeDirectoryClient employeeDirectoryClient = mock(EmployeeDirectoryClient.class);
        AuthContextService authContextService = mock(AuthContextService.class);

        when(contactMapper.selectOne(any())).thenReturn(null);
        when(employeeDirectoryClient.getEmployee("S-10492")).thenReturn(
            new EmployeeDirectoryLookupResponse("xian", "China", "Alex Chen", "alex.c@company.com", "scheduler")
        );

        ContactInformationService service = new ContactInformationService(
            contactMapper,
            tagMapper,
            staffBindingMapper,
            linkMapper,
            new WorkspaceStaffProfileSupport(employeeDirectoryClient),
            new AvatarUrlResolver("https://avatar.example"),
            authContextService
        );

        ContactInformationCreateRequest request = new ContactInformationCreateRequest(
            "Payments Core",
            "payments-core@company.com",
            "XM-PAY-01",
            "GSD-PAY-882",
            "EIM-9331",
            List.of("Upstream"),
            List.of("S-10492"),
            List.of(new com.support.server.supportrosterserver.dto.contactinformation.ContactInformationLinkDto("Other", "https://example.com/wiki"))
        );

        var response = service.createContact(request);

        verify(contactMapper).insert(any(SupportTeamContactEntity.class));
        verify(tagMapper).insert(any(SupportTeamContactTagEntity.class));
        verify(staffBindingMapper).insert(any(SupportTeamContactStaffEntity.class));
        verify(linkMapper, never()).insert(any(SupportTeamContactLinkEntity.class));
        assertEquals("Payments Core", response.name());
        assertEquals("Other", response.links().get(0).label());
        assertEquals("https://example.com/wiki", response.links().get(0).url());
        assertEquals("Alex Chen", response.staff().get(0).name());
        assertEquals("alex.c@company.com", response.staff().get(0).email());
        assertEquals("https://avatar.example/S-10/S-10492.jpg", response.staff().get(0).avatar());
    }
}
