package com.flexcore.user.repository;

import com.flexcore.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = {"role"})
    Page<User> findAll(Pageable pageable);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole_Id(Long roleId);

    @EntityGraph(attributePaths = {"role"})
    Page<User> findByRole_Name(String roleName, Pageable pageable);
}
