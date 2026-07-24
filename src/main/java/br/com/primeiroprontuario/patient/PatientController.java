package br.com.primeiroprontuario.patient;

import br.com.primeiroprontuario.auth.DoctorPrincipal;
import br.com.primeiroprontuario.web.ApiPagination;
import br.com.primeiroprontuario.web.CorrelationIdFilter;
import br.com.primeiroprontuario.web.InvalidRequestException;
import br.com.primeiroprontuario.web.InvalidRequestException.InvalidField;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
class PatientController {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "fullName", "motherName", "birthDate", "cpf", "phone", "email", "status");

    private final PatientService patientService;

    PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    ResponseEntity<PatientResponse> create(
            @Valid @RequestBody CreatePatientRequest patientRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        var patient = patientService.create(
                patientRequest.fullName(),
                patientRequest.motherName(),
                patientRequest.birthDate(),
                parseCpf(patientRequest.cpf()),
                patientRequest.phone(),
                patientRequest.email(),
                patientRequest.address(),
                patientRequest.emergencyContact(),
                patientRequest.insurance(),
                patientRequest.allergies(),
                patientRequest.notes(),
                doctor.id(),
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        var location = URI.create("/api/v1/patients/" + patient.getId());
        return ResponseEntity.created(location).body(PatientResponse.from(patient));
    }

    @GetMapping("/{patientId}")
    PatientResponse find(@PathVariable UUID patientId) {
        return PatientResponse.from(patientService.find(patientId));
    }

    @PutMapping("/{patientId}")
    PatientResponse update(
            @PathVariable UUID patientId,
            @Valid @RequestBody UpdatePatientRequest patientRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        return PatientResponse.from(patientService.update(
                patientId,
                patientRequest.fullName(),
                patientRequest.motherName(),
                patientRequest.birthDate(),
                parseCpf(patientRequest.cpf()),
                patientRequest.phone(),
                patientRequest.email(),
                patientRequest.address(),
                patientRequest.emergencyContact(),
                patientRequest.insurance(),
                patientRequest.allergies(),
                patientRequest.notes(),
                patientRequest.version(),
                doctor.id(),
                (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE)));
    }

    @PatchMapping("/{patientId}/status")
    PatientResponse changeStatus(
            @PathVariable UUID patientId,
            @Valid @RequestBody ChangePatientStatusRequest statusRequest,
            @AuthenticationPrincipal DoctorPrincipal doctor,
            HttpServletRequest request) {
        return PatientResponse.from(patientService.changeStatus(
                patientId, parseStatus(statusRequest.status()), statusRequest.version(), doctor.id(), (String)
                        request.getAttribute(CorrelationIdFilter.ATTRIBUTE)));
    }

    @GetMapping
    PatientPage search(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String motherName,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "fullName,asc") String sort) {
        ApiPagination.validate(page, size);
        var pageable = PageRequest.of(page, size, parseSort(sort));
        var result = patientService.search(fullName, motherName, cpf, phone, email, parseStatus(status), pageable);
        return new PatientPage(
                result.getContent().stream().map(PatientSummary::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private Sort parseSort(String value) {
        var parts = value.split(",", -1);
        if (parts.length != 2 || !SORTABLE_FIELDS.contains(parts[0])) {
            throw invalidField("sort", "ordenação inválida");
        }
        final Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw invalidField("sort", "ordenação inválida");
        }
        var requested = new Sort.Order(direction, parts[0]);
        if ("id".equals(parts[0])) {
            return Sort.by(requested);
        }
        return Sort.by(requested, Sort.Order.asc("id"));
    }

    private PatientStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PatientStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidField("status", "status inválido");
        }
    }

    private InvalidRequestException invalidField(String field, String message) {
        return new InvalidRequestException(List.of(new InvalidField(field, message)));
    }

    private Cpf parseCpf(String value) {
        try {
            return Cpf.of(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(List.of(new InvalidField("cpf", "CPF inválido")));
        }
    }

    private record CreatePatientRequest(
            @NotBlank(message = "é obrigatório") String fullName,
            @NotBlank(message = "é obrigatório") String motherName,
            @NotNull(message = "é obrigatório") LocalDate birthDate,
            @NotBlank(message = "é obrigatório") String cpf,
            @NotBlank(message = "é obrigatório") String phone,
            String email,
            String address,
            String emergencyContact,
            String insurance,
            String allergies,
            String notes) {

        @Override
        public String toString() {
            return "CreatePatientRequest[REDACTED]";
        }
    }

    private record UpdatePatientRequest(
            @NotBlank(message = "é obrigatório") String fullName,
            @NotBlank(message = "é obrigatório") String motherName,
            @NotNull(message = "é obrigatório") LocalDate birthDate,
            @NotBlank(message = "é obrigatório") String cpf,
            @NotBlank(message = "é obrigatório") String phone,
            String email,
            String address,
            String emergencyContact,
            String insurance,
            String allergies,
            String notes,
            @NotNull(message = "é obrigatório") Long version) {

        @Override
        public String toString() {
            return "UpdatePatientRequest[REDACTED]";
        }
    }

    private record ChangePatientStatusRequest(
            @NotNull(message = "é obrigatório") String status,
            @NotNull(message = "é obrigatório") Long version) {

        @Override
        public String toString() {
            return "ChangePatientStatusRequest[REDACTED]";
        }
    }

    private record PatientResponse(
            UUID id,
            String fullName,
            String motherName,
            LocalDate birthDate,
            String cpf,
            String phone,
            String email,
            String address,
            String emergencyContact,
            String insurance,
            String allergies,
            String notes,
            PatientStatus status,
            long version) {

        private static PatientResponse from(Patient patient) {
            return new PatientResponse(
                    patient.getId(),
                    patient.getFullName(),
                    patient.getMotherName(),
                    patient.getBirthDate(),
                    patient.getCpf().value(),
                    patient.getPhone(),
                    patient.getEmail(),
                    patient.getAddress(),
                    patient.getEmergencyContact(),
                    patient.getInsurance(),
                    patient.getAllergies(),
                    patient.getNotes(),
                    patient.getStatus(),
                    patient.getVersion());
        }

        @Override
        public String toString() {
            return "PatientResponse[REDACTED]";
        }
    }

    private record PatientPage(List<PatientSummary> content, int page, int size, long totalElements, int totalPages) {

        @Override
        public String toString() {
            return "PatientPage[REDACTED]";
        }
    }

    private record PatientSummary(
            UUID id,
            String fullName,
            String motherName,
            LocalDate birthDate,
            String cpf,
            String phone,
            String email,
            PatientStatus status) {

        private static PatientSummary from(Patient patient) {
            return new PatientSummary(
                    patient.getId(),
                    patient.getFullName(),
                    patient.getMotherName(),
                    patient.getBirthDate(),
                    patient.getCpf().value(),
                    patient.getPhone(),
                    patient.getEmail(),
                    patient.getStatus());
        }

        @Override
        public String toString() {
            return "PatientSummary[REDACTED]";
        }
    }
}
