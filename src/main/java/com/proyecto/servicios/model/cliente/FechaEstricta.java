package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class FechaEstricta extends JsonDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
        }
        String value = parser.getText();
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("Formato", value, 0);
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return (LocalDate) context.handleWeirdStringValue(LocalDate.class, value, "Se requiere una fecha real YYYY-MM-DD sin hora");
        }
    }
}
