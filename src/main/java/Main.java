import no.nav.apiapp.ApiApp;
import no.nav.fo.veilarbregistrering.config.ApplicationConfig;
import no.nav.sbl.dialogarena.common.cxf.StsSecurityConstants;

import static java.lang.System.setProperty;
import static no.nav.brukerdialog.security.Constants.OIDC_REDIRECT_URL_PROPERTY_NAME;
import static no.nav.dialogarena.aktor.AktorConfig.AKTOER_ENDPOINT_URL;
import static no.nav.sbl.dialogarena.common.abac.pep.CredentialConstants.SYSTEMUSER_PASSWORD;
import static no.nav.sbl.dialogarena.common.abac.pep.CredentialConstants.SYSTEMUSER_USERNAME;
import static no.nav.sbl.dialogarena.common.abac.pep.service.AbacService.ABAC_ENDPOINT_URL_PROPERTY_NAME;
import static no.nav.sbl.dialogarena.common.cxf.StsSecurityConstants.STS_URL_KEY;
import static no.nav.sbl.util.EnvironmentUtils.getRequiredProperty;

public class Main {

    public static void main(String... args) throws Exception {

        setProperty("http.nonProxyHosts", "*.155.55.|*.192.168.|*.10.|*.local|*.rtv.gov|*.adeo.no|*.nav.no|*.aetat.no|*.devillo.no|*.oera.no");
        setProperty("http.proxyHost", "webproxy-utvikler.nav.no");
        setProperty("http.proxyPort", "8088");
        setProperty("https.proxyHost", "webproxy-utvikler.nav.no");
        setProperty("https.proxyPort", "8088");

        setProperty(SYSTEMUSER_USERNAME, getRequiredProperty("SRVVEILARBREGISTRERING_USERNAME"));
        setProperty(SYSTEMUSER_PASSWORD, getRequiredProperty("SRVVEILARBREGISTRERING_PASSWORD"));

        setProperty(AKTOER_ENDPOINT_URL, getRequiredProperty("AKTOER_V2_ENDPOINTURL"));
        setProperty(OIDC_REDIRECT_URL_PROPERTY_NAME, getRequiredProperty("VEILARBLOGIN_REDIRECT_URL_URL"));
        setProperty(ABAC_ENDPOINT_URL_PROPERTY_NAME, getRequiredProperty("ABAC_PDP_ENDPOINT_URL"));
        setProperty(STS_URL_KEY, getRequiredProperty("SECURITYTOKENSERVICE_URL"));


        ApiApp.startApp(ApplicationConfig.class, args);
    }

}