package com.support.server.supportrosterserver.service.workspace;

import org.springframework.stereotype.Service;

import com.support.server.supportrosterserver.entity.workspace.OperationLogEntity;
import com.support.server.supportrosterserver.mapper.OperationLogMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkspaceOperationLogService {

    private final OperationLogMapper operationLogMapper;

    public void log(String actor, String action, String targetType, Long targetId, String details) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setActor(actor == null || actor.isBlank() ? "system" : actor);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId == null ? null : String.valueOf(targetId));
        entity.setDetails(details);
        operationLogMapper.insert(entity);
    }
}
