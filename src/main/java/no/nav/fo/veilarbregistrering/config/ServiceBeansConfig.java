package no.nav.fo.veilarbregistrering.config;

import no.nav.apiapp.security.PepClient;
import no.nav.dialogarena.aktor.AktorService;
import no.nav.fo.veilarbregistrering.db.ArbeidssokerregistreringRepository;
import no.nav.fo.veilarbregistrering.httpclient.OppfolgingClient;
import no.nav.fo.veilarbregistrering.resources.RegistreringResource;
import no.nav.fo.veilarbregistrering.service.*;
import no.nav.tjeneste.virksomhet.arbeidsforhold.v3.binding.ArbeidsforholdV3;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

@Configuration
public class ServiceBeansConfig {


    @Bean
    BrukerRegistreringService registrerBrukerService(
            ArbeidssokerregistreringRepository arbeidssokerregistreringRepository,
            AktorService aktorService,
            RemoteFeatureConfig.RegistreringFeature skalRegistrereBrukerGenerellFeature,
            OppfolgingClient oppfolgingClient,
            ArbeidsforholdService arbeidsforholdService,
            StartRegistreringUtilsService startRegistreringUtilsService)
    {
        return new BrukerRegistreringService(
                arbeidssokerregistreringRepository,
                aktorService,
                skalRegistrereBrukerGenerellFeature,
                oppfolgingClient,
                arbeidsforholdService,
                startRegistreringUtilsService
        );
    }

    @Bean
    RegistreringResource registreringResource(PepClient pepClient,
                                              UserService userService,
                                              ArbeidsforholdService arbeidsforholdService,
                                              BrukerRegistreringService brukerRegistreringService) {
        return new RegistreringResource(
                pepClient,
                userService,
                arbeidsforholdService,
                brukerRegistreringService);
    }

    @Bean
    ArbeidssokerregistreringRepository arbeidssokerregistreringRepository(JdbcTemplate db) {
        return new ArbeidssokerregistreringRepository(db);
    }

    @Bean
    ArbeidsforholdService arbeidsforholdService(ArbeidsforholdV3 arbeidsforholdV3) {
        return new ArbeidsforholdService(arbeidsforholdV3);
    }

    @Bean
    OppfolgingClient oppfolgingClient(Provider<HttpServletRequest> provider) {
        return new OppfolgingClient(provider);
    }

    @Bean
    StartRegistreringUtilsService startRegistreringUtils() {
        return new StartRegistreringUtilsService();
    }

    @Bean
    UserService userService() {
        return new UserService();
    }

}
