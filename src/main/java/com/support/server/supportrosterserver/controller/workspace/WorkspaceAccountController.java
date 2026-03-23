package com.support.server.supportrosterserver.controller.workspace;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccountDto;
import com.support.server.supportrosterserver.dto.workspace.WorkspaceAccountUpsertRequest;
import com.support.server.supportrosterserver.service.workspace.WorkspaceAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspace/accounts")
@RequiredArgsConstructor
public class WorkspaceAccountController {

    private final WorkspaceAccountService workspaceAccountService;

    @GetMapping
    public ResponseEntity<List<WorkspaceAccountDto>> listAccounts(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(workspaceAccountService.listAccounts(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceAccountDto> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(workspaceAccountService.getAccount(id));
    }

    @PostMapping
    public ResponseEntity<WorkspaceAccountDto> createAccount(@Valid @RequestBody WorkspaceAccountUpsertRequest request) {
        return ResponseEntity.ok(workspaceAccountService.createAccount(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkspaceAccountDto> updateAccount(@PathVariable Long id, @Valid @RequestBody WorkspaceAccountUpsertRequest request) {
        return ResponseEntity.ok(workspaceAccountService.updateAccount(id, request));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        workspaceAccountService.resetPassword(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableAccount(@PathVariable Long id) {
        workspaceAccountService.enableAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disableAccount(@PathVariable Long id) {
        workspaceAccountService.disableAccount(id);
        return ResponseEntity.noContent().build();
    }
}
