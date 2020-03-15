package no.nav.fo.veilarbregistrering.bruker.udi;

import no.nav.fo.veilarbregistrering.bruker.PersonstatusGateway;
import no.nav.sbl.dialogarena.common.cxf.CXFClient;
import no.nav.sbl.dialogarena.types.Pingable;
import no.udi.common.v2.PingRequestType;
import no.udi.personstatus.v1.MT1067NAVV1Interface;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static no.nav.sbl.dialogarena.common.cxf.TimeoutFeature.DEFAULT_CONNECTION_TIMEOUT;
import static no.nav.sbl.dialogarena.types.Pingable.Ping.feilet;
import static no.nav.sbl.dialogarena.types.Pingable.Ping.lyktes;
import static no.nav.sbl.util.EnvironmentUtils.getRequiredProperty;

@Configuration
public class UdiServiceWSConfig {

    public static final String UDI_ENDPOINT_URL = "UDI_ENDPOINTURL";

    private final static String URL = getRequiredProperty(UDI_ENDPOINT_URL);

    @Bean
    PersonstatusGateway personStatusGateway(MT1067NAVV1Interface mt1067NAVV1Interface) {
        return new PersonstatusGatewayImpl(mt1067NAVV1Interface);
    }

    @Bean
    MT1067NAVV1Interface mt1067NAVV1Interface() {
        return createMT1067NAVV1InterfaceCXFClient()
                .timeout(DEFAULT_CONNECTION_TIMEOUT, 120000)
                .configureStsForOnBehalfOfWithJWT()
                .build();
    }

    @Bean
    Pingable personStatusPing() {
        final MT1067NAVV1Interface personStatusService = createMT1067NAVV1InterfaceCXFClient()
                .configureStsForSystemUserInFSS()
                .build();

        Pingable.Ping.PingMetadata metadata = new Pingable.Ping.PingMetadata(
                "MT1067NAVV1Interface via " + URL,
                "Ping av MT1067NAVV1Interface. Henter informasjon om personstatus fra UDI.",
                false
        );

        return () -> {
            try {
                personStatusService.ping(new PingRequestType());
                return lyktes(metadata);
            } catch (Exception e) {
                return feilet(metadata, e);
            }
        };
    }

    private CXFClient<MT1067NAVV1Interface> createMT1067NAVV1InterfaceCXFClient() {
        return new CXFClient<>(MT1067NAVV1Interface.class)
                .withOutInterceptor(new LoggingOutInterceptor())
                .address(URL);
    }
}
