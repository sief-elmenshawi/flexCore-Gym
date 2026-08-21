package com.flexcore.subscription.repository;

import com.flexcore.subscription.entity.FamilyGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

    @EntityGraph(attributePaths = {"ownerUser", "plan"})
    List<FamilyGroup> findByOwnerUserId(Long ownerUserId);
}
