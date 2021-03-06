package no.nav.fo.veilarbregistrering.registrering.bruker;
import lombok.*;

@Data
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class TekstForSporsmal {
    private String sporsmalId;
    private String sporsmal;
    private String svar;
}
