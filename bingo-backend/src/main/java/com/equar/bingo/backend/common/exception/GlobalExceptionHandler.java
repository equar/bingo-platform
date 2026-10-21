package com.equar.bingo.backend.common.exception;

import com.equar.bingo.backend.common.api.ApiErrorResponse;
import com.equar.bingo.backend.common.api.ValidationErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(ApiException.class)
        @ApiResponse(responseCode = "400", description = "Request rejected",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        @ApiResponse(responseCode = "404", description = "Resource not found",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        @ApiResponse(responseCode = "409", description = "Request conflicts with current state",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
                return ResponseEntity.status(exception.getStatus())
                                .body(error(exception.getStatus(), exception.getCode(), exception.getMessage(), request,
                                                List.of()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        @ApiResponse(responseCode = "400", description = "Request validation failed",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                        HttpServletRequest request) {
                List<ValidationErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                                .map(fieldError -> new ValidationErrorDetail(
                                                fieldError.getField(),
                                                Objects.requireNonNullElse(fieldError.getDefaultMessage(),
                                                                "Invalid value")))
                                .collect(Collectors.toList());
                details.addAll(exception.getBindingResult().getGlobalErrors().stream()
                                .map(error -> new ValidationErrorDetail(
                                                error.getObjectName(),
                                                Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")))
                                .collect(Collectors.toList()));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                                                request, details));
        }

        @ExceptionHandler(BindException.class)
        @ApiResponse(responseCode = "400", description = "Request binding failed",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleBinding(BindException exception, HttpServletRequest request) {
                List<ValidationErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                                .map(fieldError -> new ValidationErrorDetail(
                                                fieldError.getField(),
                                                Objects.requireNonNullElse(fieldError.getDefaultMessage(),
                                                                "Invalid value")))
                                .collect(Collectors.toList());
                return badRequest("VALIDATION_ERROR", "Request validation failed", request, details);
        }

        @ExceptionHandler(HandlerMethodValidationException.class)
        @ApiResponse(responseCode = "400", description = "Request validation failed",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException exception,
                        HttpServletRequest request) {
                return badRequest("VALIDATION_ERROR", "Request validation failed", request, List.of());
        }

        @ExceptionHandler(ConstraintViolationException.class)
        @ApiResponse(responseCode = "400", description = "Request validation failed",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                        HttpServletRequest request) {
                List<ValidationErrorDetail> details = exception.getConstraintViolations().stream()
                                .map(violation -> new ValidationErrorDetail(violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                                .collect(Collectors.toList());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                                                request, details));
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        @ApiResponse(responseCode = "400", description = "Required request header is missing",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException exception,
                        HttpServletRequest request) {
                return badRequest("MISSING_REQUIRED_HEADER", "A required request header is missing", request,
                                List.of(new ValidationErrorDetail(exception.getHeaderName(), "Header is required")));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        @ApiResponse(responseCode = "400", description = "Request body is missing or malformed",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleUnreadableRequestBody(HttpMessageNotReadableException exception,
                        HttpServletRequest request) {
                return badRequest("INVALID_REQUEST_BODY", "Request body is missing or malformed", request, List.of());
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        @ApiResponse(responseCode = "400", description = "Required request parameter is missing",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
                        MissingServletRequestParameterException exception, HttpServletRequest request) {
                return badRequest("MISSING_REQUIRED_PARAMETER", "A required request parameter is missing", request,
                                List.of(new ValidationErrorDetail(exception.getParameterName(), "Parameter is required")));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        @ApiResponse(responseCode = "400", description = "Request parameter has an invalid value",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception,
                        HttpServletRequest request) {
                return badRequest("INVALID_REQUEST_PARAMETER", "A request parameter has an invalid value", request,
                                List.of(new ValidationErrorDetail(exception.getName(), "Invalid value")));
        }

        @ExceptionHandler(BadCredentialsException.class)
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException exception,
                        HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(error(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "Invalid credentials",
                                                request, List.of()));
        }

        @ExceptionHandler(Exception.class)
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
                        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        public ResponseEntity<ApiErrorResponse> handleFallback(Exception exception, HttpServletRequest request) {
                log.error("Unhandled request failure for {} {} (correlationId={})", request.getMethod(),
                                request.getRequestURI(),
                                com.equar.bingo.backend.common.api.ApiErrorResponseWriter.resolveCorrelationId(request),
                                exception);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                                                "An unexpected error occurred", request,
                                                List.of()));
        }

        private ResponseEntity<ApiErrorResponse> badRequest(String code,
                        String message,
                        HttpServletRequest request,
                        List<ValidationErrorDetail> details) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(error(HttpStatus.BAD_REQUEST, code, message, request, details));
        }

        private ApiErrorResponse error(HttpStatus status,
                        String code,
                        String message,
                        HttpServletRequest request,
                        List<ValidationErrorDetail> details) {
                return new ApiErrorResponse(
                                Instant.now(),
                                status.value(),
                                status.getReasonPhrase(),
                                code,
                                message,
                                request.getRequestURI(),
                                com.equar.bingo.backend.common.api.ApiErrorResponseWriter.resolveCorrelationId(request),
                                details);
        }
}