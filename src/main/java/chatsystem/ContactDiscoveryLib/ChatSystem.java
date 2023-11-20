package chatsystem.ContactDiscoveryLib;

import chatsystem.Main;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Scanner;

//Nos Headers sont sur 4 caracs
//NB on aurait pu optimiser et ne pas utiliser de ID vu que le pseudo est unique ( du coup dans notre cas on utilise les deux pour l'instant )


public class ChatSystem { //instance de chat sur une machine

    //Attribut
    ContactsManager cm;
    Contact monContact;
    DatagramSocket socketBroadcast;
    ListeningThread LT;
    UpdateContactsThread UCT;
    private DataOutputStream out;
    private DataInputStream in;
    private InetAddress address;
    private int port;
    private boolean pseudoAccepted;
    private boolean IDAccepted;
    public void initSocket_Broadcast() {
    }

    //Constructeur
    public ChatSystem(String ip, int port){
        this.port = port;
        this.cm = new ContactsManager();
        initSocket(ip, port);
    }
    //Methods

    //Getter and setter
    public Contact getMonContact() {
        return monContact;
    }
    public void setMonContact(String pseudo, int id) {
        this.monContact.setPseudo(pseudo);
        this.monContact.setId(id);
    }





    //OTHER method
    public void initSocket(String addr, int port){
        try {
            this.socketBroadcast = new DatagramSocket(port);
            this.address = InetAddress.getByName(addr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private int chooseID(){
        IDAccepted = false;
        int ite = 0;
        int id = -1;
        while (!IDAccepted && ite < 10) {
            ite++;
            id = cm.getIdMax() + 1;
            cm.setIdMax(id);
            IDAccepted = true;
            for (int p : Main.portList) { // TODO: Changer ce mécanisme dégueulasse quand on passera au cas réel sur des IPs
                if (p == this.port) continue;
                send_DEID(p, id);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if(IDAccepted){
            return id;
        }else { return -1; }
        
    }



    private String choosePseudo(String pseudo){
        pseudoAccepted = true;
        for(int p : Main.portList){ // TODO: Changer ce mécanisme dégueulasse quand on passera au cas réel sur des IPs
            send_DEPS(p, pseudo);
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if(!pseudoAccepted){
            System.out.println("Pseudo non disponible, fermeture du chat");
            closeChat(); // Fermeture du chat, solution temporaire
        }

        return pseudo;
    }

    public void start(String pseudoAsked){
        startListening();
        startUpdateContacts();
        int id = chooseID();
        String pseudo = choosePseudo(pseudoAsked);
        this.monContact = new Contact(pseudo, id);

    }



    public void send_INCO(DatagramPacket rep) {

        byte[] buf = new byte[256];
        String msg_to_send = "INCO" + ":" + monContact.getPseudo() + ":" + monContact.getId();
        buf = msg_to_send.getBytes();
        rep.setData(buf);
        //DatagramPacket outPacket = new DatagramPacket(buf, buf.length, this.address, this.port);
        try {
            this.socketBroadcast.send(rep);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send_DECO(int port_dest) {//on test en localhost, donc on passe le port en arg
        byte[] buf = new byte[256];
        String msg_to_send = "DECO";
        buf = msg_to_send.getBytes();

        DatagramPacket outPacket = new DatagramPacket(buf, buf.length, this.address, port_dest);
        try {
            this.socketBroadcast.send(outPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send_DEPS(int port_dest, String pseudo){
        byte[] buf = new byte[256];
        String msg_to_send = "DEPS" + ":" + pseudo;
        buf = msg_to_send.getBytes();

        DatagramPacket outPacket = new DatagramPacket(buf, buf.length, this.address, port_dest);
        try {
            this.socketBroadcast.send(outPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send_REPS(DatagramPacket rep) {

            byte[] buf = new byte[256];
            String msg_to_send = "REPS";
            buf = msg_to_send.getBytes();
            rep.setData(buf);
            //DatagramPacket outPacket = new DatagramPacket(buf, buf.length, this.address, this.port);
            try {
                this.socketBroadcast.send(rep);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }

    public void send_DEID(int port_dest, int id_voulu){
        byte[] buf = new byte[256];
        String msg_to_send = "DEID"+ ":" + id_voulu;
        buf = msg_to_send.getBytes();

        DatagramPacket outPacket = new DatagramPacket(buf, buf.length, this.address, port_dest);
        try {
            this.socketBroadcast.send(outPacket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void send_REID(DatagramPacket rep,int id) {

        byte[] buf = new byte[256];
        String msg_to_send = "REID" + ":" + id;
        buf = msg_to_send.getBytes();
        rep.setData(buf);
        DatagramPacket outPacket = new DatagramPacket(buf, buf.length, this.address, this.port);
        try {
            this.socketBroadcast.send(rep);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public DatagramPacket listen(){
        byte[] buf = new byte[256];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        try {
            socketBroadcast.receive(packet);
            //System.out.println("j'ai receive un paquet");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return packet;
    }

    public void startListening(){
        this.LT = new ListeningThread();
        LT.start();
    }

    public void startUpdateContacts(){
        this.UCT = new UpdateContactsThread();
        UCT.start();
    }

    public void afficherListeContacts(){
        this.cm.afficherListe();
    }

    public void closeChat(){
        LT.interrupt();
        UCT.interrupt();
        System.exit(1);
    }


    public class ListeningThread extends Thread {
        HeaderDatagram header ;
        @Override
        public void run() {
            DatagramPacket rep;
            //System.out.println("je run un Thread de listen");
            while(true){

                //System.out.println("debut while");
                rep = listen(); //réécrire la rep et la renvoyer
                //System.out.println("pdu reçu");
                String msg = new String(rep.getData(), 0, rep.getLength());
                switch(DatagramManager.getHeader(msg)){
                    case INCO: // On reçoit une info de contact, il faut l'ajouter à notre liste de contacts

                        cm.updateContact(DatagramManager.INCO_to_Contact(msg));
                        //System.out.println("Envoie Contact");

                        break;
                    case DECO: //On reçoit une demande de contact, on souhaite renvoyer notre contact au destinaire
                        //System.out.println("j'ai reçu DECO");
                        send_INCO(rep);
                        //System.out.println("j'ai envoyé INCO");
                        break;
                    case REPS: // Si le pseudo demandé est refusé par un contact
                        pseudoAccepted = false;
                        break;
                    case DEPS: //
                        String his_pseudo = DatagramManager.XXPS_to_pseudo(msg);
                        if (cm.search_contact_by_pseudo(his_pseudo)!=null){ //Si l'id existe déjà
                            send_REPS(rep);
                        } //else on fait rien il ( il supposera que oui tant que personne lui dit non )
                        send_REPS(rep);
                        break;
                    case DEID: //si l'ID existe il refuse avec un id_conseiller ( le max + 1 de sa liste ) si l'ID n'existe pas, il renvoie rien
                        int his_id = DatagramManager.XXID_to_id(msg);
                        if (cm.search_contact_by_id(his_id)!=null){ //Si l'id existe déjà
                            send_REID(rep,his_id+1); //on renvoie refus et on donne l'id conseillé
                            cm.setIdMax(his_id+1); //et on met à jour l'id_max
                        } //else on fait rien il ( il supposera que oui tant que personne lui dit non )
                        break;

                    case REID: //Si on reçoit un refus alors notre ID n'est pas accepté et c'est la fonction start() qui relancera une requete si besoin
                        int suggest_id = DatagramManager.XXID_to_id(msg);
                        IDAccepted = false;
                        cm.setIdMax(suggest_id);

                    default:
                        System.out.println("Header inconnu");

                }

            }
        }
    }

    public class UpdateContactsThread extends Thread {
        @Override
        public void run() {
            while(true){
                for(int p : Main.portList){ // TODO: Changer ce mécanisme déguelasse quand on passera au cas réel sur un intranet
                    send_DECO(p);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                cm.decreaseTTL();
            }
        }
    }

}
