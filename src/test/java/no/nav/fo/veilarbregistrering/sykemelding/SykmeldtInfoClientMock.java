package no.nav.fo.veilarbregistrering.sykemelding;

import no.nav.fo.veilarbregistrering.bruker.Foedselsnummer;
import no.nav.fo.veilarbregistrering.sykemelding.adapter.InfotrygdData;
import no.nav.fo.veilarbregistrering.sykemelding.adapter.SykmeldtInfoClient;

public class SykmeldtInfoClientMock extends SykmeldtInfoClient {

    public SykmeldtInfoClientMock() {
        super(null, null);
    }

    @Override
    public InfotrygdData hentSykmeldtInfoData(Foedselsnummer fnr) {
        return new InfotrygdData().withMaksDato("2018-01-01");
    }
}
