package com.acme.blueprint.shared.web;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.acme.blueprint.shared.BusinessException;
import com.acme.blueprint.shared.BusinessRuleViolationException;
import com.acme.blueprint.shared.ConflictException;
import com.acme.blueprint.shared.InvalidRequestException;
import com.acme.blueprint.shared.PreconditionFailedException;
import com.acme.blueprint.shared.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Turns every error — business, framework or unexpected — into an RFC 9457
 * problem detail that carries a stable {@code code}. Field- and
 * parameter-level problems also list the offenders under {@code errors}, so a
 * form can highlight them.
 *
 * <p>When Spring Security is added, map {@code AccessDeniedException} (403)
 * and {@code AuthenticationException} (401) here explicitly: they carry no
 * {@code @ResponseStatus} and would otherwise reach the 500 fallback.
 */
@RestControllerAdvice
class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

    private static final String CODE = "code";
    private static final String ERRORS = "errors";
    private static final String VERSION_MISMATCH = "request.version-mismatch";

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusinessException(BusinessException exception) {
        return problem(statusOf(exception), exception.getMessage(), exception.code());
    }

    /**
     * Another transaction committed a change to the same aggregate first. For a
     * request guarded by {@code If-Match} that is the same situation as a stale
     * tag, so it gets the same 412 and code; without a precondition it is a
     * plain conflict.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception, WebRequest request) {
        String detail = "The resource was changed concurrently; reload it and retry";
        return request.getHeader(HttpHeaders.IF_MATCH) != null
                ? problem(HttpStatus.PRECONDITION_FAILED, detail, VERSION_MISMATCH)
                : problem(HttpStatus.CONFLICT, detail, "request.concurrent-modification");
    }

    /**
     * Last resort. Exceptions annotated with {@code @ResponseStatus} keep their
     * status; anything else is logged and answered with a generic 500 that
     * never leaks the cause.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        ResponseStatus declared = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
        if (declared != null) {
            HttpStatus status = declared.code();
            String reason = declared.reason().isEmpty() ? status.getReasonPhrase() : declared.reason();
            return problem(status, reason, codeFor(status));
        }
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "server.internal-error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setProperty(CODE, "request.invalid");
        problem.setProperty(ERRORS, exception.getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Constraint violations on {@code @RequestParam}s and headers: same shape as body validation. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setProperty(CODE, "request.invalid");
        problem.setProperty(ERRORS, exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** A missing {@code If-Match} is not a malformed request but an unconditional one: 428. */
    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception instanceof MissingRequestHeaderException missing
                && HttpHeaders.IF_MATCH.equalsIgnoreCase(missing.getHeaderName())) {
            ProblemDetail problem = problem(HttpStatus.PRECONDITION_REQUIRED,
                    "This request must be conditional: send If-Match with the ETag you last read",
                    "request.precondition-required");
            return handleExceptionInternal(exception, problem, headers, HttpStatus.PRECONDITION_REQUIRED, request);
        }
        return super.handleServletRequestBindingException(exception, headers, status, request);
    }

    /** A JSON value of the wrong type — typically an enum in the wrong case — is reported per field. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request body could not be read");
        if (exception.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            problem.setProperty(CODE, "request.invalid");
            problem.setProperty(ERRORS, List.of(new FieldViolation(fieldPath(mismatch), expectation(mismatch))));
        } else {
            problem.setProperty(CODE, "request.malformed-body");
        }
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    /** Every framework-produced problem passes through here; give it a code if it has none. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object resolved = body == null && exception instanceof ErrorResponse error ? error.getBody() : body;
        if (resolved instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            problem.setProperty(CODE, defaultCode(exception, status));
        }
        return super.handleExceptionInternal(exception, resolved, headers, status, request);
    }

    // Named pattern bindings, never `_`: generated projects may target Java 21,
    // and unnamed patterns are Java 22+.
    private static HttpStatus statusOf(BusinessException exception) {
        return switch (exception) {
            case InvalidRequestException e -> HttpStatus.BAD_REQUEST;
            case ResourceNotFoundException e -> HttpStatus.NOT_FOUND;
            case ConflictException e -> HttpStatus.CONFLICT;
            case PreconditionFailedException e -> HttpStatus.PRECONDITION_FAILED;
            case BusinessRuleViolationException e -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    private static String defaultCode(Exception exception, HttpStatusCode status) {
        return switch (exception) {
            case HttpMessageNotReadableException e -> "request.malformed-body";
            case TypeMismatchException e -> "request.invalid-parameter";
            case MissingRequestValueException e -> "request.missing-parameter";
            case MethodArgumentNotValidException e -> "request.invalid";
            case HandlerMethodValidationException e -> "request.invalid";
            case NoResourceFoundException e -> "request.not-found";
            case NoHandlerFoundException e -> "request.not-found";
            case HttpRequestMethodNotSupportedException e -> "request.method-not-allowed";
            case HttpMediaTypeNotSupportedException e -> "request.unsupported-media-type";
            case HttpMediaTypeNotAcceptableException e -> "request.not-acceptable";
            default -> codeFor(status);
        };
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "request.unauthenticated";
            case 403 -> "request.forbidden";
            case 404 -> "request.not-found";
            case 409 -> "request.conflict";
            default -> status.is5xxServerError() ? "server.internal-error" : "request.invalid";
        };
    }

    private static String fieldPath(MismatchedInputException mismatch) {
        return mismatch.getPath().stream()
                .map(reference -> reference.getPropertyName() != null
                        ? reference.getPropertyName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String expectation(MismatchedInputException mismatch) {
        Class<?> target = mismatch.getTargetType();
        return target != null && target.isEnum()
                ? "must be one of " + Arrays.toString(target.getEnumConstants())
                : "has an invalid value";
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }

    record FieldViolation(String field, String message) {}
}
