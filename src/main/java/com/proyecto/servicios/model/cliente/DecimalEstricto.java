package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.math.BigDecimal;

public class DecimalEstricto extends JsonDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.currentToken().isNumeric()) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        return parser.getDecimalValue();
    }
}
