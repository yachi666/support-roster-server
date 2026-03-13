package com.support.server.supportrosterserver.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.support.server.supportrosterserver.entity.workspace.OperationLogEntity;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}