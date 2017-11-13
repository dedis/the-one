/* 
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import input.EventQueue;
import input.EventQueueHandler;

import java.io.Serializable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.Scanner;
import java.lang.Exception;

import movement.MapBasedMovement;
import movement.MovementModel;
import movement.map.SimMap;
import routing.MessageRouter;

/**
 * A simulation scenario used for getting and storing the settings of a
 * simulation run.
 */
public class SimScenario implements Serializable {
	
	/** a way to get a hold of this... */	
	private static SimScenario myinstance=null;

	/** namespace of scenario settings ({@value})*/
	public static final String SCENARIO_NS = "Scenario";
	/** number of host groups -setting id ({@value})*/
	public static final String NROF_GROUPS_S = "nrofHostGroups";
	/** number of interface types -setting id ({@value})*/
	public static final String NROF_INTTYPES_S = "nrofInterfaceTypes";
	/** scenario name -setting id ({@value})*/
	public static final String NAME_S = "name";
	/** end time -setting id ({@value})*/
	public static final String END_TIME_S = "endTime";
	/** update interval -setting id ({@value})*/
	public static final String UP_INT_S = "updateInterval";
	/** simulate connections -setting id ({@value})*/
	public static final String SIM_CON_S = "simulateConnections";

        public static final String PRNG_SEED_S = "prngSeed";
        public static final String MOBY_NS = "moby";
        public static final String PATH_TRUST_ELEMENTS_S = "pathToTrustElementsFile";

	/** namespace for interface type settings ({@value}) */
	public static final String INTTYPE_NS = "Interface";
	/** interface type -setting id ({@value}) */
	public static final String INTTYPE_S = "type";
	/** interface name -setting id ({@value}) */
	public static final String INTNAME_S = "name";

	/** namespace for application type settings ({@value}) */
	public static final String APPTYPE_NS = "Application";
	/** application type -setting id ({@value}) */
	public static final String APPTYPE_S = "type";
	/** setting name for the number of applications */
	public static final String APPCOUNT_S = "nrofApplications";
	
	/** namespace for host group settings ({@value})*/
	public static final String GROUP_NS = "Group";
	/** group id -setting id ({@value})*/
	public static final String GROUP_ID_S = "groupID";
	/** number of hosts in the group -setting id ({@value})*/
	public static final String NROF_HOSTS_S = "nrofHosts";
	/** movement model class -setting id ({@value})*/
	public static final String MOVEMENT_MODEL_S = "movementModel";
	/** router class -setting id ({@value})*/
	public static final String ROUTER_S = "router";
	/** number of interfaces in the group -setting id ({@value})*/
	public static final String NROF_INTERF_S = "nrofInterfaces";
	/** interface name in the group -setting id ({@value})*/
	public static final String INTERFACENAME_S = "interface";
	/** application name in the group -setting id ({@value})*/
	public static final String GAPPNAME_S = "application";

	/** package where to look for movement models */
	private static final String MM_PACKAGE = "movement.";
	/** package where to look for router classes */
	private static final String ROUTING_PACKAGE = "routing.";

	/** package where to look for interface classes */
	private static final String INTTYPE_PACKAGE = "interfaces.";
	
	/** package where to look for application classes */
	private static final String APP_PACKAGE = "applications.";
	
	/** The world instance */
	private World world;
	/** List of hosts in this simulation */
	protected List<DTNHost> hosts;
	/** Name of the simulation */
	private String name;
	/** number of host groups */
	int nrofGroups;
	/** Width of the world */
	private int worldSizeX;
	/** Height of the world */
	private int worldSizeY;
	/** Largest host's radio range */
	private double maxHostRange;
	/** Simulation end time */
	private double endTime;
	/** Update interval of sim time */
	private double updateInterval;
	/** External events queue */
	private EventQueueHandler eqHandler;
	/** Should connections between hosts be simulated */
	private boolean simulateConnections;
	/** Map used for host movement (if any) */
	private SimMap simMap;

	/** Global connection event listeners */
	private List<ConnectionListener> connectionListeners;
	/** Global message event listeners */
	private List<MessageListener> messageListeners;
	/** Global movement event listeners */
	private List<MovementListener> movementListeners;
	/** Global update event listeners */
	private List<UpdateListener> updateListeners;
	/** Global application event listeners */
	private List<ApplicationListener> appListeners;

        private Random rand; // main source of randomness for this scenario

