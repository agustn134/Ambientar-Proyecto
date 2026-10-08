package com.proyecto.servicios.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<PasswordValida, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.codePointCount(0, value.length()) < 8
                || value.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        boolean upper=false, lower=false, number=false, special=false;
        for (int code : value.codePoints().toArray()) {
            if (Character.isWhitespace(code) || Character.isSpaceChar(code) || Character.isISOControl(code)) return false;
            upper |= Character.isUpperCase(code);
            lower |= Character.isLowerCase(code);
            number |= code >= '0' && code <= '9';
            special |= !Character.isLetterOrDigit(code);
        }
        return upper && lower && number && special;
    }
}
