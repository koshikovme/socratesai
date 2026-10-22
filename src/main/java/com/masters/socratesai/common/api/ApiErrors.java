package com.masters.socratesai.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.UUID;

@UtilityClass
public class ApiErrors {
    private static final String REQUEST_ID = ApiErrors.class.getName() + ".requestId";

    public ApiError body(ApiErrorCode code, HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID);
        String id = existing instanceof UUID value ? value.toString() : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, UUID.fromString(id));
        return new ApiError(code.name(), code.getMessage(), id);
    }

    public void write(ApiErrorCode code, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        ApiError error = body(code, request);
        response.setStatus(code.getStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // All values are server-owned enum constants or a generated UUID.
        response.getWriter().write("{\"code\":\"" + error.code() + "\",\"message\":\"" + error.message()
                + "\",\"requestId\":\"" + error.requestId() + "\"}");
    }
}
