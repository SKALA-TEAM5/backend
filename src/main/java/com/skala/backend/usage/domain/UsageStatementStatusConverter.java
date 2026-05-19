package com.skala.backend.usage.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UsageStatementStatusConverter implements AttributeConverter<UsageStatementStatus, String> {

    @Override
    public String convertToDatabaseColumn(UsageStatementStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public UsageStatementStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UsageStatementStatus.from(dbData);
    }
}
