package org.example.operations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Backup/Restore — scripts operacionais")
class BackupRestoreScriptsTest {

    @Test
    @DisplayName("script local deve conter backup e restore com validação de erro")
    void scriptLocalDeveConterComandosCriticos() throws IOException {
        String content = Files.readString(Path.of("scripts", "backup-restore-local.ps1"));

        assertThat(content).contains("pg_dump");
        assertThat(content).contains("pg_restore");
        assertThat(content).contains("dropdb");
        assertThat(content).contains("createdb");
        assertThat(content).contains(".backups");
        assertThat(content).contains("$LASTEXITCODE");
    }

    @Test
    @DisplayName("script prod-sim deve conter backup e restore com validação de erro")
    void scriptProdSimDeveConterComandosCriticos() throws IOException {
        String content = Files.readString(Path.of("scripts", "backup-restore-prod-sim.ps1"));

        assertThat(content).contains("pg_dump");
        assertThat(content).contains("pg_restore");
        assertThat(content).contains("dropdb");
        assertThat(content).contains("createdb");
        assertThat(content).contains(".backups");
        assertThat(content).contains("$LASTEXITCODE");
    }
}
