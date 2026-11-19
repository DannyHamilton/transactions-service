package com.example.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationSmokeTest {

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoadsAgainstH2() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThat(c.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }

    @Test
    void uuidV7GeneratorProducesVersion7() {
        assertThat(UuidCreator.getTimeOrderedEpoch().version()).isEqualTo(7);
    }
}