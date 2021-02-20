//|:AgenPro|=_BEGIN
//|AgenPro:|

import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.TransportMapping;
import org.snmp4j.agent.*;
import org.snmp4j.agent.cfg.EngineBootsCounterFile;
import org.snmp4j.agent.io.DefaultMOPersistenceProvider;
import org.snmp4j.agent.io.MOInput;
import org.snmp4j.agent.io.MOInputFactory;
import org.snmp4j.agent.io.prop.PropertyMOInput;
import org.snmp4j.agent.mo.DefaultMOFactory;
import org.snmp4j.agent.mo.MOFactory;
import org.snmp4j.agent.mo.util.VariableProvider;
import org.snmp4j.agent.request.Request;
import org.snmp4j.agent.request.RequestStatus;
import org.snmp4j.agent.request.SubRequest;
import org.snmp4j.agent.request.SubRequestIterator;
import org.snmp4j.log.JavaLogFactory;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.*;
import org.snmp4j.transport.TransportMappings;
import org.snmp4j.util.ArgumentParser;
import org.snmp4j.util.ThreadPool;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

//|:AgenPro|=import
//|AgenPro:|


public class Agent implements VariableProvider {

    private static final String DEFAULT_CL_PARAMETERS = "-c[s{=Agent.cfg}] -bc[s{=Agent.bc}]";
    private static final String DEFAULT_CL_COMMANDS = "#address[s{=udp:127.0.0.1/161}<(udp|tcp):.*[/[0-9]+]?>] ..";

    static {
        LogFactory.setLogFactory(new JavaLogFactory());
    }

    protected AgentConfigManager agent;
    protected MOServer server;
    // supported MIBs
    protected Modules modules;
    private LogAdapter logger = LogFactory.getLogger(Agent.class);
    private String configFile;
    private File bootCounterFile;


