package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.mapper.RoleGroupMapper;
import com.support.server.supportrosterserver.mapper.TeamMapper;
import com.support.server.supportrosterserver.mapper.TeamRoleGroupRelMapper;

class WorkspaceLookupServiceTest {

    private WorkspaceLookupService workspaceLookupService;

    @BeforeEach
    void setUp() {
        workspaceLookupService = new WorkspaceLookupService(
            mock(RoleGroupMapper.class),
            mock(TeamMapper.class),
            mock(TeamRoleGroupRelMapper.class)
        );
    }

    @Test
    void shouldDeriveIncidentManagerTeamProfile() {
        WorkspaceLookupService.TeamProfile profile = workspaceLookupService.deriveTeamProfile("Incident_Manager_China");

        assertNotNull(profile);
        assertEquals("incident-manager", profile.teamCode());
        assertEquals("Incident Manager", profile.name());
        assertEquals("orange", profile.color());
    }

    @Test
    void shouldInferTimezoneFromRegion() {
        assertEquals("Asia/Shanghai", workspaceLookupService.inferTimezone("China", "L1_China"));
        assertEquals("Asia/Kolkata", workspaceLookupService.inferTimezone("India", "Incident_Manager_India"));
        assertNull(workspaceLookupService.inferTimezone(null, null));
    }
}