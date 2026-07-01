package com.support.server.supportrosterserver.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.support.server.supportrosterserver.entity.auth.WorkspaceAccountEntity;

@Mapper
public interface WorkspaceAccountMapper extends BaseMapper<WorkspaceAccountEntity> {

    /**
     * Query account by staffId, including soft-deleted rows.
     * Bypasses {@code @TableLogic} filter to detect deactivated accounts.
     * Orders by deleted ASC so active/pending rows (deleted=0) come first
     * when both a deleted and non-deleted account exist for the same staffId.
     */
    @Select("SELECT * FROM workspace_account WHERE staff_id = #{staffId} ORDER BY deleted ASC LIMIT 1")
    WorkspaceAccountEntity selectAnyByStaffId(@Param("staffId") String staffId);
}
