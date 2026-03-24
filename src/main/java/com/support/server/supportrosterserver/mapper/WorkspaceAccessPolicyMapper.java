package com.support.server.supportrosterserver.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.support.server.supportrosterserver.entity.workspace.WorkspaceAccessPolicyEntity;

@Mapper
public interface WorkspaceAccessPolicyMapper extends BaseMapper<WorkspaceAccessPolicyEntity> {
}
