package com.support.server.supportrosterserver.repository;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.fesod.sheet.FesodSheet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import com.support.server.supportrosterserver.entity.ColorRow;
import com.support.server.supportrosterserver.entity.RosterEntry;
import com.support.server.supportrosterserver.entity.RosterRow;
import com.support.server.supportrosterserver.entity.RoleGroup;
import com.support.server.supportrosterserver.entity.ShiftCode;
import com.support.server.supportrosterserver.entity.Staff;

import jakarta.annotation.PostConstruct;

@Repository
public class RosterRepository {
    private static final String ROSTER_FILE = "roster.xlsx";
    
    private List<RosterEntry> rosterEntries = new ArrayList<>();
    private Map<Long, Staff> staffMap = new HashMap<>();
    private Map<String, RoleGroup> roleGroupMap = new HashMap<>();
    private Map<String, ShiftCode> shiftCodeMap = new HashMap<>();
    private List<ShiftCode> shiftCodes = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadRosterData();
    }

    private void loadRosterData() {
        try {
            ClassPathResource resource = new ClassPathResource(ROSTER_FILE);
            String filePath = resource.getFile().getAbsolutePath();

            RosterDataListener rosterListener = new RosterDataListener();
            FesodSheet.read(filePath, RosterRow.class, rosterListener)
                .sheet()
                .doRead();

            List<RosterRow> rows = rosterListener.getDataList();
            
            for (RosterRow row : rows) {
                String roleGroup = row.getRoleGroup();
                
                if (roleGroup == null || roleGroup.isEmpty()) {
                    continue;
                }

                if ("code".equalsIgnoreCase(roleGroup)) {
                    break;
                }

                RosterEntry entry = new RosterEntry();
                entry.setRoleGroup(roleGroup);
                entry.setCode(row.getCode());
                entry.setMeaning(row.getMeaning());
                entry.setStartTime(parseTime(row.getStartTime()));
                entry.setEndTime(parseTime(row.getEndTime()));
                entry.setTimezone(row.getTimezone());
                entry.setStaffId(parseLong(row.getStaffId()));
                entry.setShowOnRosterPage("Y".equalsIgnoreCase(row.getShowOnRosterPage()));
                entry.setRemark(row.getRemark());

                rosterEntries.add(entry);

                if (!roleGroupMap.containsKey(roleGroup)) {
                    RoleGroup rg = parseRoleGroup(roleGroup);
                    roleGroupMap.put(roleGroup, rg);
                }

                Long staffId = entry.getStaffId();
                if (staffId != null && !staffMap.containsKey(staffId)) {
                    Staff staff = new Staff(staffId, "Staff " + staffId);
                    staffMap.put(staffId, staff);
                }
            }

            loadColorData(filePath);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load roster data", e);
        }
    }

    private void loadColorData(String filePath) {
        try {
            ColorDataListener colorListener = new ColorDataListener();
            FesodSheet.read(filePath, ColorRow.class, colorListener)
                .sheet()
                .doRead();

            List<ColorRow> colorRows = colorListener.getDataList();
            
            for (ColorRow row : colorRows) {
                String code = row.getCode();
                if (code != null && !code.isEmpty() && !"code".equalsIgnoreCase(code)) {
                    ShiftCode shiftCode = new ShiftCode(
                        code,
                        row.getMeaning(),
                        row.getColorName(),
                        row.getColorHex()
                    );
                    shiftCodes.add(shiftCode);
                    shiftCodeMap.put(code, shiftCode);
                }
            }
        } catch (Exception e) {
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            String[] parts = timeStr.split(":");
            return LocalTime.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                parts.length > 2 ? Integer.parseInt(parts[2]) : 0
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RoleGroup parseRoleGroup(String roleGroup) {
        String name = roleGroup.replace("_", " ");
        String category = "";
        String region = "";

        if (roleGroup.startsWith("L1_")) {
            category = "L1";
            region = roleGroup.substring(3);
        } else if (roleGroup.startsWith("AP_L2+")) {
            category = "L2+";
            region = "AP";
        } else if (roleGroup.startsWith("AP_L3")) {
            category = "L3";
            region = "AP";
        } else if (roleGroup.contains("_L2")) {
            category = "L2";
            region = roleGroup.replace("_L2", "");
        } else if (roleGroup.startsWith("Incident_Manager_")) {
            category = "Incident_Manager";
            region = roleGroup.substring("Incident_Manager_".length());
        } else if (roleGroup.startsWith("DevOps_")) {
            category = "DevOps";
            region = roleGroup.substring("DevOps_".length());
        }

        return new RoleGroup(roleGroup, name, category, region);
    }

    public List<RosterEntry> findAllRosterEntries() {
        return new ArrayList<>(rosterEntries);
    }

    public List<RosterEntry> findRosterEntriesByRoleGroup(String roleGroup) {
        return rosterEntries.stream()
            .filter(e -> e.getRoleGroup().equals(roleGroup))
            .toList();
    }

    public List<RosterEntry> findRosterEntriesByStaffId(Long staffId) {
        return rosterEntries.stream()
            .filter(e -> staffId.equals(e.getStaffId()))
            .toList();
    }

    public Staff findStaffById(Long id) {
        return staffMap.get(id);
    }

    public List<Staff> findAllStaff() {
        return new ArrayList<>(staffMap.values());
    }

    public RoleGroup findRoleGroupById(String id) {
        return roleGroupMap.get(id);
    }

    public List<RoleGroup> findAllRoleGroups() {
        return new ArrayList<>(roleGroupMap.values());
    }

    public ShiftCode findShiftCodeByCode(String code) {
        return shiftCodeMap.get(code);
    }

    public List<ShiftCode> findAllShiftCodes() {
        return new ArrayList<>(shiftCodes);
    }
}
