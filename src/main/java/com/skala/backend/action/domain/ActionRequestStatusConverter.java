package com.skala.backend.action.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ActionRequestStatusConverter implements AttributeConverter<ActionRequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(ActionRequestStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ActionRequestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ActionRequestStatus.from(dbData);
    }
}
