package com.lubrimanager.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("""
            select u from User u
              join fetch u.branch
            where lower(u.username) = lower(:value)
               or (u.email is not null and lower(u.email) = lower(:value))
            """)
    Optional<User> findByUsernameOrEmail(@Param("value") String value);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    long countByRoleAndActiveTrue(Role role);

    @Query("""
            select u from User u
            where (lower(u.username) like :pattern
                   or lower(u.fullName) like :pattern
                   or (u.email is not null and lower(u.email) like :pattern))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            """)
    Page<User> search(@Param("pattern") String pattern,
                      @Param("role") Role role,
                      @Param("active") Boolean active,
                      Pageable pageable);
}
