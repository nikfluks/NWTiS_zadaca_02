package org.foi.nwtis.nikfluks.web.slusaci;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.web.dretve.ObradaPoruka;

@WebListener
public class SlusacAplikacije implements ServletContextListener {

    ObradaPoruka obradaPoruka;
    ServletContext context;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        context = sce.getServletContext();

        String datoteka = context.getInitParameter("konfiguracija");
        String putanja = context.getRealPath("/WEB-INF") + java.io.File.separator;
        BP_Konfiguracija bpk = new BP_Konfiguracija(putanja + datoteka);

        context.setAttribute("BP_Konfig", bpk);

        obradaPoruka = new ObradaPoruka(context);
        obradaPoruka.start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        context = sce.getServletContext();
        context.removeAttribute("BP_Konfig");
        obradaPoruka.interrupt();
    }
}
