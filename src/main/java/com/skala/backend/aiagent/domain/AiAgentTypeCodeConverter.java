package com.skala.backend.aiagent.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AiAgentTypeCodeConverter implements AttributeConverter<AiAgentTypeCode, String> {

	@Override
	public String convertToDatabaseColumn(AiAgentTypeCode attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public AiAgentTypeCode convertToEntityAttribute(String dbData) {
		return dbData == null ? null : AiAgentTypeCode.from(dbData);
	}
}
