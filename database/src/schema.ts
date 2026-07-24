import { sql } from "drizzle-orm";
import {
  bigint,
  boolean,
  check,
  date,
  foreignKey,
  index,
  integer,
  jsonb,
  pgTable,
  smallint,
  text,
  timestamp,
  unique,
  uuid,
  varchar,
} from "drizzle-orm/pg-core";

const timestampWithTimeZone = (name: string) =>
  timestamp(name, { mode: "date", precision: 6, withTimezone: true });

export const schemaMarker = pgTable("schema_marker", {
  id: smallint("id").primaryKey(),
  installedAt: timestampWithTimeZone("installed_at").notNull(),
});

export const doctorAccount = pgTable(
  "doctor_account",
  {
    id: uuid("id").primaryKey(),
    username: varchar("username", { length: 100 }).notNull(),
    passwordHash: varchar("password_hash", { length: 100 }).notNull(),
    active: boolean("active").notNull(),
    createdAt: timestampWithTimeZone("created_at").notNull(),
    singletonKey: boolean("singleton_key").default(true).notNull(),
  },
  (table) => [
    unique("doctor_account_username_key").on(table.username),
    unique("doctor_account_singleton_key_key").on(table.singletonKey),
    check("doctor_account_singleton_key_check", sql`${table.singletonKey}`),
  ],
);

export const auditEvent = pgTable(
  "audit_event",
  {
    id: uuid("id").primaryKey(),
    actorId: uuid("actor_id"),
    action: varchar("action", { length: 80 }).notNull(),
    targetType: varchar("target_type", { length: 80 }).notNull(),
    targetId: uuid("target_id"),
    outcome: varchar("outcome", { length: 20 }).notNull(),
    occurredAt: timestampWithTimeZone("occurred_at").notNull(),
    correlationId: varchar("correlation_id", { length: 36 }).notNull(),
    changedFields: jsonb("changed_fields")
      .default(sql`'[]'::jsonb`)
      .notNull(),
  },
  (table) => [
    foreignKey({
      columns: [table.actorId],
      foreignColumns: [doctorAccount.id],
      name: "audit_event_actor_id_fkey",
    }),
    check(
      "audit_event_changed_fields_check",
      sql`jsonb_typeof(${table.changedFields}) = 'array'`,
    ),
    index("audit_event_occurred_at_idx").on(
      table.occurredAt.desc().nullsFirst(),
      table.id.desc().nullsFirst(),
    ),
    index("audit_event_action_occurred_at_idx").on(
      table.action,
      table.occurredAt.desc().nullsFirst(),
      table.id.desc().nullsFirst(),
    ),
  ],
);

export const patient = pgTable(
  "patient",
  {
    id: uuid("id").primaryKey(),
    fullName: text("full_name").notNull(),
    motherName: text("mother_name").notNull(),
    birthDate: date("birth_date", { mode: "string" }).notNull(),
    cpf: varchar("cpf", { length: 11 }).notNull(),
    phone: text("phone").notNull(),
    email: text("email"),
    address: text("address"),
    emergencyContact: text("emergency_contact"),
    insurance: text("insurance"),
    allergies: text("allergies"),
    notes: text("notes"),
    status: varchar("status", { length: 20 }).notNull(),
    version: bigint("version", { mode: "number" }).notNull(),
  },
  (table) => [
    unique("patient_cpf_key").on(table.cpf),
    check("patient_full_name_check", sql`btrim(${table.fullName}) <> ''`),
    check("patient_mother_name_check", sql`btrim(${table.motherName}) <> ''`),
    check("patient_cpf_check", sql`${table.cpf} ~ '^[0-9]{11}$'`),
    check("patient_phone_check", sql`${table.phone} ~ '^[0-9]+$'`),
    check(
      "patient_status_check",
      sql`${table.status} IN ('ACTIVE', 'INACTIVE')`,
    ),
  ],
);

