// Pong — Programmazione Orientata agli Oggetti e Laboratori, Udine, a.a. 2026-27
package pong;

import geometria.Rettangolo;
import giocatori.GiocatoreAutomatico;
import java.util.Random;
import ostacoli.Livello;
import ostacoli.MotoreDeiLivelli;
import ostacoli.Ostacoli;
import ostacoli.Ostacolo;
import stelle.Raccolta;
import stelle.Stella;

/**
 * Una partita a un livello: voi a sinistra, il giocatore automatico a destra, due racchette,
 * una palla, gli ostacoli del livello, le stelle e i conti di chi vince.
 * Tiene anche il gioco a livelli (Livelli): a che livello si è e quante vite restano.
 */
class Partita {
    /** Si gioca a 5, con almeno due punti di scarto (5 a 4 non basta, 6 a 4 sì). */
    static final int PUNTI_PER_VINCERE = 5;
    static final int SCARTO_MINIMO = 2;
    static final int VITE_INIZIALI = 3;

    /** Lato della stella in pixel. */
    static final int LATO_STELLA = 20;
    /** Fotogrammi che passano senza stella prima che ne compaia una nuova (3 secondi). */
    static final int ATTESA_STELLA = 180;

    private final Campo campo;
    private final Giocatore giocatoreSinistra;
    private Giocatore giocatoreDestra;
    private final Racchetta racchettaSinistra;
    private final Racchetta racchettaDestra;
    private final Palla palla;
    private final Punteggio punteggio = new Punteggio(PUNTI_PER_VINCERE, SCARTO_MINIMO);
    private final Livelli livelli = new Livelli(MotoreDeiLivelli.NUMERO_LIVELLI, VITE_INIZIALI, Lato.SINISTRA);
    private final Random random = new Random();
    private Raccolta raccolta = new Raccolta(Raccolta.STELLE_PER_UNA_VITA);
    private Livello livello;
    private Ostacoli ostacoli;
    private boolean iniziata;
    private boolean esitoRegistrato;
    private Stella stella;
    private int fotogrammiSenzaStella;

    /** Una partita fra umano, a sinistra, e il giocatore automatico, a destra; si comincia dal livello 1. */
    Partita(Campo campo, Giocatore umano) {
        this.campo = campo;
        this.giocatoreSinistra = umano;
        double centroY = campo.getAltezza() / 2.0 - Racchetta.ALTEZZA / 2.0;
        this.racchettaSinistra = new Racchetta(30, centroY);
        this.racchettaDestra = new Racchetta(campo.getLarghezza() - 30 - Racchetta.LARGHEZZA, centroY);
        this.palla = new Palla(campo.getLarghezza() / 2.0, campo.getAltezza() / 2.0);
        preparaLivello(livelli.getLivello());
    }

    /**
     * Prepara il livello numero: chiede al motore dei livelli ostacoli nuovi, velocità della palla ed
     * errore del giocatore automatico; rimette i punti a zero e la palla ferma al centro.
     * Pretende: la partita non è in corso; numero è fra 1 e MotoreDeiLivelli.NUMERO_LIVELLI.
     */
    void preparaLivello(int numero) {
        livello = MotoreDeiLivelli.livello(numero, campo.getLarghezza(), campo.getAltezza());
        ostacoli = livello.ostacoli();
        giocatoreDestra = new GiocatoreAutomatico(livello.erroreMassimo());
        punteggio.azzera();
        iniziata = false;
        esitoRegistrato = false;
        stella = null;
        fotogrammiSenzaStella = 0;
        palla.fermaIn(campo.getLarghezza() / 2.0, campo.getAltezza() / 2.0);
    }

    // Quello che lo Schermo può chiedere: solo leggere. Il Punteggio resta qui dentro.

    /** I punti del lato chi. */
    int getPunti(Lato chi) { return punteggio.getPunti(chi); }

    /** Chi ha vinto; null se la partita non è finita. */
    Lato getVincitore() { return punteggio.getVincitore(); }

    /** Vero se si sta giocando: la partita è cominciata e non è finita. */
    boolean isInCorso() { return iniziata && !punteggio.isFinita(); }

    /** Vero se la partita è cominciata (anche se poi è finita). */
    boolean isIniziata() { return iniziata; }

    Livello getLivello() { return livello; }
    Livelli getLivelli() { return livelli; }
    Ostacoli getOstacoli() { return ostacoli; }
    Raccolta getRaccolta() { return raccolta; }
    Stella getStella() { return stella; }

    /**
     * Fa cominciare la partita e lancia la palla. Se una partita era già stata giocata, prima
     * la prepara di nuovo: se il gioco è finito (completato o perso) si ricomincia dal livello 1
     * con tutte le vite; altrimenti si gioca il livello a cui si è arrivati (ostacoli nuovi, punti a zero).
     * Pretende: la partita non è in corso.
     */
    void avvia() {
        if (iniziata) {
            if (livelli.isFinito()) {
                livelli.ricomincia();
                raccolta = new Raccolta(Raccolta.STELLE_PER_UNA_VITA);
            }
            preparaLivello(livelli.getLivello());
        }
        iniziata = true;
        lanciaPalla(random.nextBoolean());
    }

    /** Lancia la palla dal centro, alla velocità del livello. */
    private void lanciaPalla(boolean versoDestra) {
        palla.lancia(campo.getLarghezza() / 2.0, campo.getAltezza() / 2.0, versoDestra, livello.velocitaPalla());
    }

