package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = TelebirrReferenceConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTelebirrReference {
    String message() default "Telebirr reference must normalize to between 1 and 120 ASCII letters or digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
