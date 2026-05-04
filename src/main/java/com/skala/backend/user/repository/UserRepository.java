package com.skala.backend.user.repository;

import com.skala.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmployeeNo(String employeeNo);

	Optional<User> findByEmployeeNo(String employeeNo);
}
