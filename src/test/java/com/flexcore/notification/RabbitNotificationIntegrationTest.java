package com.flexcore.notification;

import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.entity.GymClass;
import com.flexcore.gymclass.event.BookingConfirmedEvent;
import com.flexcore.gymclass.repository.ClassBookingRepository;
import com.flexcore.gymclass.repository.GymClassRepository;
import com.flexcore.gymclass.service.ClassBookingService;
import com.flexcore.outbox.OutboxPublisher;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end proof of the outbox → RabbitMQ → consumer route against a real broker:
 * a confirmed booking records a PENDING outbox row, the worker publishes it to the
 * {@code flexcore.events} exchange, and the {@link NotificationConsumer} receives it
 * from the notification queue. Requires a reachable broker on localhost:5672
 * (docker run -d --name flexcore-rabbitmq -p 5672:5672 rabbitmq:3-management).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.rabbit.enabled=true")
class RabbitNotificationIntegrationTest {

    @Autowired private ClassBookingService classBookingService;
    @Autowired private GymClassRepository gymClassRepository;
    @Autowired private ClassBookingRepository classBookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private NotificationConsumer notificationConsumer;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        notificationConsumer.reset();
        transactionTemplate.executeWithoutResult(status -> {
            gymClassRepository.findByName("Rabbit Class").forEach(gc -> {
                classBookingRepository.deleteByGymClass_Id(gc.getId());
                gymClassRepository.delete(gc);
            });
            userRepository.findByEmail("rabbit-trainer@example.com").ifPresent(trainer -> {
                subscriptionRepository.findByUserIdOrderByEndDateAsc(trainer.getId())
                        .forEach(subscriptionRepository::delete);
                userRepository.delete(trainer);
            });
            subscriptionPlanRepository.findByName("Rabbit Plan")
                    .forEach(subscriptionPlanRepository::delete);
            outboxEventRepository.deleteAll();
        });
    }

    @Test
    void confirmedBooking_isDeliveredThroughRabbitMq_toTheNotificationConsumer() throws Exception {
        createMember();
        GymClass gymClass = createGymClass();
        User member = userRepository.findByEmail("rabbit-trainer@example.com").orElseThrow();

        ClassBookingResponse response = classBookingService.book(gymClass.getId(), member.getId());

        OutboxEvent recorded = outboxEventRepository
                .findFirstByEventTypeOrderByIdDesc(BookingConfirmedEvent.TYPE);
        assertNotNull(recorded);
        assertEquals(OutboxStatus.PENDING, recorded.getStatus());

        assertEquals(1, outboxPublisher.publishPendingEvents());

        OutboxEvent published = outboxEventRepository.findById(recorded.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, published.getStatus());

        BookingConfirmedEvent delivered = notificationConsumer.await(10_000);
        assertNotNull(delivered, "Message never arrived from the notification queue");
        assertEquals(response.getId(), delivered.bookingId());
        assertEquals(response.getUserId(), delivered.memberId());
    }

    private User createMember() {
        Role memberRole = roleRepository.findByName("MEMBER").orElseThrow();
        User member = userRepository.save(User.builder()
                .fullName("Rabbit Trainer")
                .email("rabbit-trainer@example.com")
                .passwordHash("x")
                .role(memberRole)
                .active(true)
                .build());
        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("Rabbit Plan")
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
        User trainer = userRepository.findByEmail("rabbit-trainer@example.com").orElseThrow();
        return gymClassRepository.save(GymClass.builder()
                .name("Rabbit Class")
                .trainer(trainer)
                .capacity(5)
                .bookedCount(0)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .build());
    }
}