export const appointment = pgTable(
  "appointment",
  {
    id: uuid("id").primaryKey(),
    patientId: uuid("patient_id").notNull(),
    startsAt: timestampWithTimeZone("starts_at").notNull(),
    endsAt: timestampWithTimeZone("ends_at").notNull(),
    durationMinutes: integer("duration_minutes").notNull(),
    status: varchar("status", { length: 20 }).notNull(),
    version: bigint("version", { mode: "number" }).default(0).notNull(),
  },
  (table) => [
    foreignKey({
      columns: [table.patientId],
      foreignColumns: [patient.id],
      name: "appointment_patient_id_fkey",
    }),
    check(
      "appointment_duration_minutes_check",
      sql`${table.durationMinutes} IN (15, 30, 45, 60)`,
    ),
    check(
      "appointment_status_check",
      sql`${table.status} IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')`,
    ),
    check(
      "appointment_check",
      sql`${table.endsAt} = ${table.startsAt} + ${table.durationMinutes} * INTERVAL '1 minute'`,
    ),
    index("appointment_interval_idx").on(
      table.startsAt,
      table.endsAt,
      table.id,
    ),
    index("appointment_patient_interval_idx").on(
      table.patientId,
      table.startsAt,
      table.id,
    ),
  ],
);

export const scheduleCalendar = pgTable(
  "schedule_calendar",
  {
    id: smallint("id").primaryKey(),
  },
  (table) => [check("schedule_calendar_id_check", sql`${table.id} = 1`)],
);

export const scheduleBlock = pgTable(
  "schedule_block",
  {
    id: uuid("id").primaryKey(),
    startsAt: timestampWithTimeZone("starts_at").notNull(),
    endsAt: timestampWithTimeZone("ends_at").notNull(),
    reason: text("reason").notNull(),
    createdAt: timestampWithTimeZone("created_at").notNull(),
  },
  (table) => [
    check(
      "schedule_block_reason_check",
      sql`length(trim(${table.reason})) > 0`,
    ),
    check("schedule_block_check", sql`${table.endsAt} > ${table.startsAt}`),
    index("schedule_block_interval_idx").on(
      table.startsAt,
      table.endsAt,
      table.id,
    ),
  ],
);

export const consultation = pgTable(
  "consultation",
  {
    id: uuid("id").primaryKey(),
    patientId: uuid("patient_id").notNull(),
    appointmentId: uuid("appointment_id"),
    anamnesis: text("anamnesis"),
    chiefComplaint: text("chief_complaint"),
    physicalExamination: text("physical_examination"),
    diagnosticHypotheses: text("diagnostic_hypotheses"),
    treatmentPlan: text("treatment_plan"),
    observations: text("observations"),
    status: varchar("status", { length: 20 }).notNull(),
    createdAt: timestampWithTimeZone("created_at").notNull(),
    version: bigint("version", { mode: "number" }).notNull(),
    finalizedBy: uuid("finalized_by"),
    finalizedAt: timestampWithTimeZone("finalized_at"),
    clinicalDate: timestampWithTimeZone("clinical_date").notNull(),
  },
  (table) => [
    unique("consultation_appointment_id_key").on(table.appointmentId),
    foreignKey({
      columns: [table.patientId],
      foreignColumns: [patient.id],
      name: "consultation_patient_id_fkey",
    }),
    foreignKey({
      columns: [table.appointmentId],
      foreignColumns: [appointment.id],
      name: "consultation_appointment_id_fkey",
    }),
    foreignKey({
      columns: [table.finalizedBy],
      foreignColumns: [doctorAccount.id],
      name: "consultation_finalized_by_fkey",
    }),
    check(
      "consultation_status_check",
      sql`${table.status} IN ('DRAFT', 'FINALIZED')`,
    ),
    check(
      "consultation_finalization_metadata_check",
      sql`(
        (${table.status} = 'DRAFT' AND ${table.finalizedBy} IS NULL AND ${table.finalizedAt} IS NULL)
        OR
        (${table.status} = 'FINALIZED' AND ${table.finalizedBy} IS NOT NULL AND ${table.finalizedAt} IS NOT NULL)
      )`,
    ),
    index("consultation_medical_record_idx").on(
      table.patientId,
      table.status,
      table.clinicalDate,
      table.createdAt,
      table.id,
    ),
  ],
);

