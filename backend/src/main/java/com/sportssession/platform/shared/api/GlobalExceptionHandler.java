package com.sportssession.platform.shared.api;

import com.sportssession.platform.player.domain.DuplicatePlayerSportProfileException;
import com.sportssession.platform.player.domain.PlayerNotFoundException;
import com.sportssession.platform.session.domain.DuplicateSessionCourtException;
import com.sportssession.platform.session.domain.DuplicateSessionParticipantException;
import com.sportssession.platform.session.domain.InvalidParticipantStateException;
import com.sportssession.platform.session.domain.InvalidSessionCourtStateException;
import com.sportssession.platform.session.domain.InvalidSessionStateException;
import com.sportssession.platform.session.domain.InvalidSessionTimeRangeException;
import com.sportssession.platform.session.domain.SessionCourtNotFoundException;
import com.sportssession.platform.session.domain.SessionNotFoundException;
import com.sportssession.platform.session.domain.SessionParticipantNotFoundException;
import com.sportssession.platform.session.domain.SessionResourceConflictException;
import com.sportssession.platform.venue.domain.CourtNotFoundException;
import com.sportssession.platform.venue.domain.DuplicateCourtNameException;
import com.sportssession.platform.venue.domain.InactiveVenueException;
import com.sportssession.platform.venue.domain.VenueNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock = Clock.systemUTC();

    @ExceptionHandler(PlayerNotFoundException.class)
    ResponseEntity<ApiError> handlePlayerNotFound(
            PlayerNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({VenueNotFoundException.class, CourtNotFoundException.class})
    ResponseEntity<ApiError> handleVenueResourceNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            SessionNotFoundException.class,
            SessionParticipantNotFoundException.class,
            SessionCourtNotFoundException.class
    })
    ResponseEntity<ApiError> handleSessionResourceNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidSessionTimeRangeException.class)
    ResponseEntity<ApiError> handleInvalidSessionTimeRange(
            InvalidSessionTimeRangeException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return error(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Malformed request or unsupported sport/skillLevel value",
                request,
                Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Invalid value for " + exception.getName(),
                request,
                Map.of());
    }

    @ExceptionHandler(DuplicatePlayerSportProfileException.class)
    ResponseEntity<ApiError> handleDuplicateProfile(
            DuplicatePlayerSportProfileException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({DuplicateCourtNameException.class, InactiveVenueException.class})
    ResponseEntity<ApiError> handleVenueConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            InvalidSessionStateException.class,
            InvalidParticipantStateException.class,
            InvalidSessionCourtStateException.class,
            DuplicateSessionParticipantException.class,
            DuplicateSessionCourtException.class,
            SessionResourceConflictException.class,
            ObjectOptimisticLockingFailureException.class
    })
    ResponseEntity<ApiError> handleSessionConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.CONFLICT,
                "The request conflicts with a database constraint",
                request,
                Map.of());
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ApiError body = new ApiError(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
