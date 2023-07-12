/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.ujaen.ssmmaa.agentes;

import es.ujaen.ssmmaa.agentes.Constantes.OrdenComanda;
import es.ujaen.ssmmaa.gui.AgenteClienteJFrame;
import jade.core.AID;
import jade.core.Agent;

import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Random;

public class AgenteCliente extends Agent {

    private AgenteClienteJFrame myGui;
    private ArrayList<Constantes.Plato> servicios = new ArrayList<>();

    protected void setup() {

        // Crear y mostrar la interfaz gráfica del agente
        myGui = new AgenteClienteJFrame(this);
        myGui.setVisible(true);
        myGui.presentarSalida("Se inicia la ejecución de " + this.getName() + "\n");

        // Registro del agente en las Páginas Amarillas
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("ServicioCliente");
        sd.setName("AgenteCliente");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Inicializar los servicios que el cliente solicitará
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String[] parametros = ((String) args[0]).split(":");
            Random rand = new Random(); // Generador de números aleatorios
            DecimalFormat df = new DecimalFormat("#.#"); // Formateador para limitar a un decimal
            DecimalFormatSymbols dfs = new DecimalFormatSymbols();
            dfs.setDecimalSeparator('.'); // Configurar el símbolo decimal a un punto
            df.setDecimalFormatSymbols(dfs);
            for (String parametro : parametros) {
                OrdenComanda orden = OrdenComanda.valueOf(parametro);
                String nombrePlato = parametro; // Nombre único para cada plato
                double precioPlato = 10.0 + (30.0 - 10.0) * rand.nextDouble(); // Precio aleatorio entre 10 y 30
                precioPlato = Double.valueOf(df.format(precioPlato)); // Limitar a un decimal
                Constantes.Plato plato = new Constantes.Plato(nombrePlato, orden, precioPlato);
                servicios.add(plato);
            }
        }

        // Comportamiento cíclico para solicitar servicios al AgenteRestaurante
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                if (!servicios.isEmpty()) {

                    // Crear y enviar un mensaje de solicitud al AgenteRestaurante
                    ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                    msg.addReceiver(new AID("AgenteRestaurante", AID.ISLOCALNAME));
                    String nombrePlato = servicios.get(0).getNombre() + " con precio " + servicios.get(0).getPrecio() + " euros";
                    msg.setContent("Necesito un servicio: " + nombrePlato);
                    msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    send(msg);

                    // Preparar el patrón de mensaje para recibir la respuesta del AgenteRestaurante
                    MessageTemplate mt = MessageTemplate.and(
                            MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                            MessageTemplate.or(
                                    MessageTemplate.MatchPerformative(ACLMessage.AGREE),
                                    MessageTemplate.or(
                                            MessageTemplate.MatchPerformative(ACLMessage.REFUSE),
                                            MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                                    )
                            )
                    );

                    // Recibir la respuesta del AgenteRestaurante
                    ACLMessage reply = myAgent.blockingReceive(mt);
                    if (reply != null) {
                        String contenido = reply.getContent();
                        if (reply.getPerformative() == ACLMessage.AGREE) {
                            // El AgenteRestaurante ha aceptado la solicitud de servicio
                            myGui.presentarSalida("El restaurante ha aceptado el servicio del plato " + nombrePlato + ".\n");
                            // Esperar la confirmación de que el servicio ha sido completado
                            mt = MessageTemplate.and(
                                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                                    MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                            );
                            reply = myAgent.blockingReceive(mt);
                            if (reply != null) {
                                contenido = reply.getContent();
                                if ("Servicio completado".equals(contenido)) {
                                    // El servicio ha sido completado, eliminarlo de la lista de servicios
                                    servicios.remove(0);
                                    myGui.presentarSalida("    El servicio del plato ha sido completado\n");
                                    if (servicios.isEmpty()) {
                                        // No hay más servicios, eliminar el agente
                                        try {
                                            Thread.sleep(Constantes.TIEMPO_FINALIZACION_CLIENTE);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                        myAgent.doDelete();
                                    } else {
                                        block(Constantes.TIEMPO_ESPERA);
                                    }
                                }
                            }
                        } else if (reply.getPerformative() == ACLMessage.REFUSE) {
                            // El AgenteRestaurante ha rechazado la solicitud de servicio
                            myGui.presentarSalida("    El restaurante ha rechazado el servicio del plato\n");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            myAgent.doDelete();
                        } else if (reply.getPerformative() == ACLMessage.INFORM && "No más servicios".equals(contenido)) {
                            // El AgenteRestaurante ha informado de que no tiene más servicios disponibles
                            myGui.presentarSalida("El restaurante no tiene más servicios disponibles\n");
                            try {
                                Thread.sleep(Constantes.TIEMPO_FINALIZACION_CLIENTE);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            myAgent.doDelete();
                        }
                    }
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
