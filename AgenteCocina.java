/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.ujaen.ssmmaa.agentes;

import es.ujaen.ssmmaa.gui.AgenteCocinaJFrame;

// AgenteCocina.java
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.HashMap;
import java.util.Map;

public class AgenteCocina extends Agent {

    private AgenteCocinaJFrame myGui;
    private Map<Constantes.OrdenComanda, Integer> cantidadPlatos = new HashMap<>(); //cantidad de platos que podrá preparar antes de finalizar 

    protected void setup() {
        // Crear y mostrar la interfaz gráfica del agente
        myGui = new AgenteCocinaJFrame(this);
        myGui.setVisible(true);
        myGui.presentarSalida("Se inicia la ejecución de " + this.getName() + "\n");

        // Registro del agente en las Páginas Amarillas
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("ServicioCocina");
        sd.setName("AgenteCocina");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Recuperar los argumentos y establecer la cantidad de platos que el agente puede preparar
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String[] parametros = ((String) args[0]).split(":");
            for (int i = 0; i < parametros.length; i++) {
                int cantidad = Integer.parseInt(parametros[i]);
                Constantes.OrdenComanda orden = Constantes.OrdenComanda.values()[i];
                cantidadPlatos.put(orden, cantidad);
            }
        }

        // Comportamiento cíclico para procesar las solicitudes de preparación de platos
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                // Esperar la solicitud de preparación de un plato del AgenteRestaurante
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                        MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST));

                ACLMessage msg = blockingReceive(mt);
                if (msg != null) {
                    // Procesar la solicitud
                    String contenido = msg.getContent();
                    if (contenido.startsWith("Preparar plato")) {
                        // Si el plato está disponible, prepararlo
                        String nombrePlato = contenido.substring("Preparar plato".length());
                        myGui.presentarSalida("ORDEN COMANDO: Plato " + nombrePlato +".\n");
                        try {
                            Thread.sleep(Constantes.TIEMPO_COMANDA); 
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        myGui.presentarSalida("    Preparando el plato ...\n");
                        try {
                            Thread.sleep(Constantes.TIEMPO_PREPARACION_PLATO); // Simular el tiempo de preparación del plato
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // Enviar un mensaje FIPA-INFORM al AgenteRestaurante para indicar que el plato ha sido preparado
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent("Plato preparado");
                        reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                        send(reply);
                        myGui.presentarSalida("    El plato ha sido cocinado\n");
                    } else if (contenido.equals("Finalizar cocina")) {
                        // Si el mensaje es "Finalizar cocina", finalizar el agente
                        myGui.presentarSalida("El restaurante ha finalizado sus servicios. Finalizando la cocina...\n");
                        try {
                            Thread.sleep(Constantes.TIEMPO_FINALIZACION_COCINA); 
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        myAgent.doDelete();
                    }
                } else {
                    block();
                }
            }
        });
    }

    protected void takeDown() {
        // Desregistro del agente de las Páginas Amarillas
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Cerrar la interfaz gráfica del agente
        myGui.dispose();

        System.out.println("AgenteCocina: Terminando.");
    }

}
