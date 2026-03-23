package com.support.server.supportrosterserver.service.workspace;

import java.time.LocalTime;

import org.springframework.stereotype.Component;

import com.support.server.supportrosterserver.entity.workspace.ShiftDefinitionEntity;
import com.support.server.supportrosterserver.exception.BadRequestException;

@Component
public class WorkspaceShiftTimeSupport {

    private static final int MINUTES_PER_DAY = 24 * 60;

    public int requireValidDurationMinutes(Integer durationMinutes) {
        if (durationMinutes == null) {
            throw new BadRequestException("Shift duration is required.");
        }
        if (durationMinutes < 1 || durationMinutes > MINUTES_PER_DAY) {
            throw new BadRequestException("Shift duration must be between 1 and 1440 minutes.");
        }
        return durationMinutes;
    }

    public int resolveDurationMinutes(ShiftDefinitionEntity shiftDefinition) {
        if (shiftDefinition == null) {
            return 0;
        }
        if (shiftDefinition.getDurationMinutes() != null) {
            return shiftDefinition.getDurationMinutes();
        }
        return durationFromTimes(shiftDefinition.getStartTime(), shiftDefinition.getEndTime());
    }

    public int durationFromTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        int startMinutes = startTime.getHour() * 60 + startTime.getMinute();
        int endMinutes = endTime.getHour() * 60 + endTime.getMinute();
        int duration = (endMinutes - startMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        return duration == 0 ? MINUTES_PER_DAY : duration;
    }

    public LocalTime deriveEndTime(LocalTime startTime, Integer durationMinutes) {
        if (startTime == null || durationMinutes == null) {
            return null;
        }
        int safeDuration = requireValidDurationMinutes(durationMinutes);
        return startTime.plusMinutes(safeDuration % MINUTES_PER_DAY);
    }

    public boolean isOvernight(LocalTime startTime, Integer durationMinutes) {
        if (startTime == null || durationMinutes == null) {
            return false;
        }
        int safeDuration = requireValidDurationMinutes(durationMinutes);
        int startMinutes = startTime.getHour() * 60 + startTime.getMinute();
        return safeDuration == MINUTES_PER_DAY || startMinutes + safeDuration > MINUTES_PER_DAY;
    }
}