    public Agent(Map args) {
        configFile = (String) ((List) args.get("c")).get(0);
        bootCounterFile = new File((String) ((List) args.get("bc")).get(0));

        server = new DefaultMOServer();
        MOServer[] moServers = new MOServer[]{server};
        InputStream configInputStream =
                Agent.class.getResourceAsStream("AgentConfig.properties");
        if (configInputStream == null) {
            System.err.println("Unable to load AgentConfig.properties. File not found. Aborting");
            System.exit(1);
        }
        final Properties props = new Properties();
        try {
            props.load(configInputStream);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        MOInputFactory configurationFactory = new MOInputFactory() {
            public MOInput createMOInput() {
                return new PropertyMOInput(props, Agent.this);
            }
        };
        MessageDispatcher messageDispatcher = new MessageDispatcherImpl();
        addListenAddresses(messageDispatcher, (List) args.get("address"));
        agent = new AgentConfigManager(new OctetString(MPv3.createLocalEngineID()),
                messageDispatcher,
                null,
                moServers,
                ThreadPool.create("Agent", 3),
                configurationFactory,
                new DefaultMOPersistenceProvider(moServers,
                        configFile),
                new EngineBootsCounterFile(bootCounterFile));
    }

    private static void defaultMain(String args[]) {
        ArgumentParser parser =
                new ArgumentParser(DEFAULT_CL_PARAMETERS,
                        DEFAULT_CL_COMMANDS);
        Map commandLineParameters = null;
        try {
            commandLineParameters = parser.parse(args);
            Agent agent = new Agent(commandLineParameters);
            // Add all available security protocols (e.g. SHA,MD5,DES,AES,3DES,..)
            SecurityProtocols.getInstance().addDefaultProtocols();
            // configure system group:
            // Set system description:
            // sampleAgent.agent.getSysDescr().setValue("My system description".getBytes());
            // Set system OID (= OID of the AGENT-CAPABILITIES statement describing
            // the implemented MIB objects of this agent:
            // sampleAgent.agent.getSysOID().setValue("1.3.1.6.1.4.1....");
            // Set the system services
            // sampleAgent.agent.getSysServices().setValue(72);
            agent.run();
            // Fire a sample named trap generated by AgenPro:
            // agent.modules.getIfMib().fireLinkDownIf10001(agent.server,
            // 	 agent.agent.getNotificationOriginator(), null, null);
        } catch (ParseException ex) {
            System.err.println(ex.getMessage());
        }
    }

    /**
     * Runs a sample agent with a default configuration defined by
     * <code>AgentConfig.properties</code>. A sample command line is:
     * <pre>
     * -c Agent.cfg -bc Agent.bc udp:127.0.0.1/4700 tcp:127.0.0.1/4700
     * </pre>
     *
     * @param args the command line arguments defining at least the listen addresses.
     *             The format is <code>-c[s{=Agent.cfg}] -bc[s{=Agent.bc}]
     *             #address[s<(udp|tcp):.*[/[0-9]+]?>] ..</code>. For the format
     *             description see {@link ArgumentParser}.
     */
    public static void main(String[] args) {
        //|:AgenPro|=main
        defaultMain(args);
        //|AgenPro:|
    }

    protected void addListenAddresses(MessageDispatcher md, List addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        for (Iterator it = addresses.iterator(); it.hasNext(); ) {
            Address address = GenericAddress.parse((String) it.next());
            TransportMapping tm =
                    TransportMappings.getInstance().createTransportMapping(address);
            if (tm != null) {
                md.addTransportMapping(tm);
            } else {
                logger.warn("No transport mapping available for address '" +
                        address + "'.");
            }
        }
    }

    public void run() {
        // initialize agent before registering our own modules
        agent.initialize();
        // this requires sysUpTime to be available.
        registerMIBs();
        // add proxy forwarder
        agent.setupProxyForwarder();
        // now continue agent setup and launch it.
        agent.run();
    }

    /**
     * Get the {@link MOFactory} that creates the various MOs (MIB Objects).
     *
     * @return a {@link DefaultMOFactory} instance by default.
     */
    protected MOFactory getFactory() {
        //|:AgenPro|=factory
        return DefaultMOFactory.getInstance();
        //|AgenPro:|
    }

    /**
     * Register your own MIB modules in the specified context of the agent.
     * The {@link MOFactory} provided to the <code>Modules</code> constructor
     * is returned by {@link #getFactory()}.
     */
    protected void registerMIBs() {
        if (modules == null) {
            modules = new Modules(getFactory());
        }
        try {
            modules.registerMOs(server, null);
            //|:AgenPro|=registerContext
            //|AgenPro:|
        } catch (DuplicateRegistrationException drex) {
            logger.error("Duplicate registration: " + drex.getMessage() + "." +
                    " MIB object registration may be incomplete!", drex);
        }
    }

    public Variable getVariable(String name) {
        OID oid;
        OctetString context = null;
        int pos = name.indexOf(':');
        if (pos >= 0) {
            context = new OctetString(name.substring(0, pos));
            oid = new OID(name.substring(pos + 1, name.length()));
        } else {
            oid = new OID(name);
        }
        final DefaultMOContextScope scope =
                new DefaultMOContextScope(context, oid, true, oid, true);
        MOQuery query = new DefaultMOQuery(scope, false, this);
        ManagedObject mo = server.lookup(query);
        if (mo != null) {
            final VariableBinding vb = new VariableBinding(oid);
            final RequestStatus status = new RequestStatus();
            SubRequest req = new SubRequest() {
                private boolean completed;
                private MOQuery query;

                public boolean hasError() {
                    return false;
                }

                public int getErrorStatus() {
                    return status.getErrorStatus();
                }

                public void setErrorStatus(int errorStatus) {
                    status.setErrorStatus(errorStatus);
                }

                public RequestStatus getStatus() {
                    return status;
                }

                public MOScope getScope() {
                    return scope;
                }

                public VariableBinding getVariableBinding() {
                    return vb;
                }

                public Request getRequest() {
                    return null;
                }

                public Object getUndoValue() {
                    return null;
                }

                public void setUndoValue(Object undoInformation) {
                }

                public void completed() {
                    completed = true;
                }

                public boolean isComplete() {
                    return completed;
                }

                public ManagedObject getTargetMO() {
                    return null;
                }

                public void setTargetMO(ManagedObject managedObject) {
                }

                public int getIndex() {
                    return 0;
                }

                public MOQuery getQuery() {
                    return query;
                }

                public void setQuery(MOQuery query) {
                    this.query = query;
                }

                public SubRequestIterator repetitions() {
                    return null;
                }

                public void updateNextRepetition() {
                }

                public Object getUserObject() {
                    return null;
                }

                public void setUserObject(Object userObject) {
                }

            };
            mo.get(req);
            return vb.getVariable();
        }
        return null;
    }

    public AgentConfigManager getAgentConfigManager() {
        return agent;
    }
}
