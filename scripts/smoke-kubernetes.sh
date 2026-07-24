#!/usr/bin/env bash
set -euo pipefail

readonly PP_NAMESPACE="primeiro-prontuario"
readonly PP_URL="${PP_URL:-https://prontuario.local}"
readonly PP_CA_CERT="${PP_CA_CERT:-deploy/tls/ca.crt}"
readonly PP_CPF="52998224725"

for pp_command in base64 cmp curl date grep jq kubectl; do
  command -v "$pp_command" >/dev/null || {
    echo "Comando obrigatório ausente: $pp_command" >&2
    exit 1
  }
done

[[ -f "$PP_CA_CERT" ]] || {
  echo "Certificado da CA local não encontrado: $PP_CA_CERT" >&2
  exit 1
}

pp_temp_directory="$(mktemp -d)"
trap 'rm -rf -- "$pp_temp_directory"' EXIT

pp_cookie_jar="$pp_temp_directory/cookies.txt"
pp_login_headers="$pp_temp_directory/login.headers"
pp_csrf_headers="$pp_temp_directory/csrf.headers"
pp_original_attachment="$pp_temp_directory/persistencia.md"
pp_downloaded_attachment="$pp_temp_directory/download.md"

printf '# Persistência PP-022\n\nConteúdo fictício para o smoke test.\n' >"$pp_original_attachment"

pp_doctor_username="$(
  kubectl get secret primeiro-prontuario-credentials \
    --namespace "$PP_NAMESPACE" \
    --output jsonpath='{.data.doctor-username}' | base64 --decode
)"
pp_doctor_password="$(
  kubectl get secret primeiro-prontuario-credentials \
    --namespace "$PP_NAMESPACE" \
    --output jsonpath='{.data.doctor-password}' | base64 --decode
)"

pp_curl=(curl --fail --silent --show-error --cacert "$PP_CA_CERT")

authenticate() {
  : >"$pp_cookie_jar"
  "${pp_curl[@]}" \
    --dump-header "$pp_login_headers" \
    --cookie-jar "$pp_cookie_jar" \
    --header 'Content-Type: application/json' \
    --data "$(jq --null-input \
      --arg username "$pp_doctor_username" \
      --arg password "$pp_doctor_password" \
      '{username: $username, password: $password}')" \
    "$PP_URL/api/v1/auth/login" >/dev/null

  pp_csrf_response="$(
    "${pp_curl[@]}" \
      --dump-header "$pp_csrf_headers" \
      --cookie "$pp_cookie_jar" \
      --cookie-jar "$pp_cookie_jar" \
      "$PP_URL/api/v1/auth/csrf"
  )"
  pp_csrf_header="$(jq -er '.headerName' <<<"$pp_csrf_response")"
  pp_csrf_token="$(jq -er '.token' <<<"$pp_csrf_response")"
}

wait_for_https() {
  local pp_attempt

  for ((pp_attempt = 1; pp_attempt <= 60; pp_attempt++)); do
    if "${pp_curl[@]}" "$PP_URL/actuator/health" 2>/dev/null |
      jq -e '.status == "UP"' >/dev/null; then
      return
    fi
    sleep 1
  done

  echo "A API não ficou saudável via HTTPS após 60 segundos." >&2
  exit 1
}

assert_https_boundary() {
  pp_http_headers="$pp_temp_directory/http.headers"
  pp_http_status="$(
    curl --silent --show-error \
      --output /dev/null \
      --dump-header "$pp_http_headers" \
      --write-out '%{http_code}' \
      "${PP_URL/https:/http:}/actuator/health"
  )"
  [[ "$pp_http_status" == "301" || "$pp_http_status" == "308" ]]
  grep -Eiq '^location: https://' "$pp_http_headers"

  grep -Ei '^set-cookie: JSESSIONID=' "$pp_login_headers" | grep -Eiq ';[[:space:]]*Secure'
  grep -Ei '^set-cookie: JSESSIONID=' "$pp_login_headers" | grep -Eiq ';[[:space:]]*HttpOnly'
  grep -Ei '^set-cookie: JSESSIONID=' "$pp_login_headers" | grep -Eiq ';[[:space:]]*SameSite=Lax'
  grep -Eih '^set-cookie: XSRF-TOKEN=' "$pp_login_headers" "$pp_csrf_headers" |
    grep -Eiq ';[[:space:]]*Secure'
}

