package com.support.server.supportrosterserver.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.StaffDto;
import com.support.server.supportrosterserver.service.workspace.WorkspaceStaffService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final WorkspaceStaffService workspaceStaffService;

    public List<StaffDto> getAllStaff() {
        return workspaceStaffService.listViewerStaff();
    }

    public StaffDto getStaffById(Long id) {
        return workspaceStaffService.getViewerStaff(id);
    }
}
