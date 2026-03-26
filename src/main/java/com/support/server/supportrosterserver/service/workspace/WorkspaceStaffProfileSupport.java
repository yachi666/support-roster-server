package com.support.server.supportrosterserver.service.workspace;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.dto.employee.EmployeeDirectoryLookupResponse;
import com.support.server.supportrosterserver.service.EmployeeDirectoryClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceStaffProfileSupport {

    private static final Logger log = LogManager.getLogger(WorkspaceStaffProfileSupport.class);

    private final EmployeeDirectoryClient employeeDirectoryClient;

    public EmployeeDirectoryLookupResponse lookupEmployeeSafely(String staffCode) {
        try {
            return employeeDirectoryClient.getEmployee(staffCode);
        } catch (RuntimeException ex) {
            log.warn("Employee lookup failed for staff ID {}. Falling back to minimal staff profile.", staffCode, ex);
            return null;
        }
    }

    public String resolveEmployeeName(String staffCode, EmployeeDirectoryLookupResponse employee) {
        if (employee == null) {
            return staffCode;
        }
        String displayName = normalizeOptionalText(employee.displayName());
        return displayName == null ? staffCode : displayName;
    }

    public String resolvePreferredText(String preferredValue, String fallbackValue) {
        String normalizedPreferredValue = normalizeOptionalText(preferredValue);
        if (normalizedPreferredValue != null) {
            return normalizedPreferredValue;
        }
        return normalizeOptionalText(fallbackValue);
    }

    public String buildRegion(EmployeeDirectoryLookupResponse employee) {
        if (employee == null) {
            return null;
        }
        return buildRegion(employee.city(), employee.country());
    }

    public String buildRegion(String city, String country) {
        String normalizedCity = normalizeOptionalText(city);
        String normalizedCountry = normalizeOptionalText(country);
        if (normalizedCity == null) {
            return normalizedCountry;
        }
        if (normalizedCountry == null) {
            return normalizedCity;
        }
        return normalizedCity + ", " + normalizedCountry;
    }

    public String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
