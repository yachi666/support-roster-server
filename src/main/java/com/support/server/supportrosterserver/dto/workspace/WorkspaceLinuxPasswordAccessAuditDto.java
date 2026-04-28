package com.support.server.supportrosterserver.dto.workspace;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceLinuxPasswordAccessAuditDto {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long staffRecordId;
    private String staffId;
    private String staffName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serverId;
    private String hostname;
    private String ip;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long credentialId;
    private String username;
    private String action;
    private String result;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createTime;
}