	static {
		DTNSim.registerForReset(SimScenario.class.getCanonicalName());
		reset();
	}

	public static void reset() {
		myinstance = null;
	}

	/**
	 * Creates a scenario based on Settings object.
	 */
	protected SimScenario() {
                //System.out.println("DEBUG: SimScenario.constructor");
		Settings s = new Settings(SCENARIO_NS);
		nrofGroups = s.getInt(NROF_GROUPS_S);

                this.rand = new Random(s.getInt(PRNG_SEED_S));

		this.name = s.valueFillString(s.getSetting(NAME_S));
		this.endTime = s.getDouble(END_TIME_S);
		this.updateInterval = s.getDouble(UP_INT_S);
		this.simulateConnections = s.getBoolean(SIM_CON_S);

		s.ensurePositiveValue(nrofGroups, NROF_GROUPS_S);
		s.ensurePositiveValue(endTime, END_TIME_S);
		s.ensurePositiveValue(updateInterval, UP_INT_S);

		this.simMap = null;
		this.maxHostRange = 1;

		this.connectionListeners = new ArrayList<ConnectionListener>();
		this.messageListeners = new ArrayList<MessageListener>();
		this.movementListeners = new ArrayList<MovementListener>();
		this.updateListeners = new ArrayList<UpdateListener>();
		this.appListeners = new ArrayList<ApplicationListener>();
		this.eqHandler = new EventQueueHandler();

		/* TODO: check size from movement models */
		s.setNameSpace(MovementModel.MOVEMENT_MODEL_NS);
		int [] worldSize = s.getCsvInts(MovementModel.WORLD_SIZE, 2);
		this.worldSizeX = worldSize[0];
		this.worldSizeY = worldSize[1];
		
		createHosts();
		
                //System.out.println("DEBUG: SimScenario.constructor: creating world");
		this.world = new World(hosts, worldSizeX, worldSizeY, updateInterval, 
				updateListeners, simulateConnections, 
				eqHandler.getEventQueues());
	}
	
	/**
	 * Returns the SimScenario instance and creates one if it doesn't exist yet
	 */
	public static SimScenario getInstance() {
                //System.out.println("DEBUG: SimScenario.getInstance");
		if (myinstance == null) {
			myinstance = new SimScenario();
		}
		return myinstance;
	}



	/**
	 * Returns the name of the simulation run
	 * @return the name of the simulation run
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Returns true if connections should be simulated
	 * @return true if connections should be simulated (false if not)
	 */
	public boolean simulateConnections() {
		return this.simulateConnections;
	}

	/**
	 * Returns the width of the world
	 * @return the width of the world
	 */
	public int getWorldSizeX() {
		return this.worldSizeX;
	}

	/**
	 * Returns the height of the world
	 * @return the height of the world
	 */
	public int getWorldSizeY() {
		return worldSizeY;
	}

	/**
	 * Returns simulation's end time
	 * @return simulation's end time
	 */
	public double getEndTime() {
		return endTime;
	}

	/**
	 * Returns update interval (simulated seconds) of the simulation
	 * @return update interval (simulated seconds) of the simulation
	 */
	public double getUpdateInterval() {
		return updateInterval;
	}

	/**
	 * Returns how long range the hosts' radios have
	 * @return Range in meters
	 */
	public double getMaxHostRange() {
		return maxHostRange;
	}

	/**
	 * Returns the (external) event queue(s) of this scenario or null if there 
	 * aren't any
	 * @return External event queues in a list or null
	 */
	public List<EventQueue> getExternalEvents() {
		return this.eqHandler.getEventQueues();
	}

	/**
	 * Returns the SimMap this scenario uses, or null if scenario doesn't
	 * use any map
	 * @return SimMap or null if no map is used
	 */
	public SimMap getMap() {
		return this.simMap;
	}

	/**
	 * Adds a new connection listener for all nodes
	 * @param cl The listener
	 */
	public void addConnectionListener(ConnectionListener cl){
		this.connectionListeners.add(cl);
	}

	/**
	 * Adds a new message listener for all nodes
	 * @param ml The listener
	 */
	public void addMessageListener(MessageListener ml){
		this.messageListeners.add(ml);
	}

	/**
	 * Adds a new movement listener for all nodes
	 * @param ml The listener
	 */
	public void addMovementListener(MovementListener ml){
		this.movementListeners.add(ml);
	}

	/**
	 * Adds a new update listener for the world
	 * @param ul The listener
	 */
	public void addUpdateListener(UpdateListener ul) {
		this.updateListeners.add(ul);
	}

