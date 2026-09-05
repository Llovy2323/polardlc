package ru.virtuoz.convert;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Convert {
    ConvertType value() default ConvertType.DEFAULT;

    enum ConvertType {
        ULTRA,
        VIRTUALIZATION,
        MUTATION,
        DEFAULT,
        EXCEPTION
    }
}