    /** Un passo del gioco: viene chiamato 60 volte al secondo. */
    void aggiorna() {
        if (!isInCorso()) return;
        ostacoli.avanzaTutti();
        muoviRacchette();
        palla.muovi();
        controllaRimbalzi();
        controllaOstacoli();
        gestisciStella();
        controllaPunto();
        registraEsito();
    }

    /** Chiede a ciascun giocatore cosa vuole fare, e lo fa. Non sa chi sono i giocatori. */
    private void muoviRacchette() {
        Mossa mossaSinistra = giocatoreSinistra.decidiMossa(palla, racchettaSinistra);
        Mossa mossaDestra = giocatoreDestra.decidiMossa(palla, racchettaDestra);
        racchettaSinistra.muovi(mossaSinistra, campo);
        racchettaDestra.muovi(mossaDestra, campo);
    }

    private void controllaRimbalzi() {
        double y = palla.getPosizione().y();
        boolean toccaAlto = y - palla.getRaggio() <= 0 && palla.getVelocitaY() < 0;
        boolean toccaBasso = y + palla.getRaggio() >= campo.getAltezza() && palla.getVelocitaY() > 0;
        if (toccaAlto || toccaBasso) palla.rimbalzaVerticale();

        if (palla.tocca(racchettaSinistra) && palla.getVelocitaX() < 0) palla.rimbalzaSu(racchettaSinistra);
        if (palla.tocca(racchettaDestra) && palla.getVelocitaX() > 0) palla.rimbalzaSu(racchettaDestra);
    }

    /**
     * Se la palla tocca un ostacolo non rotto, rimbalza e l'ostacolo viene colpito (un ostacolo per passo).
     * Qui non si sa di che tipo è l'ostacolo: che cosa fa il colpo lo decide l'ostacolo.
     */
    private void controllaOstacoli() {
        Ostacolo colpito = ostacoli.ostacoloCheTocca(palla.getArea());
        if (colpito != null) {
            palla.rimbalzaSu(colpito.getArea());
            colpito.colpisci(ostacoli);
        }
    }

    /**
     * Le stelle. Se c'è una stella visibile, passa un fotogramma e, se la racchetta di chi gioca
     * la tocca, viene raccolta: ogni STELLE_PER_UNA_VITA stelle si guadagna una vita.
     * Altrimenti si aspetta ATTESA_STELLA fotogrammi e ne compare una nuova.
     */
    private void gestisciStella() {
        if (stella != null && stella.isVisibile()) {
            stella.avanza();
            if (stella.isVisibile() && racchettaSinistraTocca(stella.getArea())) {
                stella.raccogli();
                if (raccolta.aggiungi()) livelli.guadagnaVita();
            }
            return;
        }
        fotogrammiSenzaStella++;
        if (fotogrammiSenzaStella >= ATTESA_STELLA) {
            stella = nuovaStella();
            fotogrammiSenzaStella = 0;
        }
    }

    /** Una stella nuova nella corsia della racchetta sinistra, a un'altezza a caso. */
    private Stella nuovaStella() {
        double x = racchettaSinistra.getX() + (Racchetta.LARGHEZZA - LATO_STELLA) / 2.0;
        double y = random.nextDouble() * (campo.getAltezza() - LATO_STELLA);
        return new Stella(new Rettangolo(x, y, LATO_STELLA, LATO_STELLA), livello.durataStella());
    }

    /** Vero se la racchetta sinistra e il rettangolo r si sovrappongono. */
    private boolean racchettaSinistraTocca(Rettangolo r) {
        double rx = racchettaSinistra.getX();
        double ry = racchettaSinistra.getY();
        return rx < r.x() + r.larghezza() && r.x() < rx + racchettaSinistra.getLarghezza()
                && ry < r.y() + r.altezza() && r.y() < ry + racchettaSinistra.getAltezza();
    }

    /** Se la palla è uscita da un lato, il punto va all'altro lato. */
    private void controllaPunto() {
        double x = palla.getPosizione().x();
        if (x < 0) {
            punteggio.segnaPunto(Lato.DESTRA);
            dopoIlPunto(true);
        } else if (x > campo.getLarghezza()) {
            punteggio.segnaPunto(Lato.SINISTRA);
            dopoIlPunto(false);
        }
    }

    /** Dopo un punto: se la partita non è finita si serve verso chi ha subito il punto, altrimenti la palla si ferma. */
    private void dopoIlPunto(boolean versoSinistra) {
        if (punteggio.isFinita()) {
            palla.fermaIn(campo.getLarghezza() / 2.0, campo.getAltezza() / 2.0);
        } else {
            lanciaPalla(!versoSinistra);
        }
    }

    /** Quando la partita finisce, lo dice (una volta sola) a Livelli: livello vinto o vita persa. */
    private void registraEsito() {
        if (punteggio.isFinita() && !esitoRegistrato) {
            esitoRegistrato = true;
            livelli.partitaFinita(punteggio.getVincitore());
        }
    }

    Campo getCampo() { return campo; }
    Palla getPalla() { return palla; }
    Racchetta getRacchettaSinistra() { return racchettaSinistra; }
    Racchetta getRacchettaDestra() { return racchettaDestra; }
}