package org.foi.nwtis.nikfluks.web.kontrole;

import java.util.Date;

public class Poruka {
    public static enum VrstaPoruka {  
        NWTiS_poruka,
        neNWTiS_poruka
    }
    

    private String id;
    private Date vrijemeSlanja;
    private Date vrijemePrijema;
    private String salje;
    private String predmet;
    private String privitak;
    private VrstaPoruka vrsta;

    public Poruka(String id, Date vrijemeSlanja, Date vrijemePrijema, String salje, String predmet, String privitak, VrstaPoruka vrsta) {
        this.id = id;
        this.vrijemeSlanja = vrijemeSlanja;
        this.vrijemePrijema = vrijemePrijema;
        this.salje = salje;
        this.predmet = predmet;
        this.privitak = privitak;
        this.vrsta = vrsta;
    }

    public String getId() {
        return id;
    }

    public Date getVrijemeSlanja() {
        return vrijemeSlanja;
    }

    public Date getVrijemePrijema() {
        return vrijemePrijema;
    }

    public String getPredmet() {
        return predmet;
    }

    public String getSalje() {
        return salje;
    }

    public VrstaPoruka getVrsta() {
        return vrsta;
    }

    public String getPrivitak() {
        return privitak;
    }

}
