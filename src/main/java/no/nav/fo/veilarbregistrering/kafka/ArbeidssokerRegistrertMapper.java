package no.nav.fo.veilarbregistrering.kafka;

import no.nav.arbeid.soker.registrering.ArbeidssokerRegistrertEvent;
import no.nav.fo.veilarbregistrering.besvarelse.DinSituasjonSvar;
import no.nav.fo.veilarbregistrering.bruker.AktorId;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;

class ArbeidssokerRegistrertMapper {

    static ArbeidssokerRegistrertEvent map(AktorId aktorId, DinSituasjonSvar brukersSituasjon, LocalDateTime opprettetDato) {
        return ArbeidssokerRegistrertEvent.newBuilder()
                .setAktorid(aktorId.asString())
                .setBrukersSituasjon(brukersSituasjon != null ? brukersSituasjon.toString() : null)
                .setRegistreringOpprettet(
                        ZonedDateTime.of(opprettetDato, ZoneId.systemDefault()).format(ISO_ZONED_DATE_TIME))
                .build();
    }
}
