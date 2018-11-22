package no.nav.fo.veilarbregistrering.service;

import lombok.extern.slf4j.Slf4j;
import no.nav.dialogarena.aktor.AktorService;
import no.nav.fo.veilarbregistrering.config.RemoteFeatureConfig;
import no.nav.fo.veilarbregistrering.db.ArbeidssokerregistreringRepository;
import no.nav.fo.veilarbregistrering.domain.*;
import no.nav.fo.veilarbregistrering.httpclient.DigisyfoClient;
import no.nav.fo.veilarbregistrering.httpclient.OppfolgingClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static java.time.LocalDate.now;
import static java.util.Optional.*;
import static no.nav.fo.veilarbregistrering.domain.RegistreringType.ORDINAER_REGISTRERING;
import static no.nav.fo.veilarbregistrering.domain.RegistreringType.SYKMELDT_REGISTRERING;
import static no.nav.fo.veilarbregistrering.service.StartRegistreringUtils.beregnRegistreringType;
import static no.nav.fo.veilarbregistrering.service.ValideringUtils.validerBrukerRegistrering;
import static no.nav.fo.veilarbregistrering.utils.FnrUtils.getAktorIdOrElseThrow;
import static no.nav.fo.veilarbregistrering.utils.FnrUtils.utledAlderForFnr;
import static no.nav.fo.veilarbregistrering.utils.FunksjonelleMetrikker.*;


@Slf4j
public class BrukerRegistreringService {

    private final ArbeidssokerregistreringRepository arbeidssokerregistreringRepository;
    private final AktorService aktorService;
    private final RemoteFeatureConfig.SykemeldtRegistreringFeature sykemeldtRegistreringFeature;
    private OppfolgingClient oppfolgingClient;
    private DigisyfoClient sykeforloepMetadataClient;
    private ArbeidsforholdService arbeidsforholdService;
    private StartRegistreringUtils startRegistreringUtils;

    public BrukerRegistreringService(ArbeidssokerregistreringRepository arbeidssokerregistreringRepository,
                                     AktorService aktorService,
                                     OppfolgingClient oppfolgingClient,
                                     DigisyfoClient sykeforloepMetadataClient,
                                     ArbeidsforholdService arbeidsforholdService,
                                     StartRegistreringUtils startRegistreringUtils,
                                     RemoteFeatureConfig.SykemeldtRegistreringFeature sykemeldtRegistreringFeature

    ) {
        this.arbeidssokerregistreringRepository = arbeidssokerregistreringRepository;
        this.aktorService = aktorService;
        this.sykemeldtRegistreringFeature = sykemeldtRegistreringFeature;
        this.oppfolgingClient = oppfolgingClient;
        this.sykeforloepMetadataClient = sykeforloepMetadataClient;
        this.arbeidsforholdService = arbeidsforholdService;
        this.startRegistreringUtils = startRegistreringUtils;
    }

    @Transactional
    public void reaktiverBruker(String fnr) {

        Boolean kanReaktiveres = hentStartRegistreringStatus(fnr).getRegistreringType() == RegistreringType.REAKTIVERING;
        if (!kanReaktiveres) {
            throw new RuntimeException("Bruker kan ikke reaktiveres.");
        }

        AktorId aktorId = getAktorIdOrElseThrow(aktorService, fnr);

        arbeidssokerregistreringRepository.lagreReaktiveringForBruker(aktorId);
        oppfolgingClient.reaktiverBruker(fnr);

        log.info("Reaktivering av bruker med aktørId : {}", aktorId);
    }

    @Transactional
    public OrdinaerBrukerRegistrering registrerBruker(OrdinaerBrukerRegistrering bruker, String fnr) {

        StartRegistreringStatus startRegistreringStatus = hentStartRegistreringStatus(fnr);

        if (startRegistreringStatus.isUnderOppfolging()) {
            throw new RuntimeException("Bruker allerede under oppfølging.");
        }

        try {
            validerBrukerRegistrering(bruker);
        } catch (RuntimeException e) {
            log.warn("Ugyldig innsendt registrering. Besvarelse: {} Stilling: {}", bruker.getBesvarelse(), bruker.getSisteStilling());
            rapporterInvalidRegistrering(bruker);
            throw e;
        }

        Profilering profilering = profilerBrukerTilInnsatsgruppe(fnr, bruker);

        return opprettBruker(fnr, bruker, profilering);
    }

    public StartRegistreringStatus hentStartRegistreringStatus(String fnr) {
        OppfolgingStatusData oppfolgingStatusData = oppfolgingClient.hentOppfolgingsstatus(fnr);

        Optional<SykeforloepMetaData> sykeforloepMetaData = empty();
        if (ofNullable(oppfolgingStatusData.erSykmeldtMedArbeidsgiver).orElse(false)) {
            sykeforloepMetaData = hentSykeforloepMetaData();
        }

        RegistreringType registreringType = beregnRegistreringType(oppfolgingStatusData, sykeforloepMetaData);

        StartRegistreringStatus startRegistreringStatus = new StartRegistreringStatus()
                .setUnderOppfolging(oppfolgingStatusData.isUnderOppfolging())
                .setSykmeldtFraDato(sykeforloepMetaData.map(s -> s.getSykmeldtFraDato()).orElse(""))
                .setRegistreringType(registreringType);

        if (ORDINAER_REGISTRERING.equals(registreringType)) {
            boolean oppfyllerBetingelseOmArbeidserfaring = startRegistreringUtils.harJobbetSammenhengendeSeksAvTolvSisteManeder(
                    () -> arbeidsforholdService.hentArbeidsforhold(fnr),
                    now());
            startRegistreringStatus.setJobbetSeksAvTolvSisteManeder(oppfyllerBetingelseOmArbeidserfaring);
        }

        log.info("Returnerer startregistreringsstatus {}", startRegistreringStatus);
        return startRegistreringStatus;
    }

