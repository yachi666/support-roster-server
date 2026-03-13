package com.support.server.supportrosterserver.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.ShiftCodeDto;
import com.support.server.supportrosterserver.service.workspace.WorkspaceShiftDefinitionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/shift-codes")
@RequiredArgsConstructor
public class ShiftCodeController {

    private final WorkspaceShiftDefinitionService workspaceShiftDefinitionService;

    @GetMapping
    public ResponseEntity<List<ShiftCodeDto>> getAllShiftCodes() {
        return ResponseEntity.ok(workspaceShiftDefinitionService.listViewerShiftCodes());
    }
}