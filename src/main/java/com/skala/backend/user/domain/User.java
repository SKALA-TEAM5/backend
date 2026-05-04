package com.skala.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "service")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "employee_no", nullable = false, length = 50, unique = true)
	private String employeeNo;

	@Column(name = "real_name", nullable = false, length = 100)
	private String realName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "role_code", nullable = false, length = 30)
	private RoleCode roleCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	private User(String employeeNo, String realName, String passwordHash, RoleCode roleCode) {
		this.employeeNo = employeeNo;
		this.realName = realName;
		this.passwordHash = passwordHash;
		this.roleCode = roleCode;
	}

	public static User create(String employeeNo, String realName, String passwordHash, RoleCode roleCode) {
		return new User(employeeNo, realName, passwordHash, roleCode);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getEmployeeNo() {
		return employeeNo;
	}

	public String getRealName() {
		return realName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public RoleCode getRoleCode() {
		return roleCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
