package com.skala.backend.aiagent.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AiAgentRunStatusCodeConverter implements AttributeConverter<AiAgentRunStatusCode, String> {

	@Override
	public String convertToDatabaseColumn(AiAgentRunStatusCode attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public AiAgentRunStatusCode convertToEntityAttribute(String dbData) {
		return dbData == null ? null : AiAgentRunStatusCode.from(dbData);
	}
}
