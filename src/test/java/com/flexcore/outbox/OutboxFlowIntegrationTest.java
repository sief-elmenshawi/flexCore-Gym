package com.flexcore.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.entity.GymClass;
import com.flexcore.gymclass.event.BookingConfirmedEvent;
import com.flexcore.gymclass.repository.ClassBookingRepository;
import com.flexcore.gymclass.repository.GymClassRepository;
import com.flexcore.gymclass.service.ClassBookingService;
import com.flexcore.outbox.entity.OutboxEvent;
import com.flexcore.outbox.enums.OutboxStatus;
import com.flexcore.outbox.repository.OutboxEventRepository;
import com.flexcore.role.entity.Role;
import com.flexcore.role.repository.RoleRepository;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end proof of the outbox pattern against a real database: a confirmed booking
 * records a PENDING outbox row in the same transaction, and the background worker
 * (driven manually here) publishes it and marks it PUBLISHED. Also verifies the two
 * failure-isolation properties: orphan events never crash the worker, and the recorder
 * refuses to run outside a transaction.
 * Requires a reachable test database (see src/test/resources/application-test.yml):
 *   docker exec flexcore-postgres psql -U postgres -c "CREATE DATABASE flexcore_test"
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxFlowIntegrationTest {

    private static final String UNKNOWN_EVENT_TYPE = "outbox.unknown";

    @Autowired private ClassBookingService classBookingService;
    @Autowired private GymClassRepository gymClassRepository;
    @Autowired private ClassBookingRepository classBookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private OutboxEventRecorder outboxEventRecorder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            gymClassRepository.findByName("Outbox Class").forEach(gc -> {
                classBookingRepository.deleteByGymClass_Id(gc.getId());
                gymClassRepository.delete(gc);
            });
            userRepository.findByEmail("outbox-trainer@example.com").ifPresent(trainer -> {
                subscriptionRepository.findByUserIdAndDeletedAtIsNullOrderByEndDateAsc(trainer.getId())
                        .forEach(subscriptionRepository::delete);
                userRepository.delete(trainer);
            });
            subscriptionPlanRepository.findByName("Outbox Plan")
                    .forEach(subscriptionPlanRepository::delete);
            outboxEventRepository.deleteAll();
        });
    }

    @Test
    void bookingConfirmation_isRecordedInTheSameTransaction_thenPublishedByTheWorker() throws Exception {
        User member = createMember();
        GymClass gymClass = createGymClass();

        ClassBookingResponse response = classBookingService.book(gymClass.getId(), member.getId());

        OutboxEvent recorded = outboxEventRepository
                .findFirstByEventTypeOrderByIdDesc(BookingConfirmedEvent.TYPE);
        assertNotNull(recorded);
        assertEquals(OutboxStatus.PENDING, recorded.getStatus());
        assertEquals("class_booking", recorded.getAggregateType());
        assertEquals(response.getId(), recorded.getAggregateId());

        JsonNode payload = objectMapper.readTree(recorded.getPayload());
        assertEquals(response.getId(), payload.get("bookingId").asLong());
        assertEquals(member.getId(), payload.get("memberId").asLong());
        assertEquals("Outbox Class", payload.get("className").asText());

        assertEquals(1, outboxPublisher.publishPendingEvents());

        OutboxEvent published = outboxEventRepository.findById(recorded.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void eventWithoutHandler_isIsolatedAndNeverCrashesTheWorker() {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("some_aggregate")
                .aggregateId(1L)
                .eventType(UNKNOWN_EVENT_TYPE)
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build());

        assertEquals(0, outboxPublisher.publishPendingEvents());

        OutboxEvent still = outboxEventRepository
                .findFirstByEventTypeOrderByIdDesc(UNKNOWN_EVENT_TYPE);
        assertNotNull(still);
        assertEquals(OutboxStatus.PENDING, still.getStatus());
        assertEquals(0, still.getAttempts());
    }

    @Test
    void recorder_refusesToRunOutsideATransaction() {
        var payload = new BookingConfirmedEvent(1L, 1L, 1L, "Outbox Class", LocalDateTime.now());

        assertThrows(IllegalTransactionStateException.class,
                () -> outboxEventRecorder.recordEvent(BookingConfirmedEvent.TYPE, "class_booking", 1L, payload));
    }

    private User createMember() {
        Role trainerRole = roleRepository.findByName("TRAINER").orElseThrow();
        User member = userRepository.save(User.builder()
                .fullName("Outbox Trainer")
                .email("outbox-trainer@example.com")
                .passwordHash("x")
                .role(trainerRole)
                .active(true)
                .build());
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("Outbox Plan")
                .price(new BigDecimal("100.00"))
                .durationInDays(30)
                .build());
        subscriptionRepository.save(Subscription.builder()
                .user(member)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .build());
        return member;
    }

    private GymClass createGymClass() {
        User trainer = userRepository.findByEmail("outbox-trainer@example.com").orElseThrow();
        return gymClassRepository.save(GymClass.builder()
                .name("Outbox Class")
                .trainer(trainer)
                .capacity(5)
                .bookedCount(0)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .build());
    }
}