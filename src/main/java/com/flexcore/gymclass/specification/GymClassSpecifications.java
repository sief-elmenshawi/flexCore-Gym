package com.flexcore.gymclass.specification;

import com.flexcore.gymclass.entity.GymClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class GymClassSpecifications {

    private GymClassSpecifications() {
    }

    public static Specification<GymClass> nameContains(String name) {
        return (root, query, cb) -> name == null || name.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<GymClass> hasTrainer(Long trainerId) {
        return (root, query, cb) -> trainerId == null
                ? cb.conjunction()
                : cb.equal(root.get("trainer").get("id"), trainerId);
    }

    public static Specification<GymClass> startsAtFrom(LocalDateTime from) {
        return (root, query, cb) -> from == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    public static Specification<GymClass> startsAtTo(LocalDateTime to) {
        return (root, query, cb) -> to == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("startsAt"), to);
    }
}
