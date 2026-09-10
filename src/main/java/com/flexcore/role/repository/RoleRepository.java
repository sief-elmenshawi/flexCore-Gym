package com.flexcore.role.repository;

import com.flexcore.role.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    @Override
    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAll();

    Optional<Role> findByName(String name);

    boolean existsByName(String name);
}
