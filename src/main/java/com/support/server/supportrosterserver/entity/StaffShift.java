package com.support.server.supportrosterserver.entity;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StaffShift {
    private Long staffId;
    private String name;
    private String roleGroup;
    private String region;
    private String contact;
    private String notes;
    private Map<Integer, String> dailyShifts = new HashMap<>();

    public String getShiftCodeByDay(int day) {
        return dailyShifts.get(day);
    }

    public void setShiftCodeByDay(int day, String code) {
        if (code != null && !code.isEmpty()) {
            dailyShifts.put(day, code);
        }
    }
}
