package no.nav.fo.veilarbregistrering.registrering.bruker;

import lombok.SneakyThrows;
import no.nav.fo.veilarbregistrering.arbeidsforhold.Arbeidsforhold;
import no.nav.fo.veilarbregistrering.arbeidsforhold.ArbeidsforholdGateway;
import no.nav.fo.veilarbregistrering.arbeidsforhold.FlereArbeidsforhold;
import no.nav.fo.veilarbregistrering.bruker.*;
import no.nav.fo.veilarbregistrering.oppfolging.adapter.OppfolgingClient;
import no.nav.fo.veilarbregistrering.oppfolging.adapter.OppfolgingGatewayImpl;
import no.nav.fo.veilarbregistrering.oppfolging.adapter.OppfolgingStatusData;
import no.nav.fo.veilarbregistrering.profilering.ProfileringRepository;
import no.nav.fo.veilarbregistrering.profilering.StartRegistreringUtils;
import no.nav.fo.veilarbregistrering.registrering.manuell.ManuellRegistreringService;
import no.nav.fo.veilarbregistrering.registrering.resources.StartRegistreringStatusDto;
import no.nav.fo.veilarbregistrering.sykemelding.SykemeldingService;
import no.nav.fo.veilarbregistrering.sykemelding.adapter.InfotrygdData;
import no.nav.fo.veilarbregistrering.sykemelding.adapter.SykemeldingGatewayImpl;
import no.nav.fo.veilarbregistrering.sykemelding.adapter.SykmeldtInfoClient;
import no.nav.sbl.featuretoggle.unleash.UnleashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.time.LocalDate.now;
import static no.nav.fo.veilarbregistrering.registrering.bruker.RegistreringType.SYKMELDT_REGISTRERING;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BrukerRegistreringServiceTest {

    private static Foedselsnummer FNR_OPPFYLLER_KRAV = Foedselsnummer.of(FnrUtilsTest.getFodselsnummerOnDateMinusYears(now(), 40));
    private static Bruker BRUKER_INTERN = Bruker.of(FNR_OPPFYLLER_KRAV, AktorId.valueOf("AKTØRID"));

    private BrukerRegistreringRepository brukerRegistreringRepository;
    private ProfileringRepository profileringRepository;
    private SykmeldtInfoClient sykeforloepMetadataClient;
    private BrukerRegistreringService brukerRegistreringService;
    private OppfolgingClient oppfolgingClient;
    private PersonGateway personGateway;
    private ArbeidsforholdGateway arbeidsforholdGateway;
    private StartRegistreringUtils startRegistreringUtils;
    private ManuellRegistreringService manuellRegistreringService;
    private UnleashService unleashService;
    private ArbeidssokerRegistrertProducer arbeidssokerRegistrertProducer;

    @BeforeEach
    public void setup() {
        unleashService = mock(UnleashService.class);
        brukerRegistreringRepository = mock(BrukerRegistreringRepository.class);
        profileringRepository = mock(ProfileringRepository.class);
        manuellRegistreringService = mock(ManuellRegistreringService.class);
        oppfolgingClient = mock(OppfolgingClient.class);
        personGateway = mock(PersonGateway.class);
        sykeforloepMetadataClient = mock(SykmeldtInfoClient.class);
        arbeidsforholdGateway = mock(ArbeidsforholdGateway.class);
        startRegistreringUtils = new StartRegistreringUtils();
        arbeidssokerRegistrertProducer = (aktorId, brukersSituasjon, opprettetDato) -> {}; //NoOp siden vi ikke ønsker å teste Kafka her

        brukerRegistreringService =
                new BrukerRegistreringService(
                        brukerRegistreringRepository,
                        profileringRepository,
                        new OppfolgingGatewayImpl(oppfolgingClient),
                        personGateway,
                        new SykemeldingService(new SykemeldingGatewayImpl(sykeforloepMetadataClient)),
                        arbeidsforholdGateway,
                        manuellRegistreringService,
                        startRegistreringUtils,
                        unleashService,
                        arbeidssokerRegistrertProducer);

        when(unleashService.isEnabled(any())).thenReturn(true);
    }

    /*
     * Test av besvarelsene og lagring
     * */
    @Test
    void skalRegistrereSelvgaaendeBruker() {
        mockInaktivBrukerUtenReaktivering();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        OrdinaerBrukerRegistrering selvgaaendeBruker = OrdinaerBrukerRegistreringTestdataBuilder.gyldigBrukerRegistrering();
        when(brukerRegistreringRepository.lagreOrdinaerBruker(any(OrdinaerBrukerRegistrering.class), any(AktorId.class))).thenReturn(selvgaaendeBruker);
        registrerBruker(selvgaaendeBruker, BRUKER_INTERN);
        verify(brukerRegistreringRepository, times(1)).lagreOrdinaerBruker(any(), any());
    }

    @Test
    void skalReaktivereInaktivBrukerUnder28Dager() {
        mockInaktivBrukerSomSkalReaktiveres();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();

        brukerRegistreringService.reaktiverBruker(BRUKER_INTERN);
        verify(brukerRegistreringRepository, times(1)).lagreReaktiveringForBruker(any());
    }

    @Test
    void reaktiveringAvBrukerOver28DagerSkalGiException() {
        mockInaktivBrukerSomSkalReaktiveres();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        mockOppfolgingMedRespons(
                new OppfolgingStatusData()
                        .withUnderOppfolging(false)
                        .withKanReaktiveres(false)
        );
        assertThrows(RuntimeException.class, () -> brukerRegistreringService.reaktiverBruker(BRUKER_INTERN));
        verify(brukerRegistreringRepository, times(0)).lagreReaktiveringForBruker(any());
    }

    @Test
    void skalRegistrereSelvgaaendeBrukerIDatabasen() {
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        mockOppfolgingMedRespons(new OppfolgingStatusData().withUnderOppfolging(false).withKanReaktiveres(false));
        OrdinaerBrukerRegistrering selvgaaendeBruker = OrdinaerBrukerRegistreringTestdataBuilder.gyldigBrukerRegistrering();
        when(brukerRegistreringRepository.lagreOrdinaerBruker(any(OrdinaerBrukerRegistrering.class), any(AktorId.class))).thenReturn(selvgaaendeBruker);
        registrerBruker(selvgaaendeBruker, BRUKER_INTERN);
        verify(oppfolgingClient, times(1)).aktiverBruker(any());
        verify(brukerRegistreringRepository, times(1)).lagreOrdinaerBruker(any(), any());
    }

    @Test
    void skalIkkeLagreRegistreringSomErUnderOppfolging() {
        mockBrukerUnderOppfolging();
        OrdinaerBrukerRegistrering selvgaaendeBruker = OrdinaerBrukerRegistreringTestdataBuilder.gyldigBrukerRegistrering();
        assertThrows(RuntimeException.class, () -> registrerBruker(selvgaaendeBruker, BRUKER_INTERN));
    }

    @Test
    public void skalReturnereUnderOppfolgingNaarUnderOppfolging() {
        mockArbeidssokerSomHarAktivOppfolging();
        StartRegistreringStatusDto startRegistreringStatus = brukerRegistreringService.hentStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == RegistreringType.ALLEREDE_REGISTRERT).isTrue();
    }

    @Test
    public void skalReturnereAtBrukerOppfyllerBetingelseOmArbeidserfaring() {
        mockInaktivBrukerUtenReaktivering();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        StartRegistreringStatusDto startRegistreringStatus = brukerRegistreringService.hentStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getJobbetSeksAvTolvSisteManeder()).isTrue();
    }

    @Test
    public void skalReturnereFalseOmIkkeUnderOppfolging() {
        mockOppfolgingMedRespons(inaktivBruker());
        mockArbeidsforhold(arbeidsforholdSomOppfyllerKrav());
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == RegistreringType.ALLEREDE_REGISTRERT).isFalse();
    }

    @Test
    void skalIkkeRegistrereSykmeldteMedTomBesvarelse() {
        mockSykmeldtBrukerOver39uker();
        mockSykmeldtMedArbeidsgiver();
        SykmeldtRegistrering sykmeldtRegistrering = new SykmeldtRegistrering().setBesvarelse(null);
        assertThrows(RuntimeException.class, () -> brukerRegistreringService.registrerSykmeldt(sykmeldtRegistrering, BRUKER_INTERN));
    }

    @Test
    void skalIkkeRegistrereSykmeldtSomIkkeOppfyllerKrav() {
        mockSykmeldtMedArbeidsgiver();
        SykmeldtRegistrering sykmeldtRegistrering = SykmeldtRegistreringTestdataBuilder.gyldigSykmeldtRegistrering();
        assertThrows(RuntimeException.class, () -> brukerRegistreringService.registrerSykmeldt(sykmeldtRegistrering, BRUKER_INTERN));
    }

    @Test
    public void skalReturnereAlleredeUnderOppfolging() {
        mockArbeidssokerSomHarAktivOppfolging();
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == RegistreringType.ALLEREDE_REGISTRERT).isTrue();
    }

    @Test
    public void skalReturnereReaktivering() {
        mockOppfolgingMedRespons(inaktivBruker());
        mockArbeidsforhold(arbeidsforholdSomOppfyllerKrav());
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == RegistreringType.REAKTIVERING).isTrue();
    }

    @Test
    public void skalReturnereSykmeldtRegistrering() {
        mockSykmeldtBruker();
        mockSykmeldtBrukerOver39uker();
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == SYKMELDT_REGISTRERING).isTrue();
    }

    @Test
    public void skalReturnereSperret() {
        mockSykmeldtBruker();
        mockSykmeldtBrukerUnder39uker();
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == RegistreringType.SPERRET).isTrue();
    }

    @Test
    public void gitt_at_geografiskTilknytning_ikke_ble_funnet_skal_null_returneres() {
        mockInaktivBrukerUtenReaktivering();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus).isNotNull();
        assertThat(startRegistreringStatus.getGeografiskTilknytning()).isNull();

    }

    @Test
    public void gitt_at_geografiskTilknytning_er_1234_skal_1234_returneres() {
        mockInaktivBrukerUtenReaktivering();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        when(personGateway.hentGeografiskTilknytning(any())).thenReturn(Optional.of(GeografiskTilknytning.of("1234")));
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus).isNotNull();
        assertThat(startRegistreringStatus.getGeografiskTilknytning()).isEqualTo("1234");
    }

    @Test
    public void gitt_at_geografiskTilknytning_kaster_exception_skal_null_returneres() {
        mockInaktivBrukerUtenReaktivering();
        mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring();
        when(personGateway.hentGeografiskTilknytning(any())).thenThrow(new RuntimeException("Ikke tilgang"));
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus).isNotNull();
        assertThat(startRegistreringStatus.getGeografiskTilknytning()).isNull();
    }

    @Test
    public void skalReturnereOrdinarRegistrering() {
        mockIkkeSykmeldtBruker();
        mockArbeidsforhold(arbeidsforholdSomOppfyllerKrav());
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        assertThat(startRegistreringStatus.getRegistreringType() == RegistreringType.ORDINAER_REGISTRERING).isTrue();
    }

    @Test
    public void mockDataSkalIkkeGjeldeNaarMockToggleErAv() {
        mockSykmeldtBruker();
        mockSykmeldtBrukerUnder39uker();
        StartRegistreringStatusDto startRegistreringStatus = getStartRegistreringStatus(FNR_OPPFYLLER_KRAV);
        verify(sykeforloepMetadataClient, times(1)).hentSykmeldtInfoData(any());
        assertThat(SYKMELDT_REGISTRERING.equals(startRegistreringStatus.getRegistreringType())).isFalse();
    }

    private List<Arbeidsforhold> arbeidsforholdSomOppfyllerKrav() {
        return Collections.singletonList(new Arbeidsforhold()
                .setArbeidsgiverOrgnummer("orgnummer")
                .setStyrk("styrk")
                .setFom(LocalDate.of(2017, 1, 10)));
    }

    private OppfolgingStatusData inaktivBruker() {
        return new OppfolgingStatusData().withUnderOppfolging(false).withKanReaktiveres(true);
    }

    private void mockOppfolgingMedRespons(OppfolgingStatusData oppfolgingStatusData) {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(oppfolgingStatusData);
    }

    @SneakyThrows
    private StartRegistreringStatusDto getStartRegistreringStatus(Foedselsnummer fnr) {
        return brukerRegistreringService.hentStartRegistreringStatus(fnr);
    }

    @SneakyThrows
    private void mockArbeidsforhold(List<Arbeidsforhold> arbeidsforhold) {
        when(arbeidsforholdGateway.hentArbeidsforhold(any())).thenReturn(FlereArbeidsforhold.of(arbeidsforhold));
    }

    private OrdinaerBrukerRegistrering registrerBruker(OrdinaerBrukerRegistrering ordinaerBrukerRegistrering, Bruker bruker) {
        return brukerRegistreringService.registrerBruker(ordinaerBrukerRegistrering, bruker);
    }

    private void mockBrukerUnderOppfolging() {
        when(brukerRegistreringRepository.lagreOrdinaerBruker(any(), any())).thenReturn(OrdinaerBrukerRegistreringTestdataBuilder.gyldigBrukerRegistrering());

    }

    private void mockArbeidssokerSomHarAktivOppfolging() {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(
                new OppfolgingStatusData().withUnderOppfolging(true).withKanReaktiveres(false)
        );
    }

    private void mockInaktivBrukerUtenReaktivering() {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(
                new OppfolgingStatusData().withUnderOppfolging(false).withKanReaktiveres(false)
        );
    }

    private void mockInaktivBrukerSomSkalReaktiveres() {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(
                new OppfolgingStatusData().withUnderOppfolging(false).withKanReaktiveres(true)
        );
    }

    private void mockSykmeldtBruker() {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(
                new OppfolgingStatusData()
                        .withUnderOppfolging(false)
                        .withKanReaktiveres(false)
                        .withErSykmeldtMedArbeidsgiver(true)
        );
    }

    private void mockIkkeSykmeldtBruker() {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(
                new OppfolgingStatusData()
                        .withUnderOppfolging(false)
                        .withKanReaktiveres(false)
                        .withErSykmeldtMedArbeidsgiver(false)
        );
    }

    private void mockSykmeldtBrukerOver39uker() {
        String dagensDatoMinus13Uker = now().plusWeeks(13).toString();
        when(sykeforloepMetadataClient.hentSykmeldtInfoData(any())).thenReturn(
                new InfotrygdData()
                        .withMaksDato(dagensDatoMinus13Uker)
        );
    }

    private void mockSykmeldtBrukerUnder39uker() {
        String dagensDatoMinus14Uker = now().plusWeeks(14).toString();
        when(sykeforloepMetadataClient.hentSykmeldtInfoData(any())).thenReturn(
                new InfotrygdData()
                        .withMaksDato(dagensDatoMinus14Uker)
        );
    }


    private void mockSykmeldtMedArbeidsgiver() {
        when(oppfolgingClient.hentOppfolgingsstatus(any())).thenReturn(
                new OppfolgingStatusData().withErSykmeldtMedArbeidsgiver(true).withKanReaktiveres(false)
        );
    }

    private void mockArbeidssforholdSomOppfyllerBetingelseOmArbeidserfaring() {
        when(arbeidsforholdGateway.hentArbeidsforhold(any())).thenReturn(
                FlereArbeidsforhold.of(Collections.singletonList(new Arbeidsforhold()
                        .setArbeidsgiverOrgnummer("orgnummer")
                        .setStyrk("styrk")
                        .setFom(LocalDate.of(2017, 1, 10))))
        );
    }
}
