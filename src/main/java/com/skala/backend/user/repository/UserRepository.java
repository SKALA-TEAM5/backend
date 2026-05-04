package com.skala.backend.user.repository;

import com.skala.backend.user.domain.User;
import com.skala.backend.user.domain.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmployeeNo(String employeeNo);

	Optional<User> findByEmployeeNo(String employeeNo);

	@Query("""
			SELECT u
			FROM User u
			WHERE (:roleCode IS NULL OR u.roleCode = :roleCode)
				AND (:keyword IS NULL
					OR LOWER(u.employeeNo) LIKE LOWER(CONCAT('%', :keyword, '%'))
					OR LOWER(u.realName) LIKE LOWER(CONCAT('%', :keyword, '%')))
			ORDER BY u.id DESC
			""")
	List<User> search(@Param("roleCode") RoleCode roleCode, @Param("keyword") String keyword);
}
