package org.foi.nwtis.nikfluks.web.kontrole;

import java.util.Date;

public class Dnevnik {

    private int id;
    private String sadrzaj;
    private Date vrijemeZapisa;

    public Dnevnik(int id, String sadrzaj, Date vrijemeZapisa) {
        this.id = id;
        this.sadrzaj = sadrzaj;
        this.vrijemeZapisa = vrijemeZapisa;
    }

    public int getId() {
        return id;
    }

    public String getSadrzaj() {
        return sadrzaj;
    }

    public Date getVrijemeZapisa() {
        return vrijemeZapisa;
    }
}
