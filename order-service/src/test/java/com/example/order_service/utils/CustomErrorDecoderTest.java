package com.example.order_service.utils;

import com.example.order_service.exception.NotFoundException;
import com.example.order_service.exception.ServiceUnavailableException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomErrorDecoderTest {
    private final CustomErrorDecoder decoder = new CustomErrorDecoder();

    @Test
    void conflictKeepsTheOtherServicesMessage() {
        Exception e = decoder.decode("reserve", response(409, "{\"status\":409,\"message\":\"Not enough stock for Mug: 2 left\",\"data\":null}"));

        assertThat(e).isInstanceOf(IllegalStateException.class).hasMessage("Not enough stock for Mug: 2 left");
    }

    @Test
    void statusesMapToTheirMeaning() {
        assertThat(decoder.decode("m", response(404, "{\"message\":\"Product not found\"}"))).isInstanceOf(NotFoundException.class);
        assertThat(decoder.decode("m", response(400, "not json"))).isInstanceOf(IllegalArgumentException.class).hasMessage("Bad request");
        assertThat(decoder.decode("m", response(500, ""))).isInstanceOf(ServiceUnavailableException.class);
    }

    private static Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder().status(status).request(request).headers(Map.of())
                .body(body, StandardCharsets.UTF_8).build();
    }
}
