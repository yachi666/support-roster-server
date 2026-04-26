package com.support.server.supportrosterserver.dto.contactinformation;

import java.util.List;

public record ContactInformationDto(
    Long id,
    String name,
    String email,
    String xMatter,
    String gsd,
    String eim,
    List<String> roles,
    List<ContactInformationStaffDto> staff,
    List<ContactInformationLinkDto> links
) {
}
