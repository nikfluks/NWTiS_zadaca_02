package org.foi.nwtis.nikfluks.web.dretve;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Flags;
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
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;

public class ObradaPoruka extends Thread {

    ServletContext context;
    private String posluzitelj;
    private String korImeMail;
    private String lozinkaMail;
    private int port;
    private int spavanje;
    private int nPorukaZaCitanje;
    private String nazivPrivitka;
    private String urlBaze;
    private String korImeBaza;
    private String lozinkaBaza;
    private String uprProgram;
    private String mailFolder;
    private String datotekaLog;
    private boolean radi = true;
    int brojPrivitaka = 0;
    String nazivTrenutnogPrivitka = "";
    boolean procitana = false;
    MimeBodyPart part = null;
    List<Integer> brojRedova = new ArrayList<>();
    List<String> dohvaceniSadrzaj = new ArrayList<>();
    List<Message> messages = new ArrayList<>();
    JsonObject jsonSadrzaj;
    Session session;
    Properties properties = System.getProperties();
    private Store store;
    private Folder folderInbox;
    private Folder folderNWTiS;
    String sadrzajPrivitka;

    int brojObradePoruka = 0;
    String pocetakObrade;
    String krajObrade;
    long trajanjeObrade;
    int brojNeprocitanihPoruka = 0;
    int brojDodanihIOT = 0;
    int brojAzuriranihIOT = 0;
    int brojNeispravnih = 0;

    public ObradaPoruka(ServletContext context) {
        this.context = context;
    }

    @Override
    public void interrupt() {
        radi = false;
        super.interrupt();
    }

