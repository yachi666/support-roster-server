package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.mapper.TeamMapper;

class WorkspaceLookupServiceTest {

    private WorkspaceLookupService workspaceLookupService;

    @BeforeEach
    void setUp() {
        workspaceLookupService = new WorkspaceLookupService(
            mock(TeamMapper.class)
        );
    }

    @Test
    void shouldInferTimezoneFromRegion() {
        assertEquals("HKT", workspaceLookupService.inferTimezone("China", "L1 China"));
        assertEquals("IST", workspaceLookupService.inferTimezone("India", "Incident Manager India"));
        assertEquals("UTC", workspaceLookupService.inferTimezone(null, "EMEA L2"));
        assertEquals("HKT", workspaceLookupService.inferTimezone(null, "AP L2"));
        assertNull(workspaceLookupService.inferTimezone(null, null));
    }

    @Test
    void shouldNormalizeWorkspaceTimezoneToSupportedValues() {
        assertEquals("UTC", workspaceLookupService.normalizeWorkspaceTimezone(null));
        assertEquals("UTC", workspaceLookupService.normalizeWorkspaceTimezone("GMT"));
        assertEquals("HKT", workspaceLookupService.normalizeWorkspaceTimezone("Asia/Shanghai"));
        assertEquals("IST", workspaceLookupService.normalizeWorkspaceTimezone("Asia/Kolkata"));
        assertEquals("IST", workspaceLookupService.normalizeWorkspaceTimezone("America/New_York"));
        assertEquals("UTC", workspaceLookupService.normalizeWorkspaceTimezone("Unknown/Zone"));
    }
}