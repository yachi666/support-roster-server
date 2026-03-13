package com.support.server.supportrosterserver.repository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.fesod.sheet.FesodSheet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import com.support.server.supportrosterserver.entity.RoleGroup;
import com.support.server.supportrosterserver.entity.ShiftDefinition;
import com.support.server.supportrosterserver.entity.ShiftDefinitionRow;
import com.support.server.supportrosterserver.entity.Staff;
import com.support.server.supportrosterserver.entity.StaffShift;
import com.support.server.supportrosterserver.entity.StaffShiftRow;

import jakarta.annotation.PostConstruct;

@Repository
public class RosterRepository {
    private static final String ROSTER_FILE = "roster.xlsx";
    private static final int SHEET_INDEX_SHIFT_DEFINITIONS = 0;
    private static final int SHEET_INDEX_STAFF_SHIFTS = 1;
    
    private Map<String, ShiftDefinition> shiftDefinitionMap = new HashMap<>();
    private List<StaffShift> staffShifts = new ArrayList<>();
    private Map<Long, Staff> staffMap = new HashMap<>();
    private Map<String, RoleGroup> roleGroupMap = new HashMap<>();

    @PostConstruct
    public void init() {
        loadRosterData();
    }

    private void loadRosterData() {
        try {
            ClassPathResource resource = new ClassPathResource(ROSTER_FILE);
            byte[] rosterBytes = resource.getContentAsByteArray();

            loadShiftDefinitions(rosterBytes);
            loadStaffShiftData(rosterBytes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load roster data", e);
        }
    }

    private void loadShiftDefinitions(byte[] rosterBytes) {
        try(ByteArrayInputStream inputStream = new ByteArrayInputStream(rosterBytes)) {
            ShiftDefinitionDataListener listener = new ShiftDefinitionDataListener();
            FesodSheet.read(inputStream, ShiftDefinitionRow.class, listener)
                .sheet(SHEET_INDEX_SHIFT_DEFINITIONS)
                .doRead();

            List<ShiftDefinitionRow> rows = listener.getDataList();
            
            for (ShiftDefinitionRow row : rows) {
                String roleGroup = row.getRoleGroup();
                
                if (roleGroup == null || roleGroup.isEmpty() || "role_group".equalsIgnoreCase(roleGroup)) {
                    continue;
                }

                String code = row.getCode();
                if (code == null || code.isEmpty()) {
                    continue;
                }

                String key = roleGroup + "|" + code;
                ShiftDefinition def = new ShiftDefinition();
                def.setRoleGroup(roleGroup);
                def.setCode(code);
                def.setMeaning(row.getMeaning());
                def.setStartTime(row.getStartTime());
                def.setEndTime(row.getEndTime());
                def.setTimezone(row.getTimezone());
                def.setShowOnRosterPage("Y".equalsIgnoreCase(row.getShowOnRosterPage()));
                def.setRemark(row.getRemark());

                shiftDefinitionMap.put(key, def);

                if (!roleGroupMap.containsKey(roleGroup)) {
                    RoleGroup rg = parseRoleGroup(roleGroup);
                    roleGroupMap.put(roleGroup, rg);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load shift definitions", e);
        }
    }

    private void loadStaffShiftData(byte[] rosterBytes) {
        try(InputStream inputStream = new ByteArrayInputStream(rosterBytes)) {
            StaffShiftDataListener staffShiftListener = new StaffShiftDataListener();
            FesodSheet.read(inputStream, StaffShiftRow.class, staffShiftListener)
                .sheet(SHEET_INDEX_STAFF_SHIFTS)
                .doRead();

            List<StaffShiftRow> rows = staffShiftListener.getDataList();
            
            for (StaffShiftRow row : rows) {
                String name = row.getName();
                
                if (name == null || name.isEmpty() || "name".equalsIgnoreCase(name)) {
                    continue;
                }

                Long staffId = parseLong(row.getStaffId());
                if (staffId == null) {
                    continue;
                }

                StaffShift staffShift = new StaffShift();
                staffShift.setStaffId(staffId);
                staffShift.setName(row.getName());
                staffShift.setRoleGroup(row.getRoleGroup());
                staffShift.setRegion(row.getRegion());
                staffShift.setContact(row.getContact());
                staffShift.setNotes(row.getNotes());

                for (int day = 1; day <= 31; day++) {
                    String shiftCode = row.getShiftCodeByDay(day);
                    staffShift.setShiftCodeByDay(day, shiftCode);
                }

                staffShifts.add(staffShift);

                if (!staffMap.containsKey(staffId)) {
                    Staff staff = new Staff(staffId, row.getName());
                    staff.setRegion(row.getRegion());
                    staff.setContact(row.getContact());
                    staffMap.put(staffId, staff);
                }

                String roleGroup = row.getRoleGroup();
                if (roleGroup != null && !roleGroup.isEmpty() && !roleGroupMap.containsKey(roleGroup)) {
                    RoleGroup rg = parseRoleGroup(roleGroup);
                    roleGroupMap.put(roleGroup, rg);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load staff shift data", e);
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

    public ShiftDefinition findShiftDefinition(String roleGroup, String code) {
        String key = roleGroup + "|" + code;
        return shiftDefinitionMap.get(key);
    }

    public List<StaffShift> findAllStaffShifts() {
        return new ArrayList<>(staffShifts);
    }

    public List<StaffShift> findStaffShiftsByRoleGroup(String roleGroup) {
        return staffShifts.stream()
            .filter(s -> s.getRoleGroup().equals(roleGroup))
            .toList();
    }

    public StaffShift findStaffShiftByStaffId(Long staffId) {
        return staffShifts.stream()
            .filter(s -> s.getStaffId().equals(staffId))
            .findFirst()
            .orElse(null);
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
}