    @Override
    public void run() {
        while (radi) {
            try {
                SimpleDateFormat formatVremena = new SimpleDateFormat("dd.MM.yyyy HH.mm.ss.SSS");
                brojNeprocitanihPoruka = 0;
                brojDodanihIOT = 0;
                brojAzuriranihIOT = 0;
                brojNeispravnih = 0;

                Date pocetak = new Date();
                pocetakObrade = formatVremena.format(pocetak);

                citajPorukePoGrupama();

                Date kraj = new Date();
                krajObrade = formatVremena.format(kraj);

                trajanjeObrade = kraj.getTime() - pocetak.getTime();
                brojObradePoruka++;
                System.out.println("Obrada poruke broj: " + brojObradePoruka);

                dodajPodatkeULog();
                sleep(spavanje);
            } catch (NoSuchProviderException ex) {
                Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            } catch (MessagingException | InterruptedException ex) {
                Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public synchronized void start() {
        if (dohvatiPodatkeIzKonfiguracije()) {
            isprazniLogDatoteku();
            super.start();
        } else {
            System.out.println("Greška pri dohvaćanju podataka iz kofiguracije!");
        }
    }

    private void isprazniLogDatoteku() {
        String putanja = context.getRealPath("/");
        File datoteka = new File(putanja + File.separator + datotekaLog);

        if (datoteka.exists()) {
            try {
                PrintWriter pw = new PrintWriter(datoteka);
                pw.write("");
                pw.close();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private boolean dohvatiPodatkeIzKonfiguracije() {
        try {
            BP_Konfiguracija bpk = (BP_Konfiguracija) context.getAttribute("BP_Konfig");

            posluzitelj = bpk.getMailServer();
            korImeMail = bpk.getMailUsernameThread();
            lozinkaMail = bpk.getMailPasswordThread();
            spavanje = Integer.parseInt(bpk.getMailTimeSecThreadCycle()) * 1000;
            port = Integer.parseInt(bpk.getMailImapPort());
            nPorukaZaCitanje = Integer.parseInt(bpk.getMailNumMessagesToRead());
            nazivPrivitka = bpk.getMailAttachmentFilename();
            urlBaze = bpk.getServerDatabase() + bpk.getUserDatabase();
            korImeBaza = bpk.getUserDatabase();
            lozinkaBaza = bpk.getUserPassword();
            uprProgram = bpk.getDriverDatabase();
            mailFolder = bpk.getMailFolderNWTiS();
            datotekaLog = bpk.getMailThreadCycleLogFilename();

            Class.forName(uprProgram);
            return true;
        } catch (ClassNotFoundException | NumberFormatException ex) {
            System.err.println("Greška kod dohvatiPodatkeIzKonfiguracije");
            return false;
        }
    }

    private void postaviFoldere() {
        try {
            properties.put("mail.smtp.host", posluzitelj);
            session = Session.getInstance(properties, null);
            store = session.getStore("imap");
            store.connect(posluzitelj, port, korImeMail, lozinkaMail);
            folderInbox = store.getFolder("INBOX");
            folderInbox.open(Folder.READ_WRITE);

            folderNWTiS = store.getFolder(mailFolder);
            if (!folderNWTiS.exists()) {
                folderNWTiS.create(Folder.HOLDS_MESSAGES);
            }
        } catch (MessagingException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void citajPorukePoGrupama() throws NoSuchProviderException, MessagingException {
        postaviFoldere();

        if (folderInbox.getUnreadMessageCount() > 0) {
            int ukupanBrojPoruka = folderInbox.getMessageCount();
            brojNeprocitanihPoruka = 0;

            for (int i = 0; i < ukupanBrojPoruka; i += nPorukaZaCitanje) {
                messages.clear();
                if (ukupanBrojPoruka < i + nPorukaZaCitanje) {
                    messages.addAll(Arrays.asList(folderInbox.getMessages(i + 1, ukupanBrojPoruka)));
                } else {
                    messages.addAll(Arrays.asList(folderInbox.getMessages(i + 1, i + nPorukaZaCitanje)));
                }
                odrediNWTiSPoruke(messages);
            }
        }
        folderInbox.close(true);
        store.close();
    }

    private void odrediNWTiSPoruke(List<Message> messages) {
        for (Message message : messages) {
            try {
                procitana = message.isSet(Flags.Flag.SEEN);
                if (!procitana) {
                    brojNeprocitanihPoruka++;
                    boolean postojiPrivitak = provjeriPrivitak(message);

                    //tocno jedan privitak tocno odredenog naziva, NWTiS poruka
                    if (postojiPrivitak && brojPrivitaka == 1 && nazivTrenutnogPrivitka.equalsIgnoreCase(nazivPrivitka)) {
                        if (ispravnaNWTiSPoruka(part)) {
                            provjeriPrijeUpisaUBazu(sadrzajPrivitka);
                            int trenutniId = jsonSadrzaj.get("id").getAsInt();
                            dodajUDnevnik(trenutniId, sadrzajPrivitka);
                        } else {
                            message.setFlag(Flags.Flag.SEEN, true);
                            brojNeispravnih++;
                        }
                        prebaciPorukuUNWTiSFolder(message);
                    } else {
                        message.setFlag(Flags.Flag.SEEN, procitana);
                    }
                }
            } catch (MessagingException ex) {
                Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void prebaciPorukuUNWTiSFolder(Message message) {
        try {
            Message[] messages = new Message[1];
            messages[0] = message;
            folderInbox.copyMessages(messages, folderNWTiS);

            folderInbox.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
        } catch (MessagingException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean provjeriPrivitak(Message message) {
        try {
            brojPrivitaka = 0;
            nazivTrenutnogPrivitka = "";
            String tipSadrzaja = message.getContentType();

            if (tipSadrzaja.contains("multipart")) {
                Multipart multiPart = (Multipart) message.getContent();
                int ukupanBrojDijelova = multiPart.getCount();

                for (int nDio = 0; nDio < ukupanBrojDijelova; nDio++) {
                    part = (MimeBodyPart) multiPart.getBodyPart(nDio);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        brojPrivitaka++;
                        nazivTrenutnogPrivitka = part.getFileName();
                    }
                }
                return true;
            } else {
                return false;
            }
        } catch (MessagingException | IOException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private boolean ispravnaNWTiSPoruka(MimeBodyPart part) {
        sadrzajPrivitka = ucitajSadrzajPrivitka(part);

        if (sadrzajPrivitka != null) {
            boolean ispravanFormat = provjeriFormat(part);
            boolean ispravanSadrzaj = provjeriSadrzaj(sadrzajPrivitka);
            jsonSadrzaj = provjeriJsonFormat(sadrzajPrivitka);

            if (ispravanFormat && ispravanSadrzaj && jsonSadrzaj != null) {
                return true;
            } else {
                System.err.println("Nije dobar format ili regex ne prolazi!");
                return false;
            }
        } else {
            return false;
        }
    }

    private String ucitajSadrzajPrivitka(MimeBodyPart part) {
        InputStream input = null;
        StringBuilder sadrzaj = null;

        try {
            input = part.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

            sadrzaj = new StringBuilder();
            int znak;

            while ((znak = in.read()) != -1) {
                sadrzaj.append((char) znak);
            }

            in.close();
        } catch (IOException | MessagingException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        System.out.println("sadrzaj: " + sadrzaj.toString());
        return sadrzaj.toString();
    }

    private boolean provjeriSadrzaj(String sadrzaj) {
        String sintaksa = "^\\{\"id\": [1-9][0-9]{0,3}, \"komanda\": \"(dodaj|azuriraj)\""
                + "(, \"[\\wĐŠŽĆČđšžćč ]{1,30}\": (\"[\\wĐŠŽĆČđšžćč ]{1,30}\"|\\d\\d{0,2}\\.\\d\\d?|\\d\\d{0,2})){1,5}"
                + ", \"vrijeme\": \"((\\d{4}\\.(1[012]|0[13-9])\\.(3[01]|[12][0-9]|0[1-9]))"
                + "|((\\d{4}\\.02\\.([12][0-9]|0[1-9]))))"
                + " ([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]\"\\}$";

        Pattern pattern = Pattern.compile(sintaksa);
        Matcher m = pattern.matcher(sadrzaj);
        boolean status = m.matches();
        return status;
    }

    private boolean provjeriFormat(MimeBodyPart part) {
        try {
            if (part.getContentType().toLowerCase().contains("text/json")
                    || part.getContentType().toLowerCase().contains("application/json")) {
                return true;
            } else {
                return false;
            }
        } catch (MessagingException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private Timestamp dohvatiVrijemeTimestamp() {
        try {
            String vrijemeString = jsonSadrzaj.get("vrijeme").getAsString();
            SimpleDateFormat formatVremena = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
            Date vrijeme = formatVremena.parse(vrijemeString);
            Timestamp vrijemeTimestamp = new Timestamp(vrijeme.getTime());
            return vrijemeTimestamp;
        } catch (ParseException ex) {
            return null;
        }
    }

    private void provjeriPrijeUpisaUBazu(String sadrzaj) {
        int trenutniId = jsonSadrzaj.get("id").getAsInt();
        String komanda = jsonSadrzaj.get("komanda").getAsString();

        String upit = "SELECT COUNT(*) AS Broj FROM uredaji WHERE id=" + trenutniId + ";";
        boolean uspjesnoIzvrsenUpit = pokusajIzvrsiUpit(upit);
        Timestamp vrijemeTimestamp = dohvatiVrijemeTimestamp();

        if (uspjesnoIzvrsenUpit && vrijemeTimestamp != null) {
            if (komanda.equalsIgnoreCase("dodaj")) {
                dodajUredajUBazu(trenutniId, sadrzaj, vrijemeTimestamp);
            } else if (komanda.equalsIgnoreCase("azuriraj")) {
                azurirajUredajUBazi(trenutniId, sadrzaj, vrijemeTimestamp);
            }
        }
    }

    private JsonObject provjeriJsonFormat(String sadrzaj) {
        try {
            jsonSadrzaj = new JsonParser().parse(sadrzaj).getAsJsonObject();
            return jsonSadrzaj;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    private boolean pokusajIzvrsiUpit(String upit) {
        try {
            Connection con = DriverManager.getConnection(urlBaze, korImeBaza, lozinkaBaza);
            Statement stat = con.createStatement();

            if (upit.toLowerCase().startsWith("select count")) {
                ResultSet rez = stat.executeQuery(upit);
                brojRedova.clear();

                while (rez.next()) {
                    brojRedova.add(Integer.parseInt(rez.getString("Broj")));
                }
                rez.close();
            } else if (upit.toLowerCase().startsWith("select")) {
                ResultSet rez = stat.executeQuery(upit);
                dohvaceniSadrzaj.clear();

                while (rez.next()) {
                    dohvaceniSadrzaj.add(rez.getString("sadrzaj"));
                }
                rez.close();
            } else {
                stat.execute(upit);
            }

            stat.close();
            con.close();
            return true;
        } catch (SQLException ex) {
            System.err.println("SQLException: " + ex.getMessage());
            return false;
        }
    }

    private String dohvatiNaziv() {
        String naziv;
        try {
            naziv = jsonSadrzaj.get("naziv").getAsString();
        } catch (NullPointerException ex) {
            naziv = "";
        }
        return naziv;
    }

    private void dodajUredajUBazu(int trenutniId, String sadrzaj, Timestamp vrijemeDodavanja) {
        String naziv = dohvatiNaziv();

        //id ne smije vec postojati u bazi:
        //count je vratio sam 1 red i vrijednost mu je 0
        if (brojRedova != null && brojRedova.size() == 1 && brojRedova.contains(0)) {
            String upit = "INSERT INTO uredaji (id, naziv, sadrzaj) "
                    + "VALUES (" + trenutniId + ", '" + naziv + "', '" + sadrzaj + "');";

            if (pokusajIzvrsiUpit(upit)) {
                System.out.println("USPJEŠNO DODAVANJE uredaja");
                brojDodanihIOT++;
            } else {
                System.err.println("NEUSPJEŠNO DODAVANJE uredaja");
            }
        } else {
            System.err.println("Id " + trenutniId + " već postoji u bazi!");
        }
    }

    private void azurirajUredajUBazi(int trenutniId, String sadrzaj, Timestamp vrijemePromjene) {
        //samo 1 zadani id mora postojati u bazi:
        //count je vratio sam 1 red i vrijednost mu je 1 što znači da postoji samo 1 takav id u bazi
        if (brojRedova != null && brojRedova.size() == 1 && brojRedova.contains(1)) {
            String upit = "SELECT sadrzaj FROM uredaji WHERE id=" + trenutniId + ";";

            if (pokusajIzvrsiUpit(upit) && dohvaceniSadrzaj != null && dohvaceniSadrzaj.size() == 1) {
                String trenutniSadrzaj = dohvaceniSadrzaj.get(0);
                JsonObject jsonSadrzajIzBaze = provjeriJsonFormat(trenutniSadrzaj);
                jsonSadrzaj = provjeriJsonFormat(sadrzaj);

                String noviSadrzaj = azurirajJson(jsonSadrzajIzBaze);
                jsonSadrzaj = provjeriJsonFormat(noviSadrzaj);
                String naziv = dohvatiNaziv();
                upit = "UPDATE uredaji SET naziv='" + naziv + "', sadrzaj='" + noviSadrzaj + "', "
                        + "vrijeme_promjene='" + vrijemePromjene + "' WHERE id=" + trenutniId + ";";

                if (pokusajIzvrsiUpit(upit)) {
                    System.out.println("USPJEŠNO AŽURIRANO");
                    brojAzuriranihIOT++;
                } else {
                    System.err.println("NEUSPJEŠNO AŽURIRANO");
                }
            }
        } else {
            System.err.println("Id " + trenutniId + " još ne postoji u bazi!");
        }
    }

    private String azurirajJson(JsonObject jsonSadrzajIzBaze) {
        if (jsonSadrzaj != null && jsonSadrzajIzBaze != null) {
            jsonSadrzajIzBaze.remove("vrijeme");
            //obrisem vrijeme tako da se novo vrijeme doda na kraj
            //prolazim kroz sve atribute koje je korisnik poslao i ako vec takav atribut postoji u bazi
            //onda mu se vrijednost samo zamjeni (to add sam radi)
            //a ako takav atribut ne postoji, on se doda na kraj
            //posto je vrijeme uvijek zadnji atribut (mora biti ak je proso kroz regex), onda se ono doda na kraj
            for (String kljucJsona : jsonSadrzaj.keySet()) {
                jsonSadrzajIzBaze.add(kljucJsona, jsonSadrzaj.get(kljucJsona));
            }
        }

        StringBuilder noviSadrzaj = new StringBuilder();
        noviSadrzaj.append("{");
        for (String kljucJsona : jsonSadrzajIzBaze.keySet()) {
            noviSadrzaj.append("\"").append(kljucJsona).append("\"").append(": ").append(jsonSadrzajIzBaze.get(kljucJsona));
            if (!kljucJsona.equalsIgnoreCase("vrijeme")) {
                noviSadrzaj.append(", ");
            }
        }
        noviSadrzaj.append("}");
        return noviSadrzaj.toString();
    }

    private void dodajUDnevnik(int trenutniId, String sadrzaj) {
        String upit = "INSERT INTO dnevnik (id, sadrzaj) VALUES (" + trenutniId + ", '" + sadrzaj + "');";

        if (pokusajIzvrsiUpit(upit)) {
            System.out.println("USPJEŠNO DODANO u dnevnik");
        } else {
            System.err.println("NEUSPJEŠNO DODANO u dnevnik");
        }
    }

    private void dodajPodatkeULog() {
        try {
            String putanja = context.getRealPath("/WEB-INF");
            File datoteka = new File(putanja + File.separator + datotekaLog);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(datoteka, true), StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            sb.append("Obrada poruka broj: ").append(brojObradePoruka).append(System.lineSeparator());
            sb.append("Obrada započela u: ").append(pocetakObrade).append(System.lineSeparator());
            sb.append("Obrada završila u: ").append(krajObrade).append(System.lineSeparator());
            sb.append("Trajanje obrade u ms: ").append(trajanjeObrade).append(System.lineSeparator());
            sb.append("Broj poruka: ").append(brojNeprocitanihPoruka).append(System.lineSeparator());
            sb.append("Broj dodanih IOT: ").append(brojDodanihIOT).append(System.lineSeparator());
            sb.append("Broj ažuriranih IOT: ").append(brojAzuriranihIOT).append(System.lineSeparator());
            sb.append("Broj neispravnih poruka: ").append(brojNeispravnih).append(System.lineSeparator());
            sb.append(System.lineSeparator());

            bw.write(sb.toString());
            bw.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ObradaPoruka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