	/**
	 * Returns the list of registered update listeners
	 * @return the list of registered update listeners
	 */
	public List<UpdateListener> getUpdateListeners() {
		return this.updateListeners;
	}

	/** 
	 * Adds a new application event listener for all nodes.
	 * @param al The listener
	 */
	public void addApplicationListener(ApplicationListener al) {
		this.appListeners.add(al);
	}
	
	/**
	 * Returns the list of registered application event listeners
	 * @return the list of registered application event listeners
	 */
	public List<ApplicationListener> getApplicationListeners() {
		return this.appListeners;
	}
	
	/**
	 * Creates hosts for the scenario
	 */
	protected void createHosts() {
                //System.out.println("DEBUG: SimScenario.createHosts");
		this.hosts = new ArrayList<DTNHost>();

		for (int i=1; i<=nrofGroups; i++) {
			List<NetworkInterface> interfaces = 
				new ArrayList<NetworkInterface>();
			Settings s = new Settings(GROUP_NS+i);
			s.setSecondaryNamespace(GROUP_NS);
			String gid = s.getSetting(GROUP_ID_S);
			int nrofHosts = s.getInt(NROF_HOSTS_S);
			int nrofInterfaces = s.getInt(NROF_INTERF_S);
			int appCount;

			// creates prototypes of MessageRouter and MovementModel
                        // Basically, calls the MovementModel(Settings s) constructor
			MovementModel mmProto = 
				(MovementModel)s.createIntializedObject(MM_PACKAGE + 
						s.getSetting(MOVEMENT_MODEL_S));
                        // Basically, calls the MessageRouter(Settings s) constructor
			MessageRouter mRouterProto = 
				(MessageRouter)s.createIntializedObject(ROUTING_PACKAGE + 
						s.getSetting(ROUTER_S));
			
			/* checks that these values are positive (throws Error if not) */
			s.ensurePositiveValue(nrofHosts, NROF_HOSTS_S);
			s.ensurePositiveValue(nrofInterfaces, NROF_INTERF_S);

			// setup interfaces
			for (int j=1;j<=nrofInterfaces;j++) {
				String intName = s.getSetting(INTERFACENAME_S + j);
				Settings intSettings = new Settings(intName); 
				NetworkInterface iface = 
					(NetworkInterface)intSettings.createIntializedObject(
							INTTYPE_PACKAGE +intSettings.getSetting(INTTYPE_S));
				iface.setClisteners(connectionListeners);
				iface.setGroupSettings(s);
				interfaces.add(iface);
			}
                        //System.out.println("DEBUG: SimScenario.createHosts: most standard stuff created");

                        //System.out.println("DEBUG: SimScenario.createHosts: creating applications");
                        boolean mobyEnabled = false;
			// setup applications
			if (s.contains(APPCOUNT_S)) {
				appCount = s.getInt(APPCOUNT_S);
			} else {
				appCount = 0;
			}
			for (int j=1; j<=appCount; j++) {
				String appname = null;
				Application protoApp = null;
				try {
					// Get name of the application for this group
					appname = s.getSetting(GAPPNAME_S+j);
                                        if (appname.equals("moby")) {
                                                mobyEnabled = true;
                                        }
					// Get settings for the given application
					Settings t = new Settings(appname);
					// Load an instance of the application
					protoApp = (Application)t.createIntializedObject(
							APP_PACKAGE + t.getSetting(APPTYPE_S));
					// Set application listeners
					protoApp.setAppListeners(this.appListeners);
					// Set the proto application in proto router
					//mRouterProto.setApplication(protoApp);
					mRouterProto.addApplication(protoApp);
				} catch (SettingsError se) {
					// Failed to create an application for this group
					System.err.println("Failed to setup an application: " + se);
					System.err.println("Caught at " + se.getStackTrace()[0]);
					System.exit(-1);
				}
			}

			if (mmProto instanceof MapBasedMovement) {
				this.simMap = ((MapBasedMovement)mmProto).getMap();
			}

                        //System.out.println("DEBUG: SimScenario.createHosts: creating trust elements");
                        Map<Integer, Map<String, Boolean>> hostsContactsType = new HashMap<>();
                        Map<Integer, Map<String, Integer>> hostsNbCommunications = new HashMap<>();
                        Map<Integer, Integer> hostsHighestNbCommunications = null;
                        Map<Integer, Map<String, List<Integer>>> hostsTrustElts = null;
                        if (mobyEnabled) {
                                Settings mobySettings = new Settings(MOBY_NS);
                                String inputFile = mobySettings.getSetting(PATH_TRUST_ELEMENTS_S);
                                hostsHighestNbCommunications = loadTrustElementsDenominators(inputFile, hostsContactsType, gid, hostsNbCommunications);
                                hostsTrustElts = computeTrustElts(hostsContactsType, hostsNbCommunications, gid);
                        }

                        //System.out.println("DEBUG: SimScenario.createHosts: creating hosts");
			// creates hosts of ith group
			for (int j=0; j<nrofHosts; j++) {
				ModuleCommunicationBus comBus = new ModuleCommunicationBus();

                                DTNHost host = null;
                                if (mobyEnabled) {
                                        Settings mobySettings = new Settings(MOBY_NS);

                                        mRouterProto.setTtlRandomizationSeed((int)rand.nextLong());

                                        // prototypes are given to new DTNHost which replicates
                                        // new instances of movement model and message router
                                        host = new DTNHost(this.messageListeners, 
                                                        this.movementListeners,	gid, interfaces, comBus, 
                                                        mmProto, mRouterProto, rand.nextLong(), mobySettings,
                                                        hostsContactsType.get(j), hostsHighestNbCommunications.get(j),
                                                        hostsTrustElts.get(j));
                                } else {
                                        // prototypes are given to new DTNHost which replicates
                                        // new instances of movement model and message router
                                        host = new DTNHost(this.messageListeners, 
                                                        this.movementListeners,	gid, interfaces, comBus, 
                                                        mmProto, mRouterProto);
                                }
				hosts.add(host);
			}
		}
                //System.out.println("DEBUG: SimScenario.createHosts: done");
	}

