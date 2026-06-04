package com.skala.backend.usage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class UsageStatementItemRequests {

	private UsageStatementItemRequests() {
	}

	public record CreateItemRequest(
			@NotBlank(message = "categoryCode는 필수입니다.")
			String categoryCode,

			@NotNull(message = "usedOn은 필수입니다.")
			LocalDate usedOn,

			@NotBlank(message = "itemName은 필수입니다.")
			@Size(max = 300, message = "itemName은 300자 이하여야 합니다.")
			String itemName,

			@Size(max = 50, message = "unit은 50자 이하여야 합니다.")
			String unit,

			@NotNull(message = "quantity는 필수입니다.")
			@Positive(message = "quantity는 0보다 커야 합니다.")
			BigDecimal quantity,

			@NotNull(message = "unitPrice는 필수입니다.")
			@PositiveOrZero(message = "unitPrice는 0 이상이어야 합니다.")
			BigDecimal unitPrice,

			@NotNull(message = "totalAmount는 필수입니다.")
			@PositiveOrZero(message = "totalAmount는 0 이상이어야 합니다.")
			BigDecimal totalAmount,

			@Size(max = 1000, message = "remark는 1000자 이하여야 합니다.")
			String remark,

			@NotNull(message = "pageNo는 필수입니다.")
			@Positive(message = "pageNo는 0보다 커야 합니다.")
			Integer pageNo
	) {
	}

	public record UpdateItemRequest(
			@NotNull(message = "usedOn은 필수입니다.")
			LocalDate usedOn,

			@NotBlank(message = "itemName은 필수입니다.")
			@Size(max = 300, message = "itemName은 300자 이하여야 합니다.")
			String itemName,

			@Size(max = 50, message = "unit은 50자 이하여야 합니다.")
			String unit,

			@NotNull(message = "quantity는 필수입니다.")
			@Positive(message = "quantity는 0보다 커야 합니다.")
			BigDecimal quantity,

			@NotNull(message = "unitPrice는 필수입니다.")
			@PositiveOrZero(message = "unitPrice는 0 이상이어야 합니다.")
			BigDecimal unitPrice,

			@NotNull(message = "totalAmount는 필수입니다.")
			@PositiveOrZero(message = "totalAmount는 0 이상이어야 합니다.")
			BigDecimal totalAmount,

			@Size(max = 1000, message = "remark는 1000자 이하여야 합니다.")
			String remark,

			@NotNull(message = "pageNo는 필수입니다.")
			@Positive(message = "pageNo는 0보다 커야 합니다.")
			Integer pageNo
	) {
	}

	public record ChangeCategoryRequest(
			@NotBlank(message = "categoryCode는 필수입니다.")
			String categoryCode
	) {
	}
}
