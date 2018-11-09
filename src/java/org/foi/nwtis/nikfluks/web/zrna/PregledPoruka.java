package org.foi.nwtis.nikfluks.web.zrna;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.FacesContext;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.web.kontrole.Izbornik;
import org.foi.nwtis.nikfluks.web.kontrole.Poruka;

@Named(value = "pregledPoruka")
@RequestScoped
public class PregledPoruka {

    private String posluzitelj;
    private String korIme;
    private String lozinka;
    private int port;
    private List<Izbornik> popisMapa;
    private String odabranaMapa;
    private List<Poruka> popisPoruka;
    private int ukupanBrojPorukaUMapi;
    private int brojPorukaZaPrikazati;
    private static int odPozicije = 1;
    private static int doPozicije = 0;
    private ServletContext context;
    private String mailFolder;
    Session session;
    Properties properties = System.getProperties();
    private Store store;
    private Folder folderInbox;
    private Folder folderNWTiS;
    Poruka.VrstaPoruka vrstaPoruke;
    MimeBodyPart part = null;
    Message[] messages;
    private static boolean postojiSljedeca = true;
    private static boolean postojiPrethodna = false;

    public PregledPoruka() {
        context = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
        dohvatiPodatkeIzKonfiguracije();
        if (doPozicije == 0) {
            doPozicije = brojPorukaZaPrikazati;
        }
        preuzmiMape();
        dodajUPopisMapa();
        preuzmiPoruke();
    }

    public void dohvatiPodatkeIzKonfiguracije() {
        BP_Konfiguracija bpk = (BP_Konfiguracija) context.getAttribute("BP_Konfig");
        posluzitelj = bpk.getMailServer();
        korIme = bpk.getMailUsernameThread();
        lozinka = bpk.getMailPasswordThread();
        mailFolder = bpk.getMailFolderNWTiS();
        brojPorukaZaPrikazati = Integer.parseInt(bpk.getMailNumMessagesToShow());
        port = Integer.parseInt(bpk.getMailImapPort());
    }