	/**
	 * Returns the list of nodes for this scenario.
	 * @return the list of nodes for this scenario.
	 */
	public List<DTNHost> getHosts() {
		return this.hosts;
	}
	
	/**
	 * Returns the World object of this scenario
	 * @return the World object
	 */
	public World getWorld() {
		return this.world;
	}

        public Map<Integer, Integer> loadTrustElementsDenominators(String inputFile, Map<Integer,
                        Map<String, Boolean>> hostsContactsType, String gid, Map<Integer, Map<String, Integer>> hostsNbCommunications) {
                //System.out.println("DEBUG: SimScenario.loadTrustElementsDenominators");
                Map<Integer, Integer> result = new HashMap<>();

		// skip empty and comment lines
		Pattern skipPattern = Pattern.compile("(#.*)|(^\\s*$)");

                try (BufferedReader reader = new BufferedReader(new FileReader(new File(inputFile)))) {
			String line = reader.readLine();
                        while (line != null) {
                                //System.out.println("DEBUG: SimScenario.loadTrustElementsDenominators: line: " + line);
                                Scanner lineScan = new Scanner(line);
                                lineScan.useDelimiter(",");

                                if (!skipPattern.matcher(line).matches()) {
                                        int userID;
                                        String rawMobyContactList;
                                        String rawNonMobyContactList;
                                        int highestNbCommunications;
                                        try {
                                                userID = lineScan.nextInt();
                                                rawMobyContactList = lineScan.next();
                                                rawNonMobyContactList = lineScan.next();
                                                highestNbCommunications = lineScan.nextInt();
                                        } catch (Exception e) {
                                                throw new SimError("Can't parse '" + line + "'", e);
                                        }
                                        Map<String, Integer> nbCommunicationsPerContact = new HashMap<>();
                                        List<String> mobyContactList = parseRawContactList(rawMobyContactList, gid, nbCommunicationsPerContact);
                                        List<String> nonMobyContactList = parseRawContactList(rawNonMobyContactList, null, nbCommunicationsPerContact);

                                        Map<String, Boolean> contactList = createContactList(mobyContactList, nonMobyContactList);
                                        hostsContactsType.put(userID, contactList);
                                        result.put(userID, highestNbCommunications);
                                        hostsNbCommunications.put(userID, nbCommunicationsPerContact);
                                }
                                
                                line = reader.readLine();
                        }
		} catch (IOException e) {
			throw new SimError(e.getMessage(),e);
		}

                return result;
        }

