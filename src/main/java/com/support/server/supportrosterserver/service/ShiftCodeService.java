package com.support.server.supportrosterserver.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.ShiftCodeDto;
import com.support.server.supportrosterserver.entity.ShiftCode;
import com.support.server.supportrosterserver.repository.RosterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShiftCodeService {

    private final RosterRepository rosterRepository;

    public List<ShiftCodeDto> getAllShiftCodes() {
        return rosterRepository.findAllShiftCodes().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    public ShiftCodeDto getShiftCodeByCode(String code) {
        ShiftCode shiftCode = rosterRepository.findShiftCodeByCode(code);
        if (shiftCode == null) {
            return null;
        }
        return convertToDto(shiftCode);
    }

    private ShiftCodeDto convertToDto(ShiftCode shiftCode) {
        return new ShiftCodeDto(
            shiftCode.getCode(),
            shiftCode.getMeaning(),
            shiftCode.getColorName(),
            shiftCode.getColorHex()
        );
    }
}
