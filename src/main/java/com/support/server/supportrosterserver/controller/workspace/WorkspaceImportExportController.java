package com.support.server.supportrosterserver.controller.workspace;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewResponse;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportPreviewSaveRequest;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceImportSaveResponse;
import com.support.server.supportrosterserver.service.workspace.WorkspaceImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/import-export")
@RequiredArgsConstructor
public class WorkspaceImportExportController {

    private final WorkspaceImportService workspaceImportService;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public ResponseEntity<WorkspaceImportPreviewResponse> previewImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false) String operator) {
        return ResponseEntity.ok(workspaceImportService.previewImport(file, year, month, operator));
    }

    @PostMapping("/save")
    public ResponseEntity<WorkspaceImportSaveResponse> savePreview(@Valid @RequestBody WorkspaceImportPreviewSaveRequest request) {
        return ResponseEntity.ok(workspaceImportService.savePreview(request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRoster(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        return workspaceImportService.exportRoster(year, month);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        return workspaceImportService.downloadTemplate();
    }
}
