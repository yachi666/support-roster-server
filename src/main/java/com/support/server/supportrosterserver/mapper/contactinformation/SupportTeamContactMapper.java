package com.support.server.supportrosterserver.mapper.contactinformation;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.support.server.supportrosterserver.entity.contactinformation.SupportTeamContactEntity;

@Mapper
public interface SupportTeamContactMapper extends BaseMapper<SupportTeamContactEntity> {

    @Select({
        "<script>",
        "SELECT *",
        "FROM support_team_contact contact",
        "WHERE contact.deleted = 0",
        "<if test='keyword != null and keyword != \"\"'>",
        "  AND (",
        "    LOWER(BTRIM(contact.team_name)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(contact.team_email)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.xmatter_group, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.gsd_group, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.eim_id, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.other_info, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR EXISTS (",
        "      SELECT 1",
        "      FROM support_team_contact_tag tag",
        "      WHERE tag.contact_id = contact.id",
        "        AND tag.deleted = 0",
        "        AND LOWER(BTRIM(tag.tag)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    )",
        "    OR EXISTS (",
        "      SELECT 1",
        "      FROM support_team_contact_staff binding",
        "      WHERE binding.contact_id = contact.id",
        "        AND binding.deleted = 0",
        "        AND LOWER(BTRIM(binding.staff_id)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    )",
        "    OR EXISTS (",
        "      SELECT 1",
        "      FROM support_team_contact_link link",
        "      WHERE link.contact_id = contact.id",
        "        AND link.deleted = 0",
        "        AND (",
        "          LOWER(BTRIM(link.label)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "          OR LOWER(BTRIM(link.url)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "        )",
        "    )",
        "  )",
        "</if>",
        "ORDER BY LOWER(BTRIM(contact.team_name)) ASC, contact.id ASC",
        "LIMIT #{limit} OFFSET #{offset}",
        "</script>"
    })
    List<SupportTeamContactEntity> searchContacts(@Param("keyword") String keyword, @Param("limit") long limit, @Param("offset") long offset);

    @Select({
        "<script>",
        "SELECT COUNT(*)",
        "FROM support_team_contact contact",
        "WHERE contact.deleted = 0",
        "<if test='keyword != null and keyword != \"\"'>",
        "  AND (",
        "    LOWER(BTRIM(contact.team_name)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(contact.team_email)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.xmatter_group, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.gsd_group, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.eim_id, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR LOWER(BTRIM(COALESCE(contact.other_info, ''))) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    OR EXISTS (",
        "      SELECT 1",
        "      FROM support_team_contact_tag tag",
        "      WHERE tag.contact_id = contact.id",
        "        AND tag.deleted = 0",
        "        AND LOWER(BTRIM(tag.tag)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    )",
        "    OR EXISTS (",
        "      SELECT 1",
        "      FROM support_team_contact_staff binding",
        "      WHERE binding.contact_id = contact.id",
        "        AND binding.deleted = 0",
        "        AND LOWER(BTRIM(binding.staff_id)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "    )",
        "    OR EXISTS (",
        "      SELECT 1",
        "      FROM support_team_contact_link link",
        "      WHERE link.contact_id = contact.id",
        "        AND link.deleted = 0",
        "        AND (",
        "          LOWER(BTRIM(link.label)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "          OR LOWER(BTRIM(link.url)) LIKE CONCAT('%', LOWER(BTRIM(#{keyword})), '%')",
        "        )",
        "    )",
        "  )",
        "</if>",
        "</script>"
    })
    long countContacts(@Param("keyword") String keyword);
}
