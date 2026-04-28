package com.support.server.supportrosterserver.service.workspace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordCredentialEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerEntity;
import com.support.server.supportrosterserver.mapper.LinuxPasswordCredentialMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerMapper;

import lombok.RequiredArgsConstructor;

/**
 * Runs once at application startup to explicitly backfill legacy plaintext
 * credentials stored in the server table (username/password columns) into the
 * dedicated workspace_linux_password_credential table.
 *
 * This replaces the previous lazy read-side mutation that ran during every
 * list/detail request, making the migration path explicit and traceable.
 */
@Component
@RequiredArgsConstructor
public class LinuxPasswordLegacyBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LinuxPasswordLegacyBackfillRunner.class);

    private final LinuxPasswordServerMapper serverMapper;
    private final LinuxPasswordCredentialMapper credentialMapper;
    private final LinuxPasswordSecretService secretService;

    @Override
    public void run(ApplicationArguments args) {
        List<LinuxPasswordServerEntity> servers = serverMapper.selectList(
            Wrappers.<LinuxPasswordServerEntity>lambdaQuery()
                .isNotNull(LinuxPasswordServerEntity::getUsername)
                .ne(LinuxPasswordServerEntity::getUsername, "")
        );

        if (servers.isEmpty()) {
            return;
        }

        List<Long> serverIds = servers.stream()
            .map(LinuxPasswordServerEntity::getId)
            .filter(id -> id != null)
            .toList();

        Set<Long> serverIdsWithCredentials = credentialMapper.selectList(
                Wrappers.<LinuxPasswordCredentialEntity>lambdaQuery()
                    .in(LinuxPasswordCredentialEntity::getServerId, serverIds))
            .stream()
            .map(LinuxPasswordCredentialEntity::getServerId)
            .collect(Collectors.toSet());

        int backfilled = 0;
        for (LinuxPasswordServerEntity server : servers) {
            if (server.getId() == null
                    || serverIdsWithCredentials.contains(server.getId())
                    || !hasText(server.getUsername())
                    || !hasText(server.getPassword())) {
                continue;
            }
            try {
                LinuxPasswordSecretService.EncryptedSecret encrypted = secretService.encrypt(server.getPassword());
                LinuxPasswordCredentialEntity credential = new LinuxPasswordCredentialEntity();
                credential.setServerId(server.getId());
                credential.setUsername(server.getUsername().trim());
                credential.setPasswordCiphertext(encrypted.ciphertext());
                credential.setPasswordIv(encrypted.iv());
                credential.setKeyVersion(encrypted.keyVersion());
                credentialMapper.insert(credential);
                backfilled++;
                log.info("Backfilled legacy credential for server id={} hostname={}", server.getId(), server.getHostname());
            } catch (Exception ex) {
                log.error("Failed to backfill credential for server id={} hostname={}: {}",
                    server.getId(), server.getHostname(), ex.getMessage());
            }
        }

        if (backfilled > 0) {
            log.info("Linux password legacy backfill complete: {} credential(s) migrated", backfilled);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
