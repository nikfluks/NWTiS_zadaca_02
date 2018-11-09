package org.foi.nwtis.nikfluks.web.zrna;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Named;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.FacesContext;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;

@Named(value = "slanjePoruke")
@RequestScoped
public class SlanjePoruke {

    private String posluzitelj;
    private String prima;
    private String salje;
    private String predmet;
    private String sadrzajPrivitka;
    private List<String> popisDatoteka;
    private String odabranaDatoteka;
    private ServletContext context;
    private int uspjesnoPoslana = 0;
    private boolean validacijaOK = true;

    public SlanjePoruke() {
        context = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
        dohvatiPodatkeIzKonfiguracije();
        String[] poljeDatoteka = preuzmiNaziveDatoteka();
        popisDatoteka = new ArrayList<>();
        popisDatoteka = Arrays.asList(poljeDatoteka);
    }

    public void dohvatiPodatkeIzKonfiguracije() {
        BP_Konfiguracija bpk = (BP_Konfiguracija) context.getAttribute("BP_Konfig");
        posluzitelj = bpk.getMailServer();
        prima = bpk.getMailUsernameThread();
        salje = bpk.getMailUsernameEmailAddress();
        predmet = bpk.getMailSubjectEmail();
    }

    public String getPosluzitelj() {
        return posluzitelj;
    }

    public void setPosluzitelj(String posluzitelj) {
        this.posluzitelj = posluzitelj;
    }

    public String getPrima() {
        return prima;
    }

    public void setPrima(String prima) {
        this.prima = prima;
    }

    public String getSalje() {
        return salje;
    }

    public void setSalje(String salje) {
        this.salje = salje;
    }

    public String getPredmet() {
        return predmet;
    }

    public void setPredmet(String predmet) {
        this.predmet = predmet;
    }

    public List<String> getPopisDatoteka() {
        return popisDatoteka;
    }

    public void setPopisDatoteka(List<String> popisDatoteka) {
        this.popisDatoteka = popisDatoteka;
    }

    public String getOdabranaDatoteka() {
        return odabranaDatoteka;
    }

    public void setOdabranaDatoteka(String odabranaDatoteka) {
        this.odabranaDatoteka = odabranaDatoteka;
    }

    public String getSadrzajPrivitka() {
        return sadrzajPrivitka;
    }

    public void setSadrzajPrivitka(String sadrzajPrivitka) {
        this.sadrzajPrivitka = sadrzajPrivitka;
    }

    public String promjeniJezik() {
        return "promjeniJezik";
    }

    public String pregledPoruka() {
        return "pregledPoruka";
    }

    public String pregledDnevnika() {
        return "pregledDnevnika";
    }

    public String preuzmiSadrzaj() {
        try {
            String putanja = context.getRealPath("/WEB-INF") + File.separator;
            File dat = new File(putanja + odabranaDatoteka);
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dat), StandardCharsets.UTF_8));

            StringBuilder sadrzaj = new StringBuilder();
            int znak;

            while ((znak = br.read()) != -1) {
                sadrzaj.append((char) znak);
            }

            sadrzajPrivitka = sadrzaj.toString();
            br.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SlanjePoruke.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SlanjePoruke.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public String obrisiPrivitak() {
        sadrzajPrivitka = "";
        return "";
    }

    public String saljiPoruku() throws MessagingException {
        odrediStvarnePodatke();

        if (validiraj()) {
            try {
                java.util.Properties properties = System.getProperties();
                properties.put("mail.smtp.host", posluzitelj);

                Session session = Session.getInstance(properties, null);
                MimeMessage message = new MimeMessage(session);
                Address fromAddress = new InternetAddress(salje);
                message.setFrom(fromAddress);

                Address[] toAddresses = InternetAddress.parse(prima);
                message.setRecipients(Message.RecipientType.TO, toAddresses);

                message.setSubject(predmet);
                message.setText(sadrzajPrivitka);

                Multipart multipart = dodajPrivitak();
                message.setContent(multipart);

                Transport.send(message);
                uspjesnoPoslana = 1;
                sadrzajPrivitka = "";
            } catch (Exception e) {
                uspjesnoPoslana = -1;
            }
        }
        return "";
    }

    public boolean isValidacijaOK() {
        return validacijaOK;
    }

    public int getUspjesnoPoslana() {
        return uspjesnoPoslana;
    }

    private Multipart dodajPrivitak() {
        try {
            Multipart multipart = new MimeMultipart();
            MimeBodyPart attachPart = new MimeBodyPart();

            File datoteka = new File("NWTiS_privitak.json");
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(datoteka), StandardCharsets.UTF_8));
            bw.write(sadrzajPrivitka);
            bw.close();

            attachPart.attachFile(datoteka);
            attachPart.setHeader("Content-Type", "text/json;charset=utf-8");
            multipart.addBodyPart(attachPart);
            return multipart;
        } catch (IOException | MessagingException ex) {
            Logger.getLogger(SlanjePoruke.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

    }

    private String[] preuzmiNaziveDatoteka() {
        String putanja = context.getRealPath("/WEB-INF") + File.separator;
        File datoteka = new File(putanja);
        String[] poljeDatoteka = datoteka.list((File dir, String name) -> name.matches("^[^\\s]+\\.json$"));
        return poljeDatoteka;
    }

    private void odrediStvarnePodatke() {
        HttpServletRequest zahtjev = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        salje = zahtjev.getParameter("forma:uredeniPosiljatelj");
        prima = zahtjev.getParameter("forma:uredeniPrimatelj");
        predmet = zahtjev.getParameter("forma:uredeniPredmet");
        sadrzajPrivitka = zahtjev.getParameter("forma:uredeniSadrzaj");
    }

    private boolean validiraj() {
        String sintaksaMailova = "^[_A-Za-z0-9\\-]+(\\.[_A-Za-z0-9\\-]+)*\\@[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        String sintaksaPredmeta = "^.{10,}$";

        if (provjeriSintaksu(sintaksaMailova, salje) && provjeriSintaksu(sintaksaMailova, prima)
                && provjeriSintaksu(sintaksaPredmeta, predmet) && provjeriJsonFormat(sadrzajPrivitka) != null) {
            validacijaOK = true;
        } else {
            validacijaOK = false;
        }
        return validacijaOK;
    }

    private boolean provjeriSintaksu(String sintaksa, String textZaValidirati) {
        Pattern pattern = Pattern.compile(sintaksa);
        Matcher m = pattern.matcher(textZaValidirati);
        boolean status = m.matches();
        return status;
    }

    private JsonObject provjeriJsonFormat(String sadrzaj) {
        try {
            JsonObject jsonSadrzaj = new JsonParser().parse(sadrzaj).getAsJsonObject();
            return jsonSadrzaj;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }
}
