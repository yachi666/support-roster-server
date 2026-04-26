package com.support.server.supportrosterserver.dto.contactinformation;

import java.util.List;

public record ContactInformationListResponse(
    List<ContactInformationDto> items,
    long page,
    long pageSize,
    long total
) {
}
