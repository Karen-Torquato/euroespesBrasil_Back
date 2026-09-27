package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:flywaytest;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.seed.enabled=false",
    "app.seed.create-default-admin=false"
})
class FlywayMigrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseFlywayAndValidateSchema() {
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.flyway.locations")).contains("classpath:db/migration");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        Integer auditoriaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'AUDITORIA_EVENTOS'",
                Integer.class
        );
        assertThat(auditoriaCount).isEqualTo(1);

        Integer anexoShaColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = 'PACIENTES' AND COLUMN_NAME = 'ANEXO_SHA256'",
                Integer.class
        );
        assertThat(anexoShaColumnCount).isEqualTo(1);

        Integer cpfLen = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_NAME = 'PACIENTES' AND COLUMN_NAME = 'CPF'",
                Integer.class
        );
        assertThat(cpfLen).isGreaterThanOrEqualTo(512);
    }
}
