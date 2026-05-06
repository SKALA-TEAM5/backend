package com.skala.backend.evidence.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class EvidenceRequests {

	private EvidenceRequests() {
	}

	public record LinkEvidenceFileRequest(
			@Schema(description = "연결할 파일 ID", example = "1")
			@NotNull Long fileId,
			@Schema(
					description = "증빙 유형 코드",
					example = "receipt",
					allowableValues = {
							"receipt",
							"tax_invoice",
							"tax_invoice_confirm",
							"third_party_lookup",
							"transaction_statement",
							"site_photo",
							"item_photo",
							"wearing_photo",
							"work_photo",
							"appointment_report",
							"pay_stub",
							"work_log",
							"daily_output_log",
							"inspection_log",
							"supply_ledger",
							"inventory_ledger",
							"edu_confirm",
							"edu_attendance",
							"transfer_confirm",
							"health_checkup_result",
							"health_checkup_contract",
							"tech_guidance_contract",
							"tech_guidance_report",
							"tech_guidance_photo",
							"usage_statement",
							"analysis_table",
							"purchase_detail",
							"other_document"
					}
			)
			@NotBlank String evidenceTypeCode
	) {
	}

	public record MoveEvidenceFileLinkRequest(
			@Schema(description = "이동할 대상 상세항목 ID", example = "1")
			@NotNull Long targetItemId,
			@Schema(
					description = "증빙 유형 코드",
					example = "receipt",
					allowableValues = {
							"receipt",
							"tax_invoice",
							"tax_invoice_confirm",
							"third_party_lookup",
							"transaction_statement",
							"site_photo",
							"item_photo",
							"wearing_photo",
							"work_photo",
							"appointment_report",
							"pay_stub",
							"work_log",
							"daily_output_log",
							"inspection_log",
							"supply_ledger",
							"inventory_ledger",
							"edu_confirm",
							"edu_attendance",
							"transfer_confirm",
							"health_checkup_result",
							"health_checkup_contract",
							"tech_guidance_contract",
							"tech_guidance_report",
							"tech_guidance_photo",
							"usage_statement",
							"analysis_table",
							"purchase_detail",
							"other_document"
					}
			)
			@NotBlank String evidenceTypeCode
	) {
	}
}
