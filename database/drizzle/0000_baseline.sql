CREATE TABLE "addendum" (
	"id" uuid PRIMARY KEY NOT NULL,
	"consultation_id" uuid NOT NULL,
	"content" text NOT NULL,
	"justification" text NOT NULL,
	"author_id" uuid NOT NULL,
	"created_at" timestamp (6) with time zone NOT NULL,
	CONSTRAINT "addendum_content_check" CHECK (BTRIM("addendum"."content") <> ''),
	CONSTRAINT "addendum_justification_check" CHECK (BTRIM("addendum"."justification") <> '')
);
--> statement-breakpoint
CREATE TABLE "appointment" (
	"id" uuid PRIMARY KEY NOT NULL,
	"patient_id" uuid NOT NULL,
	"starts_at" timestamp (6) with time zone NOT NULL,
	"ends_at" timestamp (6) with time zone NOT NULL,
	"duration_minutes" integer NOT NULL,
	"status" varchar(20) NOT NULL,
	"version" bigint DEFAULT 0 NOT NULL,
	CONSTRAINT "appointment_duration_minutes_check" CHECK ("appointment"."duration_minutes" IN (15, 30, 45, 60)),
	CONSTRAINT "appointment_status_check" CHECK ("appointment"."status" IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
	CONSTRAINT "appointment_check" CHECK ("appointment"."ends_at" = "appointment"."starts_at" + "appointment"."duration_minutes" * INTERVAL '1 minute')
);
--> statement-breakpoint
CREATE TABLE "attachment" (
	"id" uuid PRIMARY KEY NOT NULL,
	"patient_id" uuid NOT NULL,
	"consultation_id" uuid,
	"original_filename" text NOT NULL,
	"media_type" varchar(80) NOT NULL,
	"size_bytes" bigint NOT NULL,
	"sha256" varchar(64) NOT NULL,
	"storage_key" varchar(36) NOT NULL,
	"status" varchar(20) NOT NULL,
	"uploaded_by" uuid NOT NULL,
	"created_at" timestamp (6) with time zone NOT NULL,
	"removed_by" uuid,
	"removal_justification" text,
	"removed_at" timestamp (6) with time zone,
	"binary_cleanup_pending" boolean DEFAULT false NOT NULL,
	CONSTRAINT "attachment_storage_key_key" UNIQUE("storage_key"),
	CONSTRAINT "attachment_size_bytes_check" CHECK ("attachment"."size_bytes" BETWEEN 1 AND 10485760),
	CONSTRAINT "attachment_status_check" CHECK ("attachment"."status" IN ('ACTIVE', 'REMOVED')),
	CONSTRAINT "attachment_tombstone_check" CHECK ((
        (
          "attachment"."status" = 'ACTIVE'
          AND "attachment"."removed_by" IS NULL
          AND "attachment"."removal_justification" IS NULL
          AND "attachment"."removed_at" IS NULL
          AND "attachment"."binary_cleanup_pending" = FALSE
        )
        OR
        (
          "attachment"."status" = 'REMOVED'
          AND "attachment"."removed_by" IS NOT NULL
          AND "attachment"."removal_justification" IS NOT NULL
          AND BTRIM("attachment"."removal_justification") <> ''
          AND "attachment"."removed_at" IS NOT NULL
        )
      ))
);
--> statement-breakpoint
CREATE TABLE "audit_event" (
	"id" uuid PRIMARY KEY NOT NULL,
	"actor_id" uuid,
	"action" varchar(80) NOT NULL,
	"target_type" varchar(80) NOT NULL,
	"target_id" uuid,
	"outcome" varchar(20) NOT NULL,
	"occurred_at" timestamp (6) with time zone NOT NULL,
	"correlation_id" varchar(36) NOT NULL,
	"changed_fields" jsonb DEFAULT '[]'::jsonb NOT NULL,
	CONSTRAINT "audit_event_changed_fields_check" CHECK (jsonb_typeof("audit_event"."changed_fields") = 'array')
);
--> statement-breakpoint
CREATE TABLE "consultation" (
	"id" uuid PRIMARY KEY NOT NULL,
	"patient_id" uuid NOT NULL,
	"appointment_id" uuid,
	"anamnesis" text,
	"chief_complaint" text,
	"physical_examination" text,
	"diagnostic_hypotheses" text,
	"treatment_plan" text,
	"observations" text,
	"status" varchar(20) NOT NULL,
	"created_at" timestamp (6) with time zone NOT NULL,
	"version" bigint NOT NULL,
	"finalized_by" uuid,
	"finalized_at" timestamp (6) with time zone,
	"clinical_date" timestamp (6) with time zone NOT NULL,
	CONSTRAINT "consultation_appointment_id_key" UNIQUE("appointment_id"),
	CONSTRAINT "consultation_status_check" CHECK ("consultation"."status" IN ('DRAFT', 'FINALIZED')),
	CONSTRAINT "consultation_finalization_metadata_check" CHECK ((
        ("consultation"."status" = 'DRAFT' AND "consultation"."finalized_by" IS NULL AND "consultation"."finalized_at" IS NULL)
        OR
        ("consultation"."status" = 'FINALIZED' AND "consultation"."finalized_by" IS NOT NULL AND "consultation"."finalized_at" IS NOT NULL)
      ))
);
--> statement-breakpoint
CREATE TABLE "doctor_account" (
	"id" uuid PRIMARY KEY NOT NULL,
	"username" varchar(100) NOT NULL,
	"password_hash" varchar(100) NOT NULL,
	"active" boolean NOT NULL,
	"created_at" timestamp (6) with time zone NOT NULL,
	"singleton_key" boolean DEFAULT true NOT NULL,
	CONSTRAINT "doctor_account_username_key" UNIQUE("username"),
	CONSTRAINT "doctor_account_singleton_key_key" UNIQUE("singleton_key"),
	CONSTRAINT "doctor_account_singleton_key_check" CHECK ("doctor_account"."singleton_key")
);
--> statement-breakpoint
CREATE TABLE "patient" (
	"id" uuid PRIMARY KEY NOT NULL,
	"full_name" text NOT NULL,
	"mother_name" text NOT NULL,
	"birth_date" date NOT NULL,
	"cpf" varchar(11) NOT NULL,
	"phone" text NOT NULL,
	"email" text,
	"address" text,
	"emergency_contact" text,
	"insurance" text,
	"allergies" text,
	"notes" text,
	"status" varchar(20) NOT NULL,
	"version" bigint NOT NULL,
	CONSTRAINT "patient_cpf_key" UNIQUE("cpf"),
	CONSTRAINT "patient_full_name_check" CHECK (btrim("patient"."full_name") <> ''),
	CONSTRAINT "patient_mother_name_check" CHECK (btrim("patient"."mother_name") <> ''),
	CONSTRAINT "patient_cpf_check" CHECK ("patient"."cpf" ~ '^[0-9]{11}$'),
	CONSTRAINT "patient_phone_check" CHECK ("patient"."phone" ~ '^[0-9]+$'),
	CONSTRAINT "patient_status_check" CHECK ("patient"."status" IN ('ACTIVE', 'INACTIVE'))
);
--> statement-breakpoint
CREATE TABLE "schedule_block" (
	"id" uuid PRIMARY KEY NOT NULL,
	"starts_at" timestamp (6) with time zone NOT NULL,
	"ends_at" timestamp (6) with time zone NOT NULL,
	"reason" text NOT NULL,
	"created_at" timestamp (6) with time zone NOT NULL,
	CONSTRAINT "schedule_block_reason_check" CHECK (length(trim("schedule_block"."reason")) > 0),
	CONSTRAINT "schedule_block_check" CHECK ("schedule_block"."ends_at" > "schedule_block"."starts_at")
);
--> statement-breakpoint
CREATE TABLE "schedule_calendar" (
	"id" smallint PRIMARY KEY NOT NULL,
	CONSTRAINT "schedule_calendar_id_check" CHECK ("schedule_calendar"."id" = 1)
);
--> statement-breakpoint
CREATE TABLE "schema_marker" (
	"id" smallint PRIMARY KEY NOT NULL,
	"installed_at" timestamp (6) with time zone NOT NULL
);
--> statement-breakpoint
ALTER TABLE "addendum" ADD CONSTRAINT "addendum_consultation_id_fkey" FOREIGN KEY ("consultation_id") REFERENCES "public"."consultation"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "addendum" ADD CONSTRAINT "addendum_author_id_fkey" FOREIGN KEY ("author_id") REFERENCES "public"."doctor_account"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "appointment" ADD CONSTRAINT "appointment_patient_id_fkey" FOREIGN KEY ("patient_id") REFERENCES "public"."patient"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "attachment" ADD CONSTRAINT "attachment_patient_id_fkey" FOREIGN KEY ("patient_id") REFERENCES "public"."patient"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "attachment" ADD CONSTRAINT "attachment_consultation_id_fkey" FOREIGN KEY ("consultation_id") REFERENCES "public"."consultation"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "attachment" ADD CONSTRAINT "attachment_uploaded_by_fkey" FOREIGN KEY ("uploaded_by") REFERENCES "public"."doctor_account"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "attachment" ADD CONSTRAINT "attachment_removed_by_fkey" FOREIGN KEY ("removed_by") REFERENCES "public"."doctor_account"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "audit_event" ADD CONSTRAINT "audit_event_actor_id_fkey" FOREIGN KEY ("actor_id") REFERENCES "public"."doctor_account"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "consultation" ADD CONSTRAINT "consultation_patient_id_fkey" FOREIGN KEY ("patient_id") REFERENCES "public"."patient"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "consultation" ADD CONSTRAINT "consultation_appointment_id_fkey" FOREIGN KEY ("appointment_id") REFERENCES "public"."appointment"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "consultation" ADD CONSTRAINT "consultation_finalized_by_fkey" FOREIGN KEY ("finalized_by") REFERENCES "public"."doctor_account"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "addendum_consultation_created_idx" ON "addendum" USING btree ("consultation_id","created_at","id");--> statement-breakpoint
CREATE INDEX "appointment_interval_idx" ON "appointment" USING btree ("starts_at","ends_at","id");--> statement-breakpoint
CREATE INDEX "appointment_patient_interval_idx" ON "appointment" USING btree ("patient_id","starts_at","id");--> statement-breakpoint
CREATE INDEX "attachment_patient_status_created_idx" ON "attachment" USING btree ("patient_id","status","created_at","id");--> statement-breakpoint
CREATE INDEX "audit_event_occurred_at_idx" ON "audit_event" USING btree ("occurred_at" DESC NULLS FIRST,"id" DESC NULLS FIRST);--> statement-breakpoint
CREATE INDEX "audit_event_action_occurred_at_idx" ON "audit_event" USING btree ("action","occurred_at" DESC NULLS FIRST,"id" DESC NULLS FIRST);--> statement-breakpoint
CREATE INDEX "consultation_medical_record_idx" ON "consultation" USING btree ("patient_id","status","clinical_date","created_at","id");--> statement-breakpoint
CREATE INDEX "schedule_block_interval_idx" ON "schedule_block" USING btree ("starts_at","ends_at","id");--> statement-breakpoint
INSERT INTO "schema_marker" ("id", "installed_at")
VALUES (1, CURRENT_TIMESTAMP);--> statement-breakpoint
INSERT INTO "schedule_calendar" ("id")
VALUES (1);--> statement-breakpoint
CREATE FUNCTION reject_addendum_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'addenda are append-only';
END;
$$;--> statement-breakpoint
CREATE TRIGGER addendum_append_only
BEFORE UPDATE OR DELETE ON addendum
FOR EACH ROW
EXECUTE FUNCTION reject_addendum_mutation();--> statement-breakpoint
CREATE FUNCTION reject_audit_event_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only'
        USING ERRCODE = '55000';
END;
$$;--> statement-breakpoint
CREATE TRIGGER audit_event_append_only
BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW
EXECUTE FUNCTION reject_audit_event_mutation();--> statement-breakpoint
REVOKE CREATE ON SCHEMA public FROM PUBLIC;--> statement-breakpoint
GRANT USAGE ON SCHEMA public TO primeiro_prontuario_runtime;--> statement-breakpoint
GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA public
TO primeiro_prontuario_runtime;--> statement-breakpoint
GRANT USAGE, SELECT, UPDATE
ON ALL SEQUENCES IN SCHEMA public
TO primeiro_prontuario_runtime;--> statement-breakpoint
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLES
TO primeiro_prontuario_runtime;--> statement-breakpoint
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT USAGE, SELECT, UPDATE
ON SEQUENCES
TO primeiro_prontuario_runtime;
