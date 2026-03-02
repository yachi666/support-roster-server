package com.support.server.supportrosterserver.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.StaffDto;
import com.support.server.supportrosterserver.entity.Staff;
import com.support.server.supportrosterserver.entity.StaffShift;
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
        
        List<StaffShift> shifts = rosterRepository.findAllStaffShifts().stream()
            .filter(s -> s.getStaffId().equals(id))
            .toList();
        
        List<String> roleGroups = shifts.stream()
            .map(StaffShift::getRoleGroup)
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
        dto.setRegion(staff.getRegion());
        dto.setContact(staff.getContact());
        return dto;
    }
}
