package com.flexcore.gymclass;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.gymclass.entity.GymClass;
import com.flexcore.gymclass.repository.GymClassRepository;
import com.flexcore.gymclass.service.ClassBookingService;
import com.flexcore.role.entity.Role;
import com.flexcore.role.repository.RoleRepository;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test that reproduces the real overbooking race: N members
 * booking the last seat at the same moment. Requires a reachable test
 * database (see src/test/resources/application-test.yml). Create it once with:
 *   docker exec flexcore-postgres psql -U postgres -c "CREATE DATABASE flexcore_test"
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

    @Autowired
    private ClassBookingService classBookingService;

    @Autowired
    private GymClassRepository gymClassRepository;

    @Autowired
    private com.flexcore.gymclass.repository.ClassBookingRepository classBookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Test
    void concurrentBookings_onSingleSeat_onlyOneSucceeds() throws Exception {
        cleanUp();

        Role trainerRole = roleRepository.findByName("TRAINER").orElseThrow();
        User trainer = userRepository.save(User.builder()
                .fullName("Concurrency Trainer")
                .email("concurrency-trainer@example.com")
                .passwordHash("x")
                .role(trainerRole)
                .active(true)
                .build());

        SubscriptionPlan plan = subscriptionPlanRepository.save(SubscriptionPlan.builder()
                .name("Rush Plan")
                .price(new java.math.BigDecimal("100.00"))
                .durationInDays(30)
                .build());
        subscriptionRepository.save(Subscription.builder()
                .user(trainer)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(30))
                .build());

        GymClass gymClass = gymClassRepository.save(GymClass.builder()
                .name("Rush Class")
                .trainer(trainer)
                .capacity(1)
                .bookedCount(0)
                .startsAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .build());

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        List<Future<Boolean>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long userId = trainer.getId(); // any active user works for the service layer
            futures.add(pool.submit((Callable<Boolean>) () -> {
                startGate.await();
                try {
                    classBookingService.book(gymClass.getId(), userId);
                    return true;
                } catch (BusinessRuleViolationException ex) {
                    return false;
                }
            }));
        }

        startGate.countDown();
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get())) {
                successes.incrementAndGet();
            }
        }
        pool.shutdown();

        GymClass reloaded = gymClassRepository.findById(gymClass.getId()).orElseThrow();
        assertEquals(1, successes.get(), "Exactly one booking must win the race");
        assertEquals(1, reloaded.getBookedCount(), "Booked count must never exceed capacity");

        cleanUp();
    }

    private void cleanUp() {
        // Idempotent: a previous failed run may have left data behind
        transactionTemplate.executeWithoutResult(status -> {
            gymClassRepository.findByName("Rush Class").forEach(gc -> {
                classBookingRepository.deleteByGymClass_Id(gc.getId());
                gymClassRepository.delete(gc);
            });
            userRepository.findByEmail("concurrency-trainer@example.com").ifPresent(trainer -> {
                subscriptionRepository.findByUserIdOrderByEndDateAsc(trainer.getId())
                        .forEach(subscriptionRepository::delete);
                userRepository.delete(trainer);
            });
            subscriptionPlanRepository.findByName("Rush Plan")
                    .forEach(subscriptionPlanRepository::delete);
        });
    }
}
