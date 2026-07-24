package br.com.primeiroprontuario.web;

import br.com.primeiroprontuario.appointment.AppointmentConflictException;
import br.com.primeiroprontuario.appointment.AppointmentInPastException;
import br.com.primeiroprontuario.appointment.AppointmentNotFoundException;
import br.com.primeiroprontuario.appointment.AppointmentRescheduleConflictException;
import br.com.primeiroprontuario.appointment.AppointmentTransitionConflictException;
import br.com.primeiroprontuario.appointment.AppointmentVersionConflictException;
import br.com.primeiroprontuario.appointment.ScheduleBlockConflictException;
import br.com.primeiroprontuario.appointment.ScheduleBlockNotFoundException;
import br.com.primeiroprontuario.appointment.ScheduleBlockNotFutureException;
import br.com.primeiroprontuario.attachment.AttachmentGoneException;
import br.com.primeiroprontuario.attachment.AttachmentNotFoundException;
import br.com.primeiroprontuario.attachment.AttachmentPatientConflictException;
import br.com.primeiroprontuario.attachment.AttachmentTooLargeException;
import br.com.primeiroprontuario.attachment.InvalidAttachmentRemovalException;
import br.com.primeiroprontuario.attachment.UnsupportedAttachmentTypeException;
import br.com.primeiroprontuario.medicalrecord.AddendumConsultationNotFinalizedException;
import br.com.primeiroprontuario.medicalrecord.ConsultationAppointmentConflictException;
import br.com.primeiroprontuario.medicalrecord.ConsultationFinalizedConflictException;
import br.com.primeiroprontuario.medicalrecord.ConsultationNotFoundException;
import br.com.primeiroprontuario.medicalrecord.ConsultationVersionConflictException;
import br.com.primeiroprontuario.medicalrecord.IncompleteConsultationException;
import br.com.primeiroprontuario.medicalrecord.InvalidAddendumException;
import br.com.primeiroprontuario.patient.DuplicateCpfException;
import br.com.primeiroprontuario.patient.InactivePatientException;
import br.com.primeiroprontuario.patient.PatientNotFoundException;
import br.com.primeiroprontuario.patient.PatientVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ApiErrorHandler {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleInvalidCredentials(AuthenticationException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "urn:problem:invalid-credentials",
                "Falha de autenticação",
                "Credenciais inválidas.",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidFields(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        problem.setProperty("errors", exception.getErrors());
        return problem;
    }

    @ExceptionHandler(IncompleteConsultationException.class)
    ProblemDetail handleIncompleteConsultation(IncompleteConsultationException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        problem.setProperty(
                "errors",
                exception.getMissingFields().stream()
                        .map(field -> new FieldError(field, "é obrigatório"))
                        .toList());
        return problem;
    }

    @ExceptionHandler(InvalidAddendumException.class)
    ProblemDetail handleInvalidAddendum(InvalidAddendumException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        problem.setProperty(
                "errors",
                exception.getMissingFields().stream()
                        .map(field -> new FieldError(field, "é obrigatório"))
                        .toList());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleInvalidBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "O corpo da requisição é inválido.",
                request);
        problem.setProperty("errors", java.util.List.of(new FieldError("body", "JSON inválido")));
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        problem.setProperty("errors", java.util.List.of(new FieldError(exception.getName(), "valor inválido")));
        return problem;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail handleMissingRequestPart(MissingServletRequestPartException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        problem.setProperty(
                "errors", java.util.List.of(new FieldError(exception.getRequestPartName(), "é obrigatório")));
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "urn:problem:resource-not-found",
                "Recurso não encontrado",
                "O recurso solicitado não existe.",
                request);
    }

    @ExceptionHandler(PatientNotFoundException.class)
    ProblemDetail handlePatientNotFound(PatientNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "urn:problem:resource-not-found",
                "Recurso não encontrado",
                "O recurso solicitado não existe.",
                request);
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    ProblemDetail handleAppointmentNotFound(AppointmentNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "urn:problem:resource-not-found",
                "Recurso não encontrado",
                "O recurso solicitado não existe.",
                request);
    }

    @ExceptionHandler(ScheduleBlockNotFoundException.class)
    ProblemDetail handleScheduleBlockNotFound(ScheduleBlockNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "urn:problem:resource-not-found",
                "Recurso não encontrado",
                "O recurso solicitado não existe.",
                request);
    }

    @ExceptionHandler(ConsultationNotFoundException.class)
    ProblemDetail handleConsultationNotFound(ConsultationNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "urn:problem:resource-not-found",
                "Recurso não encontrado",
                "O recurso solicitado não existe.",
                request);
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    ProblemDetail handleAttachmentNotFound(AttachmentNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "urn:problem:resource-not-found",
                "Recurso não encontrado",
                "O recurso solicitado não existe.",
                request);
    }

    @ExceptionHandler(AttachmentGoneException.class)
    ProblemDetail handleAttachmentGone(AttachmentGoneException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.GONE, "urn:problem:resource-gone", "Recurso removido", "O anexo foi removido.", request);
    }

    @ExceptionHandler(InvalidAttachmentRemovalException.class)
    ProblemDetail handleInvalidAttachmentRemoval(
            InvalidAttachmentRemovalException exception, HttpServletRequest request) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:problem:invalid-request",
                "Requisição inválida",
                "Um ou mais campos são inválidos.",
                request);
        problem.setProperty("errors", java.util.List.of(new FieldError("justification", "é obrigatória")));
        return problem;
    }

    @ExceptionHandler(AttachmentPatientConflictException.class)
    ProblemDetail handleAttachmentPatientConflict(
            AttachmentPatientConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "A consulta informada não pertence ao paciente.",
                request);
    }

    @ExceptionHandler(UnsupportedAttachmentTypeException.class)
    ProblemDetail handleUnsupportedAttachmentType(
            UnsupportedAttachmentTypeException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "urn:problem:unsupported-media-type",
                "Tipo de arquivo não permitido",
                "O arquivo não corresponde a um tipo permitido.",
                request);
    }

    @ExceptionHandler({AttachmentTooLargeException.class, MaxUploadSizeExceededException.class})
    ProblemDetail handleAttachmentTooLarge(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "urn:problem:payload-too-large",
                "Arquivo muito grande",
                "O arquivo excede o limite permitido.",
                request);
    }

    @ExceptionHandler(ConsultationVersionConflictException.class)
    ProblemDetail handleConsultationVersionConflict(
            ConsultationVersionConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "A consulta foi alterada desde a versão informada.",
                request);
    }

    @ExceptionHandler(ConsultationAppointmentConflictException.class)
    ProblemDetail handleConsultationAppointmentConflict(
            ConsultationAppointmentConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O agendamento informado não pode ser vinculado à consulta.",
                request);
    }

    @ExceptionHandler(ConsultationFinalizedConflictException.class)
    ProblemDetail handleConsultationFinalizedConflict(
            ConsultationFinalizedConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "Uma consulta finalizada não pode ser alterada.",
                request);
    }

    @ExceptionHandler(AddendumConsultationNotFinalizedException.class)
    ProblemDetail handleAddendumConsultationNotFinalized(
            AddendumConsultationNotFinalizedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "Somente uma consulta finalizada pode receber adendo.",
                request);
    }

    @ExceptionHandler(InactivePatientException.class)
    ProblemDetail handleInactivePatient(InactivePatientException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "urn:problem:conflict", "Conflito", "O paciente não está ativo.", request);
    }

    @ExceptionHandler(AppointmentInPastException.class)
    ProblemDetail handleAppointmentInPast(AppointmentInPastException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O agendamento não pode começar no passado.",
                request);
    }

    @ExceptionHandler(AppointmentConflictException.class)
    ProblemDetail handleAppointmentConflict(AppointmentConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O horário solicitado conflita com a agenda.",
                request);
    }

    @ExceptionHandler(AppointmentRescheduleConflictException.class)
    ProblemDetail handleAppointmentRescheduleConflict(
            AppointmentRescheduleConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O estado atual não permite reagendar o compromisso.",
                request);
    }

    @ExceptionHandler(AppointmentTransitionConflictException.class)
    ProblemDetail handleAppointmentTransitionConflict(
            AppointmentTransitionConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "A transição de estado solicitada não é permitida.",
                request);
    }

    @ExceptionHandler(AppointmentVersionConflictException.class)
    ProblemDetail handleAppointmentVersionConflict(
            AppointmentVersionConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O agendamento foi alterado desde a versão informada.",
                request);
    }

    @ExceptionHandler(ScheduleBlockConflictException.class)
    ProblemDetail handleScheduleBlockConflict(ScheduleBlockConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O horário solicitado conflita com a agenda.",
                request);
    }

    @ExceptionHandler(ScheduleBlockNotFutureException.class)
    ProblemDetail handleScheduleBlockNotFuture(ScheduleBlockNotFutureException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "Somente um bloqueio futuro pode ser criado ou removido.",
                request);
    }

    @ExceptionHandler(DuplicateCpfException.class)
    ProblemDetail handleDuplicateCpf(DuplicateCpfException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "urn:problem:conflict", "Conflito", "CPF já cadastrado.", request);
    }

    @ExceptionHandler(PatientVersionConflictException.class)
    ProblemDetail handlePatientVersionConflict(PatientVersionConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "urn:problem:conflict",
                "Conflito",
                "O paciente foi alterado desde a versão informada.",
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "urn:problem:method-not-allowed",
                "Método não permitido",
                "O método HTTP não é permitido para este recurso.",
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        var correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        LOGGER.error(
                "Unexpected request failure correlationId={} exceptionType={}",
                correlationId,
                exception.getClass().getSimpleName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "urn:problem:internal-error",
                "Erro interno",
                "Não foi possível concluir a requisição.",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return problem;
    }

    private record FieldError(String field, String message) {}
}