find_patient() {
  "${pp_curl[@]}" \
    --cookie "$pp_cookie_jar" \
    "$PP_URL/api/v1/patients?cpf=$PP_CPF" |
    jq -er '.content[0].id'
}

mutate_json() {
  local pp_method="$1"
  local pp_path="$2"
  local pp_body="$3"

  "${pp_curl[@]}" \
    --request "$pp_method" \
    --cookie "$pp_cookie_jar" \
    --cookie-jar "$pp_cookie_jar" \
    --header 'Content-Type: application/json' \
    --header "$pp_csrf_header: $pp_csrf_token" \
    --data "$pp_body" \
    "$PP_URL$pp_path"
}

choose_available_schedule() {
  local pp_days
  local pp_total

  for ((pp_days = 30; pp_days <= 395; pp_days++)); do
    pp_appointment_starts_at="$(date --date="+${pp_days} days 10:00" '+%Y-%m-%dT%H:%M:%S')"
    pp_appointment_ends_at="$(date --date="+${pp_days} days 10:30" '+%Y-%m-%dT%H:%M:%S')"
    pp_total="$(
      "${pp_curl[@]}" \
        --cookie "$pp_cookie_jar" \
        "$PP_URL/api/v1/appointments?from=$pp_appointment_starts_at&to=$pp_appointment_ends_at&size=1" |
        jq -er '.totalElements'
    )"
    if [[ "$pp_total" == "0" ]]; then
      return
    fi
  done

  echo "Nenhum horário livre encontrado para o smoke test." >&2
  exit 1
}

verify_persisted_data() {
  pp_found_patient_id="$(find_patient)"
  [[ "$pp_found_patient_id" == "$pp_patient_id" ]]

  "${pp_curl[@]}" \
    --cookie "$pp_cookie_jar" \
    "$PP_URL/api/v1/appointments/$pp_appointment_id" |
    jq -e --arg id "$pp_appointment_id" \
      '.id == $id and .status == "COMPLETED"' >/dev/null

  "${pp_curl[@]}" \
    --cookie "$pp_cookie_jar" \
    "$PP_URL/api/v1/consultations/$pp_consultation_id" |
    jq -e \
      --arg consultation_id "$pp_consultation_id" \
      --arg addendum_id "$pp_addendum_id" \
      '.id == $consultation_id
        and .status == "FINALIZED"
        and any(.addenda[]; .id == $addendum_id)' >/dev/null

  "${pp_curl[@]}" \
    --cookie "$pp_cookie_jar" \
    "$PP_URL/api/v1/patients/$pp_patient_id/medical-record?size=100" |
    jq -e \
      --arg consultation_id "$pp_consultation_id" \
      --arg addendum_id "$pp_addendum_id" \
      'any(.content[];
        .id == $consultation_id
        and any(.addenda[]; .id == $addendum_id))' >/dev/null

  "${pp_curl[@]}" \
    --cookie "$pp_cookie_jar" \
    "$PP_URL/api/v1/attachments/$pp_attachment_id/content" \
    --output "$pp_downloaded_attachment"
  cmp "$pp_original_attachment" "$pp_downloaded_attachment"

  pp_audit="$(
    "${pp_curl[@]}" \
      --cookie "$pp_cookie_jar" \
      "$PP_URL/api/v1/audit-events?size=100"
  )"
  jq -e \
    --arg patient_id "$pp_patient_id" \
    --arg appointment_id "$pp_appointment_id" \
    --arg consultation_id "$pp_consultation_id" \
    --arg addendum_id "$pp_addendum_id" \
    --arg attachment_id "$pp_attachment_id" \
    'any(.content[]; .action == "PATIENT_CREATED" and .targetId == $patient_id)
      and any(.content[]; .action == "APPOINTMENT_CREATED" and .targetId == $appointment_id)
      and any(.content[]; .action == "CONSULTATION_FINALIZED" and .targetId == $consultation_id)
      and any(.content[]; .action == "ADDENDUM_ADDED" and .targetId == $addendum_id)
      and any(.content[]; .action == "ATTACHMENT_UPLOADED" and .targetId == $attachment_id)
      and any(.content[]; .action == "ATTACHMENT_DOWNLOADED" and .targetId == $attachment_id)
      and any(.content[]; .action == "MEDICAL_RECORD_VIEWED" and .targetId == $patient_id)' \
    <<<"$pp_audit" >/dev/null
}

