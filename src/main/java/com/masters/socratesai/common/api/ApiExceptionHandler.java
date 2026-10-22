package com.masters.socratesai.common.api;

import com.masters.socratesai.common.exception.BadRequestException;
import com.masters.socratesai.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> application(ApiException ex, HttpServletRequest request) { return error(ex.getCode(), request); }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            BadRequestException.class, org.apache.coyote.BadRequestException.class})
    ResponseEntity<ApiError> invalid(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.INVALID_REQUEST, request); }

    @ExceptionHandler({NotFoundException.class, ChangeSetPersister.NotFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class})
    ResponseEntity<ApiError> missing(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.NOT_FOUND, request); }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.CONFLICT, request); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.FORBIDDEN, request); }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authentication(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.AUTHENTICATION_REQUIRED, request); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.METHOD_NOT_ALLOWED, request); }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> media(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, request); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) { return error(ApiErrorCode.INTERNAL_ERROR, request); }

    private ResponseEntity<ApiError> error(ApiErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(code.getStatus()).body(ApiErrors.body(code, request));
    }
}