export const addendum = pgTable(
  "addendum",
  {
    id: uuid("id").primaryKey(),
    consultationId: uuid("consultation_id").notNull(),
    content: text("content").notNull(),
    justification: text("justification").notNull(),
    authorId: uuid("author_id").notNull(),
    createdAt: timestampWithTimeZone("created_at").notNull(),
  },
  (table) => [
    foreignKey({
      columns: [table.consultationId],
      foreignColumns: [consultation.id],
      name: "addendum_consultation_id_fkey",
    }),
    foreignKey({
      columns: [table.authorId],
      foreignColumns: [doctorAccount.id],
      name: "addendum_author_id_fkey",
    }),
    check("addendum_content_check", sql`BTRIM(${table.content}) <> ''`),
    check(
      "addendum_justification_check",
      sql`BTRIM(${table.justification}) <> ''`,
    ),
    index("addendum_consultation_created_idx").on(
      table.consultationId,
      table.createdAt,
      table.id,
    ),
  ],
);

export const attachment = pgTable(
  "attachment",
  {
    id: uuid("id").primaryKey(),
    patientId: uuid("patient_id").notNull(),
    consultationId: uuid("consultation_id"),
    originalFilename: text("original_filename").notNull(),
    mediaType: varchar("media_type", { length: 80 }).notNull(),
    sizeBytes: bigint("size_bytes", { mode: "number" }).notNull(),
    sha256: varchar("sha256", { length: 64 }).notNull(),
    storageKey: varchar("storage_key", { length: 36 }).notNull(),
    status: varchar("status", { length: 20 }).notNull(),
    uploadedBy: uuid("uploaded_by").notNull(),
    createdAt: timestampWithTimeZone("created_at").notNull(),
    removedBy: uuid("removed_by"),
    removalJustification: text("removal_justification"),
    removedAt: timestampWithTimeZone("removed_at"),
    binaryCleanupPending: boolean("binary_cleanup_pending")
      .default(false)
      .notNull(),
  },
  (table) => [
    unique("attachment_storage_key_key").on(table.storageKey),
    foreignKey({
      columns: [table.patientId],
      foreignColumns: [patient.id],
      name: "attachment_patient_id_fkey",
    }),
    foreignKey({
      columns: [table.consultationId],
      foreignColumns: [consultation.id],
      name: "attachment_consultation_id_fkey",
    }),
    foreignKey({
      columns: [table.uploadedBy],
      foreignColumns: [doctorAccount.id],
      name: "attachment_uploaded_by_fkey",
    }),
    foreignKey({
      columns: [table.removedBy],
      foreignColumns: [doctorAccount.id],
      name: "attachment_removed_by_fkey",
    }),
    check(
      "attachment_size_bytes_check",
      sql`${table.sizeBytes} BETWEEN 1 AND 10485760`,
    ),
    check(
      "attachment_status_check",
      sql`${table.status} IN ('ACTIVE', 'REMOVED')`,
    ),
    check(
      "attachment_tombstone_check",
      sql`(
        (
          ${table.status} = 'ACTIVE'
          AND ${table.removedBy} IS NULL
          AND ${table.removalJustification} IS NULL
          AND ${table.removedAt} IS NULL
          AND ${table.binaryCleanupPending} = FALSE
        )
        OR
        (
          ${table.status} = 'REMOVED'
          AND ${table.removedBy} IS NOT NULL
          AND ${table.removalJustification} IS NOT NULL
          AND BTRIM(${table.removalJustification}) <> ''
          AND ${table.removedAt} IS NOT NULL
        )
      )`,
    ),
    index("attachment_patient_status_created_idx").on(
      table.patientId,
      table.status,
      table.createdAt,
      table.id,
    ),
  ],
);
