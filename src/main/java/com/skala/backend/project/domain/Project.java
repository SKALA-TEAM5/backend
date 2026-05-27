package com.skala.backend.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "projects", schema = "service")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "contract_no", nullable = false, length = 100)
	private String contractNo;

	@Column(name = "construction_company", nullable = false, length = 200)
	private String constructionCompany;

	@Column(name = "project_name", nullable = false, length = 300)
	private String projectName;

	@Column(name = "site_location", nullable = false, length = 500)
	private String siteLocation;

	@Column(name = "representative_name", length = 100)
	private String representativeName;

	@Column(name = "contract_amount", nullable = false)
	private BigDecimal contractAmount;

	@Column(name = "construction_start_date", nullable = false)
	private LocalDate constructionStartDate;

	@Column(name = "construction_end_date", nullable = false)
	private LocalDate constructionEndDate;

	@Column(name = "client_name", length = 200)
	private String clientName;

	@Column(name = "appropriated_amount", nullable = false)
	private BigDecimal appropriatedAmount;

	@Column(name = "project_status_code", nullable = false, length = 30)
	private ProjectStatusCode status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Project() {
	}

	private Project(
			String contractNo,
			String constructionCompany,
			String projectName,
			String siteLocation,
			String representativeName,
			BigDecimal contractAmount,
			LocalDate constructionStartDate,
			LocalDate constructionEndDate,
			String clientName,
			BigDecimal appropriatedAmount,
			ProjectStatusCode status
	) {
		this.contractNo = contractNo;
		this.constructionCompany = constructionCompany;
		this.projectName = projectName;
		this.siteLocation = siteLocation;
		this.representativeName = representativeName;
		this.contractAmount = contractAmount;
		this.constructionStartDate = constructionStartDate;
		this.constructionEndDate = constructionEndDate;
		this.clientName = clientName;
		this.appropriatedAmount = appropriatedAmount;
		this.status = status == null ? ProjectStatusCode.ACTIVE : status;
	}

	public static Project create(
			String contractNo,
			String constructionCompany,
			String projectName,
			String siteLocation,
			String representativeName,
			BigDecimal contractAmount,
			LocalDate constructionStartDate,
			LocalDate constructionEndDate,
			String clientName,
			BigDecimal appropriatedAmount,
			ProjectStatusCode status
	) {
		return new Project(
				contractNo,
				constructionCompany,
				projectName,
				siteLocation,
				representativeName,
				contractAmount,
				constructionStartDate,
				constructionEndDate,
				clientName,
				appropriatedAmount,
				status
		);
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

	public Long getId() { return id; }

	public String getContractNo() { return contractNo; }

	public String getConstructionCompany() { return constructionCompany; }

	public String getProjectName() { return projectName; }

	public String getSiteLocation() { return siteLocation; }

	public String getRepresentativeName() { return representativeName; }

	public BigDecimal getContractAmount() { return contractAmount; }

	public LocalDate getConstructionStartDate() { return constructionStartDate; }

	public LocalDate getConstructionEndDate() { return constructionEndDate; }

	public String getClientName() { return clientName; }

	public BigDecimal getAppropriatedAmount() { return appropriatedAmount; }

	public ProjectStatusCode getStatus() { return status; }

	public Instant getCreatedAt() { return createdAt; }

	public Instant getUpdatedAt() { return updatedAt; }

	public void updateContractNo(String contractNo) { this.contractNo = contractNo; }

	public void updateConstructionCompany(String constructionCompany) { this.constructionCompany = constructionCompany; }

	public void updateProjectName(String projectName) { this.projectName = projectName; }

	public void updateSiteLocation(String siteLocation) { this.siteLocation = siteLocation; }

	public void updateRepresentativeName(String representativeName) { this.representativeName = representativeName; }

	public void updateContractAmount(BigDecimal contractAmount) { this.contractAmount = contractAmount; }

	public void updateConstructionStartDate(LocalDate constructionStartDate) { this.constructionStartDate = constructionStartDate; }

	public void updateConstructionEndDate(LocalDate constructionEndDate) { this.constructionEndDate = constructionEndDate; }

	public void updateClientName(String clientName) { this.clientName = clientName; }

	public void updateAppropriatedAmount(BigDecimal appropriatedAmount) { this.appropriatedAmount = appropriatedAmount; }

	public void updateStatus(ProjectStatusCode status) { this.status = status; }
}
