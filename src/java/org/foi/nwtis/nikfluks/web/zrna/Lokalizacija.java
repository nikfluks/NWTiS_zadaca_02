package org.foi.nwtis.nikfluks.web.zrna;

import javax.inject.Named;
import javax.enterprise.context.SessionScoped;
import java.io.Serializable;
import java.util.Locale;
import javax.faces.context.FacesContext;

@Named(value = "lokalizacija")
@SessionScoped
public class Lokalizacija implements Serializable {

    private String odabraniJezik;

    public String getOdabraniJezik() {
        if (odabraniJezik != null) {
            return odabraniJezik;
        } else {
            odabraniJezik = FacesContext.getCurrentInstance().getApplication().getDefaultLocale().getLanguage();
            return odabraniJezik;
        }
    }

    /*
    public void setOdabraniJezik(String odabraniJezik) {
        this.odabraniJezik = odabraniJezik;
    }*/
    public Object odaberiJezik(String jezik) {
        Locale locale = new Locale(jezik);
        FacesContext.getCurrentInstance().getViewRoot().setLocale(locale);
        this.odabraniJezik = jezik;
        return "";
    }

    public String saljiPoruku() {
        return "saljiPoruku";
    }

    public String pregledPoruka() {
        return "pregledPoruka";
    }

    public String pregledDnevnika() {
        return "pregledDnevnika";
    }
}
