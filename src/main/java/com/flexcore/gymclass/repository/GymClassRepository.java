package com.flexcore.gymclass.repository;

import com.flexcore.gymclass.entity.GymClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GymClassRepository extends JpaRepository<GymClass, Long>, JpaSpecificationExecutor<GymClass> {

    @Override
    @EntityGraph(attributePaths = {"trainer"})
    Page<GymClass> findAll(Specification<GymClass> spec, Pageable pageable);

    List<GymClass> findByName(String name);
}
