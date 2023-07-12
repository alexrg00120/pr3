/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.ujaen.ssmmaa.agentes;

import es.ujaen.ssmmaa.gui.AgenteRestauranteJFrame;
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

public class AgenteRestaurante extends Agent {

    private AgenteRestauranteJFrame myGui;
    private int capacidad; //capacidad de usuarios que puede antender hasta finalizar su servicio
    private int servicios;//numero de servicios que podrá dar antes de finalizar 

    protected void setup() {
        // Crear y mostrar la interfaz gráfica del agente
        myGui = new AgenteRestauranteJFrame(this);
        myGui.setVisible(true);
        myGui.presentarSalida("Se inicia la ejecución de " + this.getName() + "\n");

        // Registro del agente en las Páginas Amarillas
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("ServicioRestaurante");
        sd.setName("AgenteRestaurante");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Inicializar la capacidad y los servicios del agente
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String[] parametros = ((String) args[0]).split(":");
            capacidad = Integer.parseInt(parametros[0]);
            servicios = Integer.parseInt(parametros[1]);
        }

        // Comportamiento del agente
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                if (servicios > 0) {
                    // Esperar la solicitud de servicio del AgenteCliente
                    MessageTemplate mt = MessageTemplate.and(
                            MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                            MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
                    );
                    ACLMessage mensaje = myAgent.blockingReceive(mt);
                    if (mensaje != null) {
                        String contenido = mensaje.getContent();
                        if (contenido.startsWith("Necesito un servicio: ")) {
                            String nombrePlato = contenido.substring("Necesito un servicio: ".length());

                            // Enviar un mensaje de acuerdo (AGREE) al AgenteCliente
                            ACLMessage agree = mensaje.createReply();
                            agree.setPerformative(ACLMessage.AGREE);
                            agree.setContent("Servicio aceptado");
                            agree.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                            send(agree);

                            // Enviar una solicitud de preparación de plato al AgenteCocina
                            ACLMessage solitudCocina = new ACLMessage(ACLMessage.REQUEST);
                            solitudCocina.addReceiver(new AID("AgenteCocina", AID.ISLOCALNAME));
                            solitudCocina.setContent("Preparar plato" + nombrePlato);
                            solitudCocina.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                            send(solitudCocina);

                            myGui.presentarSalida("Solicitud recibida de: " + mensaje.getSender().getName()+"\n");
                            myGui.presentarSalida("Plato solicitado: " + nombrePlato + ".\n");

                            // Esperar la confirmación de que el plato ha sido preparado
                            mt = MessageTemplate.and(
                                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                                    MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                            );
                            ACLMessage respuestaCocina = blockingReceive(mt);

                            if (respuestaCocina != null) {
                                myGui.presentarSalida("    Plato preparado\n");
                                contenido = respuestaCocina.getContent();
                                if ("Plato preparado".equals(contenido)) {
                                    try {
                                        Thread.sleep(Constantes.TIEMPO_SERVICIO_RESTAURANTE);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    // Enviar un mensaje FIPA-INFORM al AgenteCliente para indicar que el servicio ha sido completado
                                    ACLMessage inform = mensaje.createReply();
                                    inform.setPerformative(ACLMessage.INFORM);
                                    inform.setContent("Servicio completado");
                                    inform.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                                    send(inform);

                                    servicios--;
                                    myGui.presentarSalida("    Servicio completado\n");
                                    try {
                                        Thread.sleep(Constantes.TIEMPO_ESPERA);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                        } else {
                            block();
                        }
                    }
                } else {
                    
                    myGui.presentarSalida("El restaurante ha accedido el límite de servicios\n");

                    // Si no hay más servicios disponibles, enviar un mensaje FIPA-REQUEST al AgenteCocina para finalizar la cocina
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.addReceiver(new AID("AgenteCocina", AID.ISLOCALNAME));
                    request.setContent("Finalizar cocina");
                    request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    send(request);

                    // Para avisar a cliente que ya no hay mas servicios
                    ACLMessage informCliente = new ACLMessage(ACLMessage.INFORM);
                    informCliente.addReceiver(new AID("AgenteCliente", AID.ISLOCALNAME));
                    informCliente.setContent("No más servicios");
                    informCliente.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    send(informCliente);

                    try {
                        Thread.sleep(Constantes.TIEMPO_FINALIZACION_RESTAURANTE);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    myAgent.doDelete();
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

        System.out.println("AgenteRestaurante: Terminando.");
    }

}
