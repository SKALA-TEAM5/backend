package com.skala.backend.project.domain;

import com.skala.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "project_user_assignments", schema = "service")
public class ProjectUserAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_by_user_id")
	private User assignedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ProjectUserAssignment() {
	}

	private ProjectUserAssignment(Project project, User user, User assignedBy) {
		this.project = project;
		this.user = user;
		this.assignedBy = assignedBy;
	}

	public static ProjectUserAssignment create(Project project, User user, User assignedBy) {
		return new ProjectUserAssignment(project, user, assignedBy);
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }

	public Project getProject() { return project; }

	public User getUser() { return user; }

	public User getAssignedBy() { return assignedBy; }

	public Instant getCreatedAt() { return createdAt; }
}
