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
        assertEquals("Asia/Shanghai", workspaceLookupService.inferTimezone("China", "L1 China"));
        assertEquals("Asia/Kolkata", workspaceLookupService.inferTimezone("India", "Incident Manager India"));
        assertEquals("Europe/London", workspaceLookupService.inferTimezone(null, "EMEA L2"));
        assertEquals("Asia/Shanghai", workspaceLookupService.inferTimezone(null, "AP L2"));
        assertNull(workspaceLookupService.inferTimezone(null, null));
    }
}