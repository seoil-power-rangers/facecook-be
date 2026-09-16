package com.facecook.mission.event;

import com.facecook.mission.dto.MissionProgressResponse;
import com.facecook.mission.redis.MissionEventPublisher;
import com.facecook.config.AsyncConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig({MissionProgressCommittedIntegrationTest.Config.class, AsyncConfig.class})
class MissionProgressCommittedIntegrationTest {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private MissionEventPublisher missionEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void publishesToRedisOnlyAfterTransactionCommit() {
        MissionProgressResponse progress =
                new MissionProgressResponse(20L, 2, "STEP 2 미션", null, null, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            applicationEventPublisher.publishEvent(new MissionProgressCommittedEvent(progress));
            verify(missionEventPublisher, never()).publish(progress);
        });

        verify(missionEventPublisher, timeout(1_000)).publish(progress);
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        MissionEventPublisher missionEventPublisher() {
            return mock(MissionEventPublisher.class);
        }

        @Bean
        MissionProgressCommittedListener listener(MissionEventPublisher publisher) {
            return new MissionProgressCommittedListener(publisher);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:mission-event-test;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
