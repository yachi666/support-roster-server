package com.support.server.supportrosterserver.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.StaffDto;
import com.support.server.supportrosterserver.entity.RosterEntry;
import com.support.server.supportrosterserver.entity.Staff;
import com.support.server.supportrosterserver.repository.RosterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final RosterRepository rosterRepository;

    public List<StaffDto> getAllStaff() {
        return rosterRepository.findAllStaff().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    public StaffDto getStaffById(Long id) {
        Staff staff = rosterRepository.findStaffById(id);
        if (staff == null) {
            return null;
        }
        
        StaffDto dto = convertToDto(staff);
        
        List<RosterEntry> entries = rosterRepository.findRosterEntriesByStaffId(id);
        List<String> roleGroups = entries.stream()
            .map(RosterEntry::getRoleGroup)
            .distinct()
            .collect(Collectors.toList());
        dto.setRoleGroups(roleGroups);
        
        return dto;
    }

    private StaffDto convertToDto(Staff staff) {
        StaffDto dto = new StaffDto();
        dto.setId(staff.getId());
        dto.setName(staff.getName());
        dto.setAvatar(staff.getAvatar());
        dto.setEmail(staff.getEmail());
        dto.setPhone(staff.getPhone());
        dto.setSlack(staff.getSlack());
        return dto;
    }
}
