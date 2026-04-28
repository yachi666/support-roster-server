package com.support.server.supportrosterserver.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordCredentialEntity;
import com.support.server.supportrosterserver.entity.workspace.LinuxPasswordServerEntity;
import com.support.server.supportrosterserver.mapper.LinuxPasswordCredentialMapper;
import com.support.server.supportrosterserver.mapper.LinuxPasswordServerMapper;

class LinuxPasswordLegacyBackfillRunnerTest {

    private LinuxPasswordServerMapper serverMapper;
    private LinuxPasswordCredentialMapper credentialMapper;
    private LinuxPasswordSecretService secretService;
    private LinuxPasswordLegacyBackfillRunner runner;

    @BeforeEach
    void setUp() {
        serverMapper = mock(LinuxPasswordServerMapper.class);
        credentialMapper = mock(LinuxPasswordCredentialMapper.class);
        secretService = new LinuxPasswordSecretService("test-linux-password-secret");
        runner = new LinuxPasswordLegacyBackfillRunner(serverMapper, credentialMapper, secretService);
    }

    @Test
    void shouldBackfillCredentialWhenServerHasLegacyUsernamePasswordAndNoCredential() throws Exception {
        LinuxPasswordServerEntity server = buildServer(1L, "legacy-host", "10.0.0.1", "admin", "PlainPass@1");
        when(serverMapper.selectList(any())).thenReturn(List.of(server));
        when(credentialMapper.selectList(any())).thenReturn(List.of());

        runner.run(mock(ApplicationArguments.class));

        ArgumentCaptor<LinuxPasswordCredentialEntity> captor = ArgumentCaptor.forClass(LinuxPasswordCredentialEntity.class);
        verify(credentialMapper).insert(any(LinuxPasswordCredentialEntity.class));
    }

    @Test
    void shouldSkipBackfillWhenCredentialAlreadyExists() throws Exception {
        LinuxPasswordServerEntity server = buildServer(2L, "host-with-cred", "10.0.0.2", "root", "OldPass@2");
        LinuxPasswordCredentialEntity existing = new LinuxPasswordCredentialEntity();
        existing.setServerId(2L);
        existing.setUsername("root");

        when(serverMapper.selectList(any())).thenReturn(List.of(server));
        when(credentialMapper.selectList(any())).thenReturn(List.of(existing));

        runner.run(mock(ApplicationArguments.class));

        verify(credentialMapper, never()).insert(any(LinuxPasswordCredentialEntity.class));
    }

    @Test
    void shouldSkipBackfillWhenServerHasNoLegacyCredentials() throws Exception {
        LinuxPasswordServerEntity server = buildServer(3L, "clean-host", "10.0.0.3", null, null);
        when(serverMapper.selectList(any())).thenReturn(List.of(server));
        when(credentialMapper.selectList(any())).thenReturn(List.of());

        runner.run(mock(ApplicationArguments.class));

        verify(credentialMapper, never()).insert(any(LinuxPasswordCredentialEntity.class));
    }

    @Test
    void shouldBackfillMultipleServersWithLegacyCredentials() throws Exception {
        LinuxPasswordServerEntity server1 = buildServer(4L, "host-a", "10.0.0.4", "user1", "Pass@A1");
        LinuxPasswordServerEntity server2 = buildServer(5L, "host-b", "10.0.0.5", "user2", "Pass@B2");

        when(serverMapper.selectList(any())).thenReturn(List.of(server1, server2));
        when(credentialMapper.selectList(any()))
            .thenReturn(List.of())  // for server1 check
            .thenReturn(List.of()); // for server2 check

        runner.run(mock(ApplicationArguments.class));

        verify(credentialMapper, times(2)).insert(any(LinuxPasswordCredentialEntity.class));
    }

    private LinuxPasswordServerEntity buildServer(Long id, String hostname, String ip, String username, String password) {
        LinuxPasswordServerEntity entity = new LinuxPasswordServerEntity();
        entity.setId(id);
        entity.setHostname(hostname);
        entity.setIp(ip);
        entity.setUsername(username);
        entity.setPassword(password);
        entity.setStatus("online");
        return entity;
    }
}