    private OrdinaerBrukerRegistrering opprettBruker(String fnr, OrdinaerBrukerRegistrering bruker, Profilering profilering) {
        AktorId aktorId = getAktorIdOrElseThrow(aktorService, fnr);

        OrdinaerBrukerRegistrering ordinaerBrukerRegistrering = arbeidssokerregistreringRepository.lagreOrdinaerBruker(bruker, aktorId);
        arbeidssokerregistreringRepository.lagreProfilering(ordinaerBrukerRegistrering.getId(), profilering);
        oppfolgingClient.aktiverBruker(new AktiverBrukerData(new Fnr(fnr), profilering.getInnsatsgruppe()));

        rapporterProfilering(profilering);
        rapporterOrdinaerBesvarelse(bruker, profilering);
        log.info("Brukerregistrering gjennomført med data {}, Profilering {}", ordinaerBrukerRegistrering, profilering);
        return ordinaerBrukerRegistrering;
    }

    public BrukerRegistreringWrapper hentBrukerRegistrering(Fnr fnr) {

        AktorId aktorId = getAktorIdOrElseThrow(aktorService, fnr.getFnr());

        OrdinaerBrukerRegistrering ordinaerBrukerRegistrering = arbeidssokerregistreringRepository
                .hentOrdinaerBrukerregistreringMedProfileringForAktorId(aktorId);

        SykmeldtRegistrering sykmeldtBrukerRegistrering = arbeidssokerregistreringRepository
                .hentSykmeldtregistreringForAktorId(aktorId);

        if(ordinaerBrukerRegistrering == null && sykmeldtBrukerRegistrering == null){
            return null;
        }else if(ordinaerBrukerRegistrering == null){
            return new BrukerRegistreringWrapper(sykmeldtBrukerRegistrering);
        }else if(sykmeldtBrukerRegistrering == null){
            return new BrukerRegistreringWrapper(ordinaerBrukerRegistrering);
        }

        LocalDateTime profilertBrukerRegistreringDato = ordinaerBrukerRegistrering.getOpprettetDato();
        LocalDateTime sykmeldtRegistreringDato = sykmeldtBrukerRegistrering.getOpprettetDato();

        if(profilertBrukerRegistreringDato.isAfter(sykmeldtRegistreringDato)){
            return new BrukerRegistreringWrapper(ordinaerBrukerRegistrering);
        }else{
            return new BrukerRegistreringWrapper(sykmeldtBrukerRegistrering);
        }

    }

    private Profilering profilerBrukerTilInnsatsgruppe(String fnr, OrdinaerBrukerRegistrering bruker) {
        return startRegistreringUtils.profilerBruker(
                bruker,
                utledAlderForFnr(fnr, now()),
                () -> arbeidsforholdService.hentArbeidsforhold(fnr),
                now());
    }

    @Transactional
    public void registrerSykmeldt(SykmeldtRegistrering sykmeldtRegistrering, String fnr) {
        if (!sykemeldtRegistreringFeature.erSykemeldtRegistreringAktiv()) {
            throw new RuntimeException("Tjenesten for sykmeldt-registrering er togglet av.");
        }

        ofNullable (sykmeldtRegistrering.getBesvarelse())
                .orElseThrow(() -> new RuntimeException("Besvarelse for sykmeldt ugyldig."));

        StartRegistreringStatus startRegistreringStatus = hentStartRegistreringStatus(fnr);
        if (SYKMELDT_REGISTRERING.equals(startRegistreringStatus.getRegistreringType())) {
            AktorId aktorId = getAktorIdOrElseThrow(aktorService, fnr);
            oppfolgingClient.settOppfolgingSykmeldt();
            arbeidssokerregistreringRepository.lagreSykmeldtBruker(sykmeldtRegistrering, aktorId);
            log.info("Sykmeldtregistrering gjennomført med data {}", sykmeldtRegistrering);
        } else {
            throw new RuntimeException("Bruker kan ikke registreres.");
        }
    }

    private Optional<SykeforloepMetaData> hentSykeforloepMetaData() {
        if (sykemeldtRegistreringFeature.skalMockeDataFraDigisyfo()) {
            //Mocker data fra Digisyfo. todo: må fjernes når Digisyfo-tjenesten er tilgjengelig i prod.
            return of(new SykeforloepMetaData()
                    .withErArbeidsrettetOppfolgingSykmeldtInngangAktiv(true)
                    .withSykmeldtFraDato("2018-01-21"));
        }

        if (sykemeldtRegistreringFeature.skalKalleDigisyfoTjeneste()) {
            return of(sykeforloepMetadataClient.hentSykeforloepMetadata());
        } else {
            return empty();
        }
    }
}
