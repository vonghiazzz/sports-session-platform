package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.application.RatingReconciliationResult;
import com.sportssession.platform.rating.application.RatingReconciliationService;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RatingReconciliationSchedulerConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SchedulerBeanTestConfiguration.class);

    @Test
    void schedulerBeanExistsWhenEnabled() {
        contextRunner
                .withPropertyValues("rating.reconciliation.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(RatingReconciliationScheduler.class));
    }

    @Test
    void schedulerBeanIsAbsentWhenDisabled() {
        contextRunner
                .withPropertyValues("rating.reconciliation.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(RatingReconciliationScheduler.class));
    }

    @Test
    void springSchedulingActuallyInvokesEnabledTriggerWithoutSleeping()
            throws InterruptedException {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "rating.reconciliation.enabled=true",
                    "rating.reconciliation.fixed-delay-ms=20"
            ).applyTo(context);
            context.register(
                    RatingSchedulingConfiguration.class,
                    AutomaticSchedulingTestConfiguration.class
            );
            context.refresh();

            assertThat(context.getBean(CountDownLatch.class)
                    .await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RatingReconciliationScheduler.class)
    static class SchedulerBeanTestConfiguration {

        @Bean
        RatingReconciliationService ratingReconciliationService() {
            return mock(RatingReconciliationService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RatingReconciliationScheduler.class)
    static class AutomaticSchedulingTestConfiguration {

        @Bean
        CountDownLatch schedulerInvocation() {
            return new CountDownLatch(1);
        }

        @Bean
        RatingReconciliationService ratingReconciliationService(
                CountDownLatch schedulerInvocation
        ) {
            RatingReconciliationService service = mock(
                    RatingReconciliationService.class
            );
            when(service.reconcileOne(
                    SportCode.BADMINTON,
                    MatchFormat.DOUBLES
            )).thenAnswer(invocation -> {
                schedulerInvocation.countDown();
                return RatingReconciliationResult.IDLE;
            });
            return service;
        }
    }
}
