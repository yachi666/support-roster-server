package com.support.server.supportrosterserver.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDto {
    private String id;
    private String teamId;
    private Long staffId;
    private String userName;
    private String userAvatar;
    private String code;
    private String meaning;
    private OffsetDateTime start;
    private OffsetDateTime end;
    private String timezone;
    private Boolean isPrimary;
    private Boolean showOnRoster;
    private String remark;
    private ContactDto contact;
    private BackupDto backup;
}
