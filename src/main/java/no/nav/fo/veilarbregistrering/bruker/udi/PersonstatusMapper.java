package no.nav.fo.veilarbregistrering.bruker.udi;

import no.nav.fo.veilarbregistrering.bruker.Foedselsnummer;
import no.nav.fo.veilarbregistrering.bruker.Personstatus;
import no.udi.personstatus.v1.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;

public class PersonstatusMapper {

    static HentPersonstatusRequestType map(Foedselsnummer foedselsnummer) throws DatatypeConfigurationException {
        LocalDate localDate = LocalDate.now().minusMonths(3); //TODO: Hvor langt tilbake bør vi gå?
        XMLGregorianCalendar avgjorelseFraDato = DatatypeFactory.newInstance().newXMLGregorianCalendar(localDate.toString());

        WSHentPersonstatusParameter wsHentPersonstatusParameter = new WSHentPersonstatusParameter();
        wsHentPersonstatusParameter.setAvgjorelserFraDato(avgjorelseFraDato);
        wsHentPersonstatusParameter.setFodselsnummer(foedselsnummer.stringValue());
        wsHentPersonstatusParameter.setInkluderArbeidsadgang(true);
        wsHentPersonstatusParameter.setInkluderAvgjorelsehistorikk(false);
        wsHentPersonstatusParameter.setInkluderFlyktningstatus(false);
        wsHentPersonstatusParameter.setManuellOppgVedUavklartArbeidsadgang(false);

        HentPersonstatusRequestType request = new HentPersonstatusRequestType();
        request.setParameter(wsHentPersonstatusParameter);
        return request;
    }

    static Personstatus map(HentPersonstatusResponseType hentPersonstatusResponseType) {
        WSHentPersonstatusResultat resultat = hentPersonstatusResponseType.getResultat();
        WSArbeidsadgang arbeidsadgang = resultat.getArbeidsadgang();
        WSArbeidsadgangType typeArbeidsadgang = arbeidsadgang.getTypeArbeidsadgang();
        WSJaNeiUavklart harArbeidsadgang = arbeidsadgang.getHarArbeidsadgang();

        return new Personstatus(harArbeidsadgang.value(), typeArbeidsadgang.value());
    }
}