wait_for_https
authenticate
assert_https_boundary

if pp_patient_id="$(find_patient 2>/dev/null)"; then
  :
else
  pp_patient_id="$(
    mutate_json POST /api/v1/patients '{
      "fullName": "Paciente Fictício PP-022",
      "motherName": "Mãe Fictícia PP-022",
      "birthDate": "1990-01-01",
      "cpf": "52998224725",
      "phone": "11999990000"
    }' |
      jq -er '.id'
  )"
fi

choose_available_schedule
pp_appointment_id="$(
  mutate_json POST /api/v1/appointments "$(
    jq --null-input \
      --arg patient_id "$pp_patient_id" \
      --arg starts_at "$pp_appointment_starts_at" \
      '{patientId: $patient_id, startsAt: $starts_at, durationMinutes: 30}'
  )" |
    jq -er '.id'
)"

pp_consultation_id="$(
  mutate_json POST "/api/v1/patients/$pp_patient_id/consultations" "$(
    jq --null-input \
      --arg appointment_id "$pp_appointment_id" \
      '{
        appointmentId: $appointment_id,
        anamnesis: "História clínica fictícia",
        chiefComplaint: "Queixa fictícia",
        physicalExamination: "Exame fictício sem alterações",
        diagnosticHypotheses: "Hipótese fictícia",
        treatmentPlan: "Plano fictício de demonstração",
        observations: "Sem dados reais"
      }'
  )" |
    jq -er '.id'
)"

mutate_json POST "/api/v1/consultations/$pp_consultation_id/finalization" '{}' |
  jq -e '.status == "FINALIZED"' >/dev/null

pp_addendum_id="$(
  mutate_json POST "/api/v1/consultations/$pp_consultation_id/addenda" '{
    "content": "Complemento fictício do smoke test",
    "justification": "Validação acadêmica de persistência"
  }' |
    jq -er '.id'
)"

pp_attachment_id="$(
  "${pp_curl[@]}" \
    --cookie "$pp_cookie_jar" \
    --cookie-jar "$pp_cookie_jar" \
    --header "$pp_csrf_header: $pp_csrf_token" \
    --form "file=@$pp_original_attachment;type=text/markdown" \
    "$PP_URL/api/v1/patients/$pp_patient_id/attachments?consultationId=$pp_consultation_id" |
    jq -er '.id'
)"
verify_persisted_data

pp_api_pod="$(
  kubectl get pods \
    --namespace "$PP_NAMESPACE" \
    --selector app.kubernetes.io/name=primeiro-prontuario-api \
    --output jsonpath='{.items[0].metadata.name}'
)"
kubectl delete pod "$pp_api_pod" --namespace "$PP_NAMESPACE" --wait=true
kubectl rollout status deployment/primeiro-prontuario-api \
  --namespace "$PP_NAMESPACE" \
  --timeout=180s
wait_for_https
authenticate
verify_persisted_data

kubectl delete pod postgresql-0 --namespace "$PP_NAMESPACE" --wait=true
kubectl rollout status statefulset/postgresql \
  --namespace "$PP_NAMESPACE" \
  --timeout=180s
kubectl wait deployment/primeiro-prontuario-api \
  --namespace "$PP_NAMESPACE" \
  --for=condition=Available \
  --timeout=180s
wait_for_https
authenticate
verify_persisted_data

echo "Smoke PP-022 concluído: HTTPS, sessão, paciente, agenda, consulta, adendo, anexo e auditoria persistiram."
