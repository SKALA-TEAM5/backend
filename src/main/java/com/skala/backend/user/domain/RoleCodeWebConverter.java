package com.skala.backend.user.domain;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class RoleCodeWebConverter implements Converter<String, RoleCode> {

	@Override
	public RoleCode convert(String source) {
		return RoleCode.from(source);
	}
}
