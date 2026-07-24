package br.com.primeiroprontuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@Import(AppointmentApiIntegrationTest.FixedClockConfiguration.class)
@SpringBootTest(
        classes = PrimeiroProntuarioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.doctor.username=doctor",
            "app.doctor.password=valid-test-password",
            "app.time-zone=America/Sao_Paulo",
            "server.servlet.session.cookie.secure=false"
        })
class AppointmentApiIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.4");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBusinessData() {
        jdbc.execute("""
                TRUNCATE TABLE
                    attachment,
                    addendum,
                    consultation,
                    schedule_block,
                    appointment,
                    patient,
                    audit_event
                """);
    }

    @Test
    void activePatientReceivesAScheduledAppointmentWithDerivedEndAndAudit() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "529.982.247-25");

        var created = createAppointment(client, patientId, "2030-01-15T10:00:00", 45);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).isPresent();
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        assertThat(JsonPath.<String>read(created.body(), "$.patientId")).isEqualTo(patientId);
        assertThat(JsonPath.<String>read(created.body(), "$.startsAt")).isEqualTo("2030-01-15T10:00:00-03:00");
        assertThat(JsonPath.<String>read(created.body(), "$.endsAt")).isEqualTo("2030-01-15T10:45:00-03:00");
        assertThat(JsonPath.<Integer>read(created.body(), "$.durationMinutes")).isEqualTo(45);
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("SCHEDULED");

        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.id")).isEqualTo(appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("SCHEDULED");

        var audit = get(client, "/api/v1/audit-events?action=APPOINTMENT_CREATED&size=100");
        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(
                        audit.body(), "$.content[?(@.targetId == '" + appointmentId + "')].action"))
                .containsExactly("APPOINTMENT_CREATED");
    }

    @Test
    void scheduledAppointmentCanBeRescheduledWithItsKnownVersionAndAudit() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "862.883.667-57");
        var created = createAppointment(client, patientId, "2030-01-20T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var rescheduled = mutation(
                client, "PUT", "/api/v1/appointments/" + appointmentId + "/schedule", """
                {
                  "startsAt": "2030-01-20T11:00:00",
                  "durationMinutes": 45,
                  "version": %d
                }
                """.formatted(knownVersion));

        assertThat(rescheduled.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(rescheduled.body(), "$.startsAt")).isEqualTo("2030-01-20T11:00:00-03:00");
        assertThat(JsonPath.<String>read(rescheduled.body(), "$.endsAt")).isEqualTo("2030-01-20T11:45:00-03:00");
        assertThat(JsonPath.<Integer>read(rescheduled.body(), "$.durationMinutes"))
                .isEqualTo(45);
        assertThat(JsonPath.<String>read(rescheduled.body(), "$.status")).isEqualTo("SCHEDULED");
        assertThat(JsonPath.<Integer>read(rescheduled.body(), "$.version")).isEqualTo(knownVersion + 1);

        var audit = get(client, "/api/v1/audit-events?action=APPOINTMENT_RESCHEDULED&size=100");
        assertThat(JsonPath.<List<String>>read(
                        audit.body(), "$.content[?(@.targetId == '" + appointmentId + "')].action"))
                .containsExactly("APPOINTMENT_RESCHEDULED");
    }

    @Test
    void confirmedAppointmentCanAlsoBeRescheduled() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.001-08");
        var created = createAppointment(client, patientId, "2030-02-01T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var initialVersion = JsonPath.<Integer>read(created.body(), "$.version");
        var confirmed = changeAppointmentStatus(client, appointmentId, "CONFIRMED", initialVersion);

        var rescheduled = rescheduleAppointment(
                client, appointmentId, "2030-02-01T11:00:00", 30, JsonPath.read(confirmed.body(), "$.version"));

        assertThat(rescheduled.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(rescheduled.body(), "$.status")).isEqualTo("CONFIRMED");
        assertThat(JsonPath.<String>read(rescheduled.body(), "$.startsAt")).isEqualTo("2030-02-01T11:00:00-03:00");
    }

    @Test
    void conflictingRescheduleReturnsConflictWithoutChangingTheOriginalOrAuditing() throws Exception {
        var client = authenticatedClient();
        var firstPatientId = createPatient(client, "100.000.002-80");
        var secondPatientId = createPatient(client, "100.000.003-61");
        assertThat(createAppointment(client, firstPatientId, "2030-02-02T10:00:00", 30)
                        .statusCode())
                .isEqualTo(201);
        var original = createAppointment(client, secondPatientId, "2030-02-02T11:00:00", 30);
        var appointmentId = JsonPath.<String>read(original.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(original.body(), "$.version");

        var conflict = rescheduleAppointment(client, appointmentId, "2030-02-02T10:15:00", 30, knownVersion);

        assertThat(conflict.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-02T11:00:00-03:00");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
        var audit = get(client, "/api/v1/audit-events?action=APPOINTMENT_RESCHEDULED&size=100");
        assertThat(JsonPath.<List<String>>read(
                        audit.body(), "$.content[?(@.targetId == '" + appointmentId + "')].action"))
                .isEmpty();
    }

    @Test
    void statusEndpointAppliesAnAllowedTransitionAndAuditsIt() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.004-42");
        var created = createAppointment(client, patientId, "2030-02-03T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var changed = changeAppointmentStatus(client, appointmentId, "CONFIRMED", knownVersion);

        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(changed.body(), "$.status")).isEqualTo("CONFIRMED");
        assertThat(JsonPath.<Integer>read(changed.body(), "$.version")).isEqualTo(knownVersion + 1);
        var audit = get(client, "/api/v1/audit-events?action=APPOINTMENT_STATUS_CHANGED&size=100");
        assertThat(JsonPath.<List<String>>read(
                        audit.body(), "$.content[?(@.targetId == '" + appointmentId + "')].action"))
                .containsExactly("APPOINTMENT_STATUS_CHANGED");
    }

    @Test
    void statusEndpointRejectsATransitionOutsideRn020() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.017-67");
        var created = createAppointment(client, patientId, "2030-02-03T12:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var rejected = changeAppointmentStatus(client, appointmentId, "SCHEDULED", knownVersion);

        assertThat(rejected.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("SCHEDULED");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
    }

    @Test
    void terminalAppointmentCannotBeReopenedOrRescheduled() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.005-23");
        var created = createAppointment(client, patientId, "2030-02-04T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var initialVersion = JsonPath.<Integer>read(created.body(), "$.version");
        var completed = changeAppointmentStatus(client, appointmentId, "COMPLETED", initialVersion);
        var terminalVersion = JsonPath.<Integer>read(completed.body(), "$.version");

        var reopen = changeAppointmentStatus(client, appointmentId, "CONFIRMED", terminalVersion);
        var reschedule = rescheduleAppointment(client, appointmentId, "2030-02-04T11:00:00", 30, terminalVersion);

        assertThat(reopen.statusCode()).isEqualTo(409);
        assertThat(reschedule.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-04T10:00:00-03:00");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(terminalVersion);
    }

    @Test
    void twoMutationsFromTheSameKnownVersionDoNotOverwriteEachOther() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.006-04");
        var created = createAppointment(client, patientId, "2030-02-05T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var first = rescheduleAppointment(client, appointmentId, "2030-02-05T11:00:00", 45, knownVersion);
        var stale = changeAppointmentStatus(client, appointmentId, "CANCELLED", knownVersion);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(stale.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("SCHEDULED");
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-05T11:00:00-03:00");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion + 1);
    }

    @Test
    void appointmentMutationsRequireTheKnownVersion() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.018-48");
        var created = createAppointment(client, patientId, "2030-02-05T13:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");

        var scheduleWithoutVersion =
                mutation(client, "PUT", "/api/v1/appointments/" + appointmentId + "/schedule", """
                {
                  "startsAt": "2030-02-05T14:00:00",
                  "durationMinutes": 30
                }
                """);
        var statusWithoutVersion = mutation(client, "PATCH", "/api/v1/appointments/" + appointmentId + "/status", """
                        {"status":"CONFIRMED"}
                        """);

        assertMissingVersion(scheduleWithoutVersion);
        assertMissingVersion(statusWithoutVersion);
    }

    @Test
    void cancellationReleasesTheIntervalForANewAppointment() throws Exception {
        var client = authenticatedClient();
        var firstPatientId = createPatient(client, "100.000.007-95");
        var secondPatientId = createPatient(client, "100.000.008-76");
        var created = createAppointment(client, firstPatientId, "2030-02-06T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");

        var cancelled =
                changeAppointmentStatus(client, appointmentId, "CANCELLED", JsonPath.read(created.body(), "$.version"));
        var replacement = createAppointment(client, secondPatientId, "2030-02-06T10:00:00", 30);

        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(replacement.statusCode()).isEqualTo(201);
    }

    @Test
    void rescheduleRejectsAnInactivePatientAndPreservesTheAppointment() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.009-57");
        var created = createAppointment(client, patientId, "2030-02-07T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        inactivatePatient(client, patientId);

        var response = rescheduleAppointment(client, appointmentId, "2030-02-07T11:00:00", 30, knownVersion);

        assertThat(response.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-07T10:00:00-03:00");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
    }

    @Test
    void rescheduleRejectsUnsupportedDuration() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.010-90");
        var created = createAppointment(client, patientId, "2030-02-08T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var unsupported = rescheduleAppointment(client, appointmentId, "2030-02-08T11:00:00", 20, knownVersion);

        assertThat(unsupported.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(unsupported.body(), "$.errors[*].field"))
                .containsExactly("durationMinutes");
    }

    @Test
    void rescheduleRejectsAStartInThePastAndPreservesTheAppointment() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.016-86");
        var created = createAppointment(client, patientId, "2030-02-08T12:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");

        var past = rescheduleAppointment(client, appointmentId, "2030-01-15T08:59:59", 30, knownVersion);

        assertThat(past.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-08T12:00:00-03:00");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
    }

    @Test
    void rescheduleRejectsAnOverlappingScheduleBlock() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.011-71");
        var created = createAppointment(client, patientId, "2030-02-09T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        var block = mutation(client, "POST", "/api/v1/schedule-blocks", """
                {
                  "startsAt": "2030-02-09T11:00:00",
                  "endsAt": "2030-02-09T11:30:00",
                  "reason": "Compromisso externo"
                }
                """);
        assertThat(block.statusCode()).isEqualTo(201);

        var response = rescheduleAppointment(client, appointmentId, "2030-02-09T11:15:00", 30, knownVersion);

        assertThat(response.statusCode()).isEqualTo(409);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-09T10:00:00-03:00");
    }

    @Test
    void concurrentRescheduleAndCreationCannotOccupyTheSameInterval() throws Exception {
        var rescheduleClient = authenticatedClient();
        var creationClient = authenticatedClient();
        var rescheduledPatientId = createPatient(rescheduleClient, "100.000.012-52");
        var newPatientId = createPatient(creationClient, "100.000.013-33");
        var original = createAppointment(rescheduleClient, rescheduledPatientId, "2030-02-10T09:00:00", 30);
        var appointmentId = JsonPath.<String>read(original.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(original.body(), "$.version");
        var rescheduleCsrf = csrf(rescheduleClient);
        var creationCsrf = csrf(creationClient);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        HttpResponse<String> rescheduleResponse;
        HttpResponse<String> creationResponse;
        try {
            var rescheduleFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        rescheduleClient,
                        "PUT",
                        "/api/v1/appointments/" + appointmentId + "/schedule",
                        rescheduleJson("2030-02-10T10:00:00", 30, knownVersion),
                        rescheduleCsrf.headerName(),
                        rescheduleCsrf.token());
            });
            var creationFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return send(
                        creationClient,
                        "POST",
                        "/api/v1/appointments",
                        appointmentJson(newPatientId, "2030-02-10T10:00:00", 30),
                        creationCsrf.headerName(),
                        creationCsrf.token());
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            rescheduleResponse = rescheduleFuture.get(10, TimeUnit.SECONDS);
            creationResponse = creationFuture.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        var statuses = List.of(rescheduleResponse.statusCode(), creationResponse.statusCode());
        assertThat(statuses).contains(409);
        assertThat(statuses).anySatisfy(status -> assertThat(status).isIn(200, 201));
        var occupying = get(rescheduleClient, "/api/v1/appointments?from=2030-02-10T10:00:00&to=2030-02-10T10:30:00");
        assertThat(JsonPath.<List<String>>read(occupying.body(), "$.content[*].id"))
                .hasSize(1);
    }

    @Test
    void rescheduleAndItsAuditRollBackTogether() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.014-14");
        var created = createAppointment(client, patientId, "2030-02-11T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        jdbc.execute("""
                ALTER TABLE audit_event
                ADD CONSTRAINT audit_event_reject_appointment_rescheduled
                CHECK (action <> 'APPOINTMENT_RESCHEDULED')
                NOT VALID
                """);

        HttpResponse<String> failed;
        try {
            failed = rescheduleAppointment(client, appointmentId, "2030-02-11T11:00:00", 30, knownVersion);
        } finally {
            jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT audit_event_reject_appointment_rescheduled");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(JsonPath.<String>read(failed.body(), "$.type")).isEqualTo("urn:problem:internal-error");
        assertThat(JsonPath.<String>read(failed.body(), "$.detail"))
                .isEqualTo("Não foi possível concluir a requisição.");
        var correlationId = JsonPath.<String>read(failed.body(), "$.correlationId");
        assertThat(correlationId).matches("[0-9a-f-]{36}");
        assertThat(failed.headers().firstValue("X-Correlation-ID")).contains(correlationId);
        assertThat(failed.body())
                .doesNotContain("audit_event", "constraint", "SQLException", "PostgreSQL", "stack", "exception");
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.startsAt")).isEqualTo("2030-02-11T10:00:00-03:00");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
    }

    @Test
    void statusChangeAndItsAuditRollBackTogether() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.015-03");
        var created = createAppointment(client, patientId, "2030-02-12T10:00:00", 30);
        var appointmentId = JsonPath.<String>read(created.body(), "$.id");
        var knownVersion = JsonPath.<Integer>read(created.body(), "$.version");
        jdbc.execute("""
                ALTER TABLE audit_event
                ADD CONSTRAINT audit_event_reject_appointment_status_changed
                CHECK (action <> 'APPOINTMENT_STATUS_CHANGED')
                NOT VALID
                """);

        HttpResponse<String> failed;
        try {
            failed = changeAppointmentStatus(client, appointmentId, "CONFIRMED", knownVersion);
        } finally {
            jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT audit_event_reject_appointment_status_changed");
        }

        assertThat(failed.statusCode()).isEqualTo(500);
        var retrieved = get(client, "/api/v1/appointments/" + appointmentId);
        assertThat(JsonPath.<String>read(retrieved.body(), "$.status")).isEqualTo("SCHEDULED");
        assertThat(JsonPath.<Integer>read(retrieved.body(), "$.version")).isEqualTo(knownVersion);
    }

    @Test
    void activeAppointmentBlocksAnOverlappingInterval() throws Exception {
        var client = authenticatedClient();
        var firstPatientId = createPatient(client, "280.012.389-38");
        var secondPatientId = createPatient(client, "100.000.019-29");
        assertThat(createAppointment(client, firstPatientId, "2030-01-16T10:00:00", 30)
                        .statusCode())
                .isEqualTo(201);

        var response = createAppointment(client, secondPatientId, "2030-01-16T10:15:00", 30);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(response.body()).doesNotContain(firstPatientId).doesNotContain(secondPatientId);
    }

    @Test
    void adjacentAppointmentsAreAccepted() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "100.000.020-62");
        assertThat(createAppointment(client, patientId, "2030-01-17T10:00:00", 30)
                        .statusCode())
                .isEqualTo(201);

        var response = createAppointment(client, patientId, "2030-01-17T10:30:00", 15);

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void agendaFiltersByHalfOpenPeriodStatusAndPatient() throws Exception {
        var client = authenticatedClient();
        var requestedPatientId = createPatient(client, "935.411.347-80");
        var otherPatientId = createPatient(client, "390.533.447-05");
        var overlapping = createAppointment(client, requestedPatientId, "2030-01-15T09:45:00", 30);
        createAppointment(client, requestedPatientId, "2030-01-15T10:15:00", 15);
        createAppointment(client, otherPatientId, "2030-01-15T10:00:00", 15);
        var expectedId = JsonPath.<String>read(overlapping.body(), "$.id");

        var agenda = get(
                client,
                "/api/v1/appointments?from=2030-01-15T10:00:00"
                        + "&to=2030-01-15T10:15:00&status=SCHEDULED&patientId="
                        + requestedPatientId);

        assertThat(agenda.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(agenda.body(), "$.content[*].id"))
                .containsExactly(expectedId);
        assertThat(JsonPath.<Integer>read(agenda.body(), "$.page")).isZero();
        assertThat(JsonPath.<Integer>read(agenda.body(), "$.size")).isEqualTo(20);
        assertThat(JsonPath.<Integer>read(agenda.body(), "$.totalElements")).isEqualTo(1);
    }

    @Test
    void unsupportedDurationReturnsAFieldError() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "111.444.777-35");

        var response = createAppointment(client, patientId, "2030-01-15T10:00:00", 20);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.errors[*].field"))
                .containsExactly("durationMinutes");
    }

    @Test
    void appointmentInThePastReturnsASafeConflict() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "987.654.321-00");

        var response = createAppointment(client, patientId, "2030-01-15T08:59:59", 30);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:conflict");
        assertThat(response.body()).doesNotContain(patientId).doesNotContain("2030-01-15T08:59:59");
    }

    @Test
    void inactivePatientDoesNotReceiveAnAppointment() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "168.995.350-09");
        inactivatePatient(client, patientId);

        var response = createAppointment(client, patientId, "2030-01-15T10:00:00", 30);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo("O paciente não está ativo.");
    }

    @Test
    void unknownPatientDoesNotReceiveAnAppointment() throws Exception {
        var client = authenticatedClient();

        var response = createAppointment(client, UUID.randomUUID().toString(), "2030-01-15T10:00:00", 30);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:resource-not-found");
    }

    @Test
    void agendaRequiresAValidPeriod() throws Exception {
        var client = authenticatedClient();

        var missingBoundary = get(client, "/api/v1/appointments?from=2030-01-15T10:00:00");
        var reversed = get(client, "/api/v1/appointments?from=2030-01-15T11:00:00&to=2030-01-15T10:00:00");

        assertThat(missingBoundary.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(missingBoundary.body(), "$.errors[*].field"))
                .containsExactly("to");
        assertThat(reversed.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<List<String>>read(reversed.body(), "$.errors[*].field"))
                .containsExactly("to");
    }

    @Test
    void agendaPaginationHasDeterministicStartAndIdOrder() throws Exception {
        var client = authenticatedClient();
        var patientId = createPatient(client, "153.509.460-56");
        var later = createAppointment(client, patientId, "2030-01-15T11:00:00", 15);
        var sameStartFirst = createAppointment(client, patientId, "2030-01-15T10:00:00", 15);
        changeAppointmentStatus(
                client,
                JsonPath.read(sameStartFirst.body(), "$.id"),
                "CANCELLED",
                JsonPath.read(sameStartFirst.body(), "$.version"));
        var sameStartSecond = createAppointment(client, patientId, "2030-01-15T10:00:00", 15);
        var sameStartIds = new ArrayList<>(List.of(
                JsonPath.<String>read(sameStartFirst.body(), "$.id"),
                JsonPath.<String>read(sameStartSecond.body(), "$.id")));
        sameStartIds.sort(Comparator.naturalOrder());
        var expectedIds =
                List.of(sameStartIds.get(0), sameStartIds.get(1), JsonPath.<String>read(later.body(), "$.id"));
        var basePath =
                "/api/v1/appointments?from=2030-01-15T09:00:00" + "&to=2030-01-15T12:00:00&patientId=" + patientId;

        var firstRead = get(client, basePath + "&size=100");
        var secondRead = get(client, basePath + "&size=100");
        var firstPage = get(client, basePath + "&size=1&page=0");
        var secondPage = get(client, basePath + "&size=1&page=1");

        assertThat(JsonPath.<List<String>>read(firstRead.body(), "$.content[*].id"))
                .containsExactlyElementsOf(expectedIds);
        assertThat(JsonPath.<List<String>>read(secondRead.body(), "$.content[*].id"))
                .containsExactlyElementsOf(expectedIds);
        assertThat(JsonPath.<List<String>>read(firstPage.body(), "$.content[*].id"))
                .containsExactly(expectedIds.get(0));
        assertThat(JsonPath.<List<String>>read(secondPage.body(), "$.content[*].id"))
                .containsExactly(expectedIds.get(1));
    }

    private HttpClient authenticatedClient() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(cookies).build();
        var login = send(client, "POST", "/api/v1/auth/login", """
                {"username":"doctor","password":"valid-test-password"}
                """, null, null);
        assertThat(login.statusCode()).isEqualTo(200);
        return client;
    }

    private String createPatient(HttpClient client, String cpf) throws Exception {
        var created = mutation(client, "POST", "/api/v1/patients", """
                {
                  "fullName": "Ana Souza",
                  "motherName": "Maria Souza",
                  "birthDate": "1990-05-20",
                  "cpf": "%s",
                  "phone": "(11) 99999-1234"
                }
                """.formatted(cpf));
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private HttpResponse<String> createAppointment(
            HttpClient client, String patientId, String startsAt, int durationMinutes) throws Exception {
        return mutation(client, "POST", "/api/v1/appointments", appointmentJson(patientId, startsAt, durationMinutes));
    }

    private String appointmentJson(String patientId, String startsAt, int durationMinutes) {
        return """
                {
                  "patientId": "%s",
                  "startsAt": "%s",
                  "durationMinutes": %d
                }
                """.formatted(patientId, startsAt, durationMinutes);
    }

    private HttpResponse<String> rescheduleAppointment(
            HttpClient client, String appointmentId, String startsAt, int durationMinutes, int version)
            throws Exception {
        return mutation(
                client,
                "PUT",
                "/api/v1/appointments/" + appointmentId + "/schedule",
                rescheduleJson(startsAt, durationMinutes, version));
    }

    private String rescheduleJson(String startsAt, int durationMinutes, int version) {
        return """
                {
                  "startsAt": "%s",
                  "durationMinutes": %d,
                  "version": %d
                }
                """.formatted(startsAt, durationMinutes, version);
    }

    private HttpResponse<String> changeAppointmentStatus(
            HttpClient client, String appointmentId, String status, int version) throws Exception {
        return mutation(
                client, "PATCH", "/api/v1/appointments/" + appointmentId + "/status", """
                {"status":"%s","version":%d}
                """.formatted(status, version));
    }

    private Csrf csrf(HttpClient client) throws Exception {
        var response = get(client, "/api/v1/auth/csrf");
        return new Csrf(JsonPath.read(response.body(), "$.headerName"), JsonPath.read(response.body(), "$.token"));
    }

    private void inactivatePatient(HttpClient client, String patientId) throws Exception {
        var patient = get(client, "/api/v1/patients/" + patientId);
        var version = JsonPath.<Integer>read(patient.body(), "$.version");
        var response = mutation(client, "PATCH", "/api/v1/patients/" + patientId + "/status", """
                {"status":"INACTIVE","version":%d}
                """.formatted(version));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> mutation(HttpClient client, String method, String path, String body) throws Exception {
        var csrf = get(client, "/api/v1/auth/csrf");
        return send(
                client,
                method,
                path,
                body,
                JsonPath.read(csrf.body(), "$.headerName"),
                JsonPath.read(csrf.body(), "$.token"));
    }

    private HttpResponse<String> send(
            HttpClient client, String method, String path, String body, String csrfHeader, String csrfToken)
            throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(apiUri(path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (csrfHeader != null) {
            request.header(csrfHeader, csrfToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder().uri(apiUri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertMissingVersion(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.type")).isEqualTo("urn:problem:invalid-request");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.errors[*].field"))
                .containsExactly("version");
    }

    private URI apiUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record Csrf(String headerName, String token) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock appointmentTestClock() {
            return Clock.fixed(Instant.parse("2030-01-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