    private void preuzmiMape() {
        try {
            properties.put("mail.smtp.host", posluzitelj);
            session = Session.getInstance(properties, null);
            store = session.getStore("imap");
            store.connect(posluzitelj, port, korIme, lozinka);
            folderInbox = store.getFolder("INBOX");
            folderInbox.open(Folder.READ_WRITE);

            folderNWTiS = store.getFolder(mailFolder);
            if (!folderNWTiS.exists()) {
                folderNWTiS = null;
            }
            odabranaMapa = folderInbox.getName();
        } catch (NoSuchProviderException ex) {
            Logger.getLogger(PregledPoruka.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MessagingException ex) {
            Logger.getLogger(PregledPoruka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void dodajUPopisMapa() {
        popisMapa = new ArrayList<>();
        popisMapa.add(new Izbornik(folderInbox.getName(), "INBOX"));
        if (folderNWTiS != null) {
            popisMapa.add(new Izbornik(folderNWTiS.getName(), "NWTiS"));
        }
    }

    public void preuzmiPoruke() {
        pripremiPreuzimanjePoruka();
        String privitak = "";

        try {
            for (Message mess : messages) {
                String tipSadrzaja = mess.getContentType();
                privitak = "";

                if (tipSadrzaja.contains("multipart")) {
                    Multipart multiPart = (Multipart) mess.getContent();
                    int ukupanBrojDijelova = multiPart.getCount();

                    for (int nDio = 0; nDio < ukupanBrojDijelova; nDio++) {
                        part = (MimeBodyPart) multiPart.getBodyPart(nDio);
                        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                            privitak += part.getFileName() + ", ";
                        }
                    }
                }
                if (privitak.endsWith(", ")) {
                    privitak = privitak.substring(0, privitak.length() - 2);
                }
                popisPoruka.add(new Poruka(Integer.toString(mess.getMessageNumber()), mess.getSentDate(), mess.getReceivedDate(),
                        mess.getFrom()[0].toString(), mess.getSubject(), privitak, vrstaPoruke));
            }
        } catch (MessagingException | IOException ex) {
            Logger.getLogger(PregledPoruka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void pripremiPreuzimanjePoruka() {
        popisPoruka = new ArrayList<>();
        messages = new Message[brojPorukaZaPrikazati];
        HttpServletRequest zahtjev = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();

        if (odabranaMapa == null) {
            odabranaMapa = zahtjev.getParameter("formaPregled:odabranaMapa");
        }

        if (odabranaMapa.equalsIgnoreCase("inbox")) {
            dohvatiInboxPoruke();
        } else {
            dohvatiNWTiSporuke();
        }
    }

    public void dohvatiInboxPoruke() {
        try {
            if (!folderInbox.isOpen()) {
                folderInbox.open(Folder.READ_WRITE);
            }
            ukupanBrojPorukaUMapi = folderInbox.getMessageCount();
            messages = folderInbox.getMessages(odPozicije, doPozicije);
            vrstaPoruke = Poruka.VrstaPoruka.neNWTiS_poruka;
        } catch (MessagingException e) {
            System.out.println("Greška kod dohvacanja poruka iz inboxa");
        }
    }

    public void dohvatiNWTiSporuke() {
        try {
            if (!folderNWTiS.isOpen()) {
                folderNWTiS.open(Folder.READ_WRITE);
            }
            ukupanBrojPorukaUMapi = folderNWTiS.getMessageCount();
            messages = folderNWTiS.getMessages(odPozicije, doPozicije);
            vrstaPoruke = Poruka.VrstaPoruka.NWTiS_poruka;
        } catch (MessagingException e) {
            System.out.println("Greška kod dohvacanja poruka iz NWTiS-a");
        }
    }

    public String getPosluzitelj() {
        return posluzitelj;
    }

    public void setPosluzitelj(String posluzitelj) {
        this.posluzitelj = posluzitelj;
    }

    public String getKorIme() {
        return korIme;
    }

    public void setKorIme(String korIme) {
        this.korIme = korIme;
    }

    public String getLozinka() {
        return lozinka;
    }

    public void setLozinka(String lozinka) {
        this.lozinka = lozinka;
    }

    public List<Izbornik> getPopisMapa() {
        return popisMapa;
    }

    public void setPopisMapa(List<Izbornik> popisMapa) {
        this.popisMapa = popisMapa;
    }

    public String getOdabranaMapa() {
        return odabranaMapa;
    }

    public void setOdabranaMapa(String odabranaMapa) {
        this.odabranaMapa = odabranaMapa;
    }

    public List<Poruka> getPopisPoruka() {
        return popisPoruka;
    }

    public void setPopisPoruka(List<Poruka> popisPoruka) {
        this.popisPoruka = popisPoruka;
    }

    public int getUkupanBrojPorukaUMapi() {
        return ukupanBrojPorukaUMapi;
    }

    public void setUkupanBrojPorukaUMapi(int ukupanBrojPorukaUMapi) {
        this.ukupanBrojPorukaUMapi = ukupanBrojPorukaUMapi;
    }

    public int getBrojPorukaZaPrikazati() {
        return brojPorukaZaPrikazati;
    }

    public void setBrojPorukaZaPrikazati(int brojPorukaZaPrikazati) {
        this.brojPorukaZaPrikazati = brojPorukaZaPrikazati;
    }

    public int getOdPozicije() {
        return odPozicije;
    }

    public void setOdPozicije(int odPozicije) {
        this.odPozicije = odPozicije;
    }

    public int getDoPozicije() {
        return doPozicije;
    }

    public void setDoPozicije(int doPozicije) {
        this.doPozicije = doPozicije;
    }

    public ServletContext getContext() {
        return context;
    }

    public void setContext(ServletContext context) {
        this.context = context;
    }

    public String getMailFolder() {
        return mailFolder;
    }

    public void setMailFolder(String mailFolder) {
        this.mailFolder = mailFolder;
    }

    public boolean isPostojiSljedeca() {
        System.out.println("postojiSljedecaREND: " + postojiSljedeca);
        return postojiSljedeca;
    }

    public void setPostojiSljedeca(boolean postojiSljedeca) {
        this.postojiSljedeca = postojiSljedeca;
    }

    public boolean isPostojiPrethodna() {
        return postojiPrethodna;
    }

    public void setPostojiPrethodna(boolean postojiPrethodna) {
        this.postojiPrethodna = postojiPrethodna;
    }

    public String promjeniJezik() {
        return "promjeniJezik";
    }

    public String saljiPoruku() {
        return "saljiPoruku";
    }

    public String pregledDnevnika() {
        return "pregledDnevnika";
    }

    public String promjenaMape() {
        HttpServletRequest zahtjev = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        odabranaMapa = zahtjev.getParameter("formaPregled:odabranaMapa");
        System.out.println("odabranaMapa: " + odabranaMapa);
        odPozicije = 1;
        doPozicije = brojPorukaZaPrikazati;
        preuzmiPoruke();
        return "";
    }

    public String prethodnePoruke() {
        if (odPozicije >= 1) {
            doPozicije = odPozicije - 1;
            odPozicije -= brojPorukaZaPrikazati;
            if (odPozicije <= 1) {
                odPozicije = 1;
                postojiPrethodna = false;
            } else {
                postojiPrethodna = true;
            }

            preuzmiPoruke();
        }
        postojiSljedeca = true;
        return "";
    }

    public String sljedecePoruke() {
        if (doPozicije < ukupanBrojPorukaUMapi) {
            odPozicije = doPozicije + 1;
            doPozicije += brojPorukaZaPrikazati;
            if (doPozicije >= ukupanBrojPorukaUMapi) {
                doPozicije = ukupanBrojPorukaUMapi;
                postojiSljedeca = false;
            } else {
                postojiSljedeca = true;
            }
            System.out.println("postojiSljedeca: " + postojiSljedeca);

            System.out.println("sljedecePorukeOD: " + odPozicije);
            System.out.println("sljedecePorukeDO: " + doPozicije);
            preuzmiPoruke();
        }
        postojiPrethodna = true;
        return "";
    }
}
