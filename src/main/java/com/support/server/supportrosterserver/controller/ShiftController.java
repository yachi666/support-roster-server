package com.support.server.supportrosterserver.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.support.server.supportrosterserver.dto.ShiftDto;
import com.support.server.supportrosterserver.service.RosterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final RosterService rosterService;

    @GetMapping
    public ResponseEntity<List<ShiftDto>> getShiftsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        return ResponseEntity.ok(rosterService.getShiftsByDate(date, teamId, timezone));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftDto> getShiftById(@PathVariable String id) {
        ShiftDto shift = rosterService.getShiftById(id);
        if (shift == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(shift);
    }
}
