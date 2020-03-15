package no.nav.fo.veilarbregistrering.bruker.udi;

import no.nav.fo.veilarbregistrering.bruker.Foedselsnummer;
import no.nav.fo.veilarbregistrering.bruker.Personstatus;
import no.nav.fo.veilarbregistrering.bruker.PersonstatusGateway;
import no.udi.personstatus.v1.HentPersonstatusFault;
import no.udi.personstatus.v1.HentPersonstatusRequestType;
import no.udi.personstatus.v1.HentPersonstatusResponseType;
import no.udi.personstatus.v1.MT1067NAVV1Interface;

import javax.xml.datatype.DatatypeConfigurationException;

import static no.nav.fo.veilarbregistrering.bruker.udi.PersonstatusMapper.map;

class PersonstatusGatewayImpl implements PersonstatusGateway {

    private final MT1067NAVV1Interface mt1067NAVV1Interface;

    PersonstatusGatewayImpl(MT1067NAVV1Interface mt1067NAVV1Interface) {
        this.mt1067NAVV1Interface = mt1067NAVV1Interface;
    }

    @Override
    public Personstatus hentPersonstatus(Foedselsnummer foedselsnummer) {

        HentPersonstatusResponseType hentPersonstatusResponseType;
        try {
            HentPersonstatusRequestType request = map(foedselsnummer);
            hentPersonstatusResponseType = mt1067NAVV1Interface.hentPersonstatus(request);

        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Det oppstod en feil ved opprettelse av dato.", e);

        } catch (HentPersonstatusFault hentPersonstatusFault) {
            throw new RuntimeException("Henting av personstatus fra UDI feilet", hentPersonstatusFault);
        }

        return map(hentPersonstatusResponseType);
    }
}
