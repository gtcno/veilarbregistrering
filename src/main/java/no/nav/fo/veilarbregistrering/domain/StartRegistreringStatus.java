package no.nav.fo.veilarbregistrering.domain;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StartRegistreringStatus {
    private boolean underOppfolging;
    private Boolean jobbetSeksAvTolvSisteManeder;
    private String sykmeldtFraDato;
    private RegistreringType registreringType;
}