        public static List<String> parseRawContactList(String rawList, String userIdPrefix, Map<String, Integer> nbCommunicationsPerContact) {
                List<String> result = new ArrayList<>();
                String prefix = "";
                if (userIdPrefix != null) {
                        prefix = userIdPrefix;
                }

                Scanner listScan = new Scanner(rawList);
                listScan.useDelimiter("|");
		Pattern contactAndNbCommsPattern = Pattern.compile("[0-9]+=[0-9]");
                while (listScan.hasNext()) {
                        String contactAndNbComms;
                        try {
                                contactAndNbComms = listScan.findInLine(contactAndNbCommsPattern);
                        } catch (Exception e) {
                                throw new SimError("Can't parse '" + rawList + "'", e);
                        }
                        //System.out.println("DEBUG: SimScenario.parseRawContactList: contactAndNbComms: " + contactAndNbComms);
                        String[] splitted = contactAndNbComms.split("=");
                        String contactID = prefix + splitted[0];
                        result.add(contactID);

                        int nbComms = -1;
                        try{
                                nbComms = Integer.valueOf(splitted[1]);
                        } catch (NumberFormatException e) {
                                throw new SimError("Can't parse integer in '" + contactAndNbComms + "'", e);
                        }
                        nbCommunicationsPerContact.put(contactID, nbComms);
                }

                return result;
        }

        public static Map<String, Boolean> createContactList(List<String> mobyContactList, List<String> nonMobyContactList) {
                Map<String, Boolean> result = new HashMap<>();
                for (String contact: mobyContactList) {
                        result.put(contact, true);
                }
                for (String contact: nonMobyContactList) {
                        result.put(contact, false);
                }
                return result;
        }

        public static Map<Integer, Map<String, List<Integer>>> computeTrustElts(Map<Integer, Map<String, Boolean>> hostsContactsType, Map<Integer, Map<String, Integer>> hostsNbCommunications, String gid) {
                // We assume that hostsContactsType and hostsNbCommunications have the same set of keys, 
                // and that each of the associated maps have the same set of keys.
                // Meaning that hostsContactsType and hostsNbCommunications both represent the same set
                // of users/hosts, each having the same set of contacts. 
                Map<Integer, Map<String, List<Integer>>> result = new HashMap<>(hostsContactsType.size());
                for (Integer host: hostsContactsType.keySet()) {
                        Map<String, Integer> nbComms = hostsNbCommunications.get(host);

                        Map<String, Boolean> contactsType = hostsContactsType.get(host);
                        Set<String> hostMobyContacts = new HashSet<>();
                        Set<String> hostNonMobyContacts = new HashSet<>();
                        splitContactsPerType(contactsType, hostMobyContacts, hostNonMobyContacts);

                        Map<String, List<Integer>> trustElts = new HashMap<>(contactsType.size());
                        for (String contact: contactsType.keySet()) {
                                Set<String> contactMobyContacts = new HashSet<>();
                                Set<String> contactNonMobyContacts = new HashSet<>();
                                if (contactsType.get(contact)) { // i.e. contact is a Moby contact
                                        // removing the prefix characters from contact
                                        int contactID = -1;
                                        try {
                                                contactID = Integer.valueOf(contact.substring(gid.length()));
                                        } catch (NumberFormatException e) {
                                                throw new SimError("Can't parse integer in '" + contact + "'", e);
                                        }
                                        
                                        Map<String, Boolean> typeOfContactsOfContact = hostsContactsType.get(contactID);
                                        splitContactsPerType(typeOfContactsOfContact, contactMobyContacts, contactNonMobyContacts);

                                }

                                Set<String> commonMobyContacts = new HashSet<>(hostMobyContacts);
                                commonMobyContacts.retainAll(contactMobyContacts);
                                Set<String> commonNonMobyContacts = new HashSet<>(hostNonMobyContacts);
                                commonNonMobyContacts.retainAll(contactNonMobyContacts);

                                List<Integer> elements = new ArrayList<>(3);
                                elements.add(commonMobyContacts.size());
                                elements.add(commonNonMobyContacts.size());
                                elements.add(nbComms.get(contact));
                                trustElts.put(contact, elements);
                        }

                        result.put(host, trustElts);
                }

                return result;
        }

        public static void splitContactsPerType(Map<String, Boolean> contactsType, Set<String> hostMobyContacts, Set<String> hostNonMobyContacts) {
                for (String contact: contactsType.keySet()) {
                        if (contactsType.get(contact)) {
                                hostMobyContacts.add(contact);
                        } else {
                                hostNonMobyContacts.add(contact);
                        }
                }
        }

}
