package br.com.primeiroprontuario.medicalrecord;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface AddendumRepository extends Repository<Addendum, UUID> {

    Addendum save(Addendum addendum);

    List<Addendum> findByConsultationIdOrderByCreatedAtAscIdAsc(UUID consultationId);

    List<Addendum> findByConsultationIdInOrderByCreatedAtAscIdAsc(Collection<UUID> consultationIds);
}
