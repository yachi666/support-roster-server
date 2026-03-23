package com.support.server.supportrosterserver.controller.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceActivityDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceOverviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceQuickActionDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceSummaryStatDto;
import com.support.server.supportrosterserver.service.workspace.WorkspaceOverviewService;

class WorkspaceOverviewControllerTest {

    @Test
    void shouldReturnOverviewPayload() {
        WorkspaceOverviewService service = mock(WorkspaceOverviewService.class);
        WorkspaceOverviewResponse payload = new WorkspaceOverviewResponse(
            List.of(new WorkspaceSummaryStatDto("Completion Progress", "84%", "+2%", "good", 84)),
            List.of(new WorkspaceActivityDto("System", "refreshed roster", "1 mins ago")),
            List.of(new WorkspaceQuickActionDto("Export Final Roster", "Download validated schedule", "teal", "export"))
        );
        when(service.getOverview(2026, 3)).thenReturn(payload);

        WorkspaceOverviewController controller = new WorkspaceOverviewController(service);

        var response = controller.getOverview(2026, 3);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("84%", response.getBody().getStats().get(0).getValue());
    }
}
