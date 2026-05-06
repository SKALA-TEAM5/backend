package com.skala.backend.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "usage_statement_items", schema = "service")
public class UsageStatementItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usage_statement_id", nullable = false)
	private Long usageStatementId;

	@Column(name = "category_code", nullable = false, length = 50)
	private String categoryCode;

	@Column(name = "used_on", nullable = false)
	private LocalDate usedOn;

	@Column(name = "item_name", nullable = false, length = 300)
	private String itemName;

	@Column(name = "unit", length = 50)
	private String unit;

	@Column(nullable = false)
	private BigDecimal quantity;

	@Column(name = "unit_price", nullable = false)
	private BigDecimal unitPrice;

	@Column(name = "total_amount", nullable = false)
	private BigDecimal totalAmount;

	@Column(length = 1000)
	private String remark;

	@Column(name = "page_no", nullable = false)
	private Integer pageNo;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UsageStatementItem() {
	}

	public Long getId() { return id; }
	public Long getUsageStatementId() { return usageStatementId; }
	public String getCategoryCode() { return categoryCode; }
	public LocalDate getUsedOn() { return usedOn; }
	public String getItemName() { return itemName; }
	public String getUnit() { return unit; }
	public BigDecimal getQuantity() { return quantity; }
	public BigDecimal getUnitPrice() { return unitPrice; }
	public BigDecimal getTotalAmount() { return totalAmount; }
	public String getRemark() { return remark; }
	public Integer getPageNo() { return pageNo; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
