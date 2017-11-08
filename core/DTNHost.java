/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.util.RoutingInfo;
import routing.MobyRouter;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost> {
	private static int nextAddress = 0;
	private int address;

	private Coord location; 	// where is the host
	private Coord destination;	// where is it going

	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;

        // Moby variables
        private static final String MAX_NB_MOBY_CONTACTS_S = "maxnrofMobyContacts";
        private static final int DEFAULT_MAX_NB_MOBY_CONTACTS = 126; // max nb of 256-bit hashes that can fit in a Bluetooth Classic packet's payload
        private static final String MAX_NB_NON_MOBY_CONTACTS_S = "maxnrofNonMobyContacts";
        private static final int DEFAULT_MAX_NB_NON_MOBY_CONTACTS = 126; // max nb of 256-bit hashes that can fit in a Bluetooth Classic packet's payload
        private static final String TIME_REMEMBER_FORWARDED_MSG_S = "timeRememberForwardedMsgs";
        private static final int DEFAULT_TIME_REMEMBER_FORWARDED_MSG = 172800; // Time (in seconds) before we forward again a message that we already forwarded

        // TODO LATER: there currently is no way to add/remove contacts during a simulation
        private Map<String, List<Integer>> trustElements;
        // FYI: trustElements.get(hostName) = List(nbCommonMobyContacts, nbCommonNonMobyContacts, nbCommunicationsWith)
        private Map<String, Boolean> contactType;
        // contactType is a list of contacts, where true means it is a Moby contact, and false means it is a non-Moby contact
        private Map<String, Integer> allForwardedMsgIds;
        // FYI: allForwardedMsgIds.get(msgId) = expiryDate (in seconds) of msgId
        private int nbMobyContacts;
        private int nbNonMobyContacts;
        private int highestNbCommunications;
        private int maxNbMobyContacts;
        private int maxNbNonMobyContacts;
        private int durationToKeepAlreadyForwardedMsgs; // time in seconds
        private Random rand; // for selecting random subsets of (non-)Moby contacts
        private int lastForwardTime; // TODO LATER: output (as seconds) of SimClock.getIntTime()
        private int forwardIntervalTime; // TODO LATER: time interval (in seconds) to check if this host already did too many forwards of its message queue
        private boolean isMobyInstance;

	static {
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}
        /**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus, 
			MovementModel mmProto, MessageRouter mRouterProto) {
                this.isMobyInstance = false;
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId+address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}	

		// TODO - think about the names of the interfaces and the nodes
		//this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}
        }

	/**
	 * Creates a new DTNHost.
	 * @param msgLs Message listeners
	 * @param movLs Movement listeners
	 * @param groupId GroupID of this host
	 * @param interf List of NetworkInterfaces for the class
	 * @param comBus Module communication bus object
	 * @param mmProto Prototype of the movement model of this host
	 * @param mRouterProto Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs,
			List<MovementListener> movLs,
			String groupId, List<NetworkInterface> interf,
			ModuleCommunicationBus comBus, 
			MovementModel mmProto, MessageRouter mRouterProto,
                        long prngSeed, Settings mobySettings,
                        Map<String, Boolean> contactsType, int highestNbComms,
                        Map<String, List<Integer>> trustElts) {
                this.isMobyInstance = true;
		this.comBus = comBus;
		this.location = new Coord(0,0);
		this.address = getNextAddress();
		this.name = groupId+address;
		this.net = new ArrayList<NetworkInterface>();

		for (NetworkInterface i : interf) {
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}	

		// TODO - think about the names of the interfaces and the nodes
		//this.name = groupId + ((NetworkInterface)net.get(1)).getAddress();

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null) { // inform movement listeners about the location
			for (MovementListener l : movLs) {
				l.initialLocation(this, this.location);
			}
		}

                this.rand = new Random(prngSeed);
                this.allForwardedMsgIds = new HashMap<>();
                this.trustElements = trustElts;
                this.highestNbCommunications = highestNbComms;
                this.contactType = contactsType;
                this.nbMobyContacts = this.getMobyContacts().size();
                this.nbNonMobyContacts = this.getNonMobyContacts().size();

                this.maxNbMobyContacts = mobySettings.getInt(MAX_NB_MOBY_CONTACTS_S, DEFAULT_MAX_NB_MOBY_CONTACTS);
                //Settings.ensurePositiveValue((double)this.maxNbMobyContacts, MAX_NB_MOBY_CONTACTS_S);
                this.maxNbNonMobyContacts = mobySettings.getInt(MAX_NB_NON_MOBY_CONTACTS_S, DEFAULT_MAX_NB_NON_MOBY_CONTACTS);
                //Settings.ensurePositiveValue((double)this.maxNbNonMobyContacts, MAX_NB_NON_MOBY_CONTACTS_S);
                this.durationToKeepAlreadyForwardedMsgs = mobySettings.getInt(TIME_REMEMBER_FORWARDED_MSG_S, DEFAULT_TIME_REMEMBER_FORWARDED_MSG);
                //Settings.ensurePositiveValue((double)this.durationToKeepAlreadyForwardedMsgs, TIME_REMEMBER_FORWARDED_MSG_S);

                // TODO LATER: manage rate limiting of the number of times we perform forward over a period of time
                /*this.lastForwardTime; // output (as seconds) of SimClock.getIntTime()*/
                /*this.forwardIntervalTime; // time interval (in seconds) to check if this host already did too many forwards of its message queue*/
	}
	
	/**
	 * Returns a new network interface address and increments the address for
	 * subsequent calls.
	 * @return The next address.
	 */
	private synchronized static int getNextAddress() {
		return nextAddress++;	
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset() {
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is actively moving (false if not)
	 * @return true if this node is actively moving (false if not)
	 */ public boolean isMovementActive() {
		return this.movement.isActive();
	}
	
	/**
	 * Returns true if this node's radio is active (false if not)
	 * @return true if this node's radio is active (false if not)
	 */
	public boolean isRadioActive() {
		/* TODO: make this work for multiple interfaces */
		return this.getInterface(1).isActive();
	}

	/**
	 * Set a router for this host
	 * @param router The router to set
	 */
	private void setRouter(MessageRouter router) {
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * @return the router of this host
	 */
	public MessageRouter getRouter() {
		return this.router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress() {
		return this.address;
	}
	
	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus() {
		return this.comBus;
	}
	
    /**
	 * Informs the router of this host about state change in a connection
	 * object.
	 * @param con  The connection object whose state changed
	 */
	public void connectionUp(Connection con) {
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con) {
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections() {
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net) {
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host. 
	 * @return The location
	 */
	public Coord getLocation() {
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no
	 * path is in use at the moment.
	 * @return The path this node is traveling
	 */
	public Path getPath() {
		return this.path;
	}


	/**
	 * Sets the Node's location overriding any location set by movement model
	 * @param location The location to set
	 */
	public void setLocation(Coord location) {
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the messages in a collection.
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection() {
		return this.router.getMessageCollection();
	}

	/**
	 * Returns the number of messages this node is carrying.
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages() {
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty
	 * buffer but can be over 100 if a created message is bigger than buffer 
	 * space that could be freed.
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy() {
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		return 100*((bSize-freeBuffer)/bSize);
	}

	/**
	 * Returns routing info of this host's router.
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo() {
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces() {
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	public NetworkInterface getInterface(int interfaceNo) {
		NetworkInterface ni = null;
		try {
			ni = net.get(interfaceNo-1);
		} catch (IndexOutOfBoundsException ex) {
			throw new SimError("No such interface: "+interfaceNo + 
					" at " + this);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	protected NetworkInterface getInterface(String interfacetype) {
		for (NetworkInterface ni : net) {
			if (ni.getInterfaceType().equals(interfacetype)) {
				return ni;
			}
		}
		return null;	
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId, 
			boolean up) {
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null) {
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype "+interfaceId;
		} else {
			ni = getInterface(1);
			no = anotherHost.getInterface(1);
			
			assert (ni.getInterfaceType().equals(no.getInterfaceType())) : 
				"Interface types do not match.  Please specify interface type explicitly";
		}
		
		if (up) {
			ni.createConnection(no);
		} else {
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h) {
		Debug.p("WARNING: using deprecated DTNHost.connect(DTNHost)" +
		"Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h,null,true);
	}

	/**
	 * Updates node's network layer and router.
	 * @param simulateConnections Should network layer be updated too
	 */
	public void update(boolean simulateConnections) {
                // Called by World.updateHosts()

		if (!isRadioActive()) {
			// Make sure inactive nodes don't have connections
			tearDownAllConnections();
			return;
		}
		
		if (simulateConnections) {
			for (NetworkInterface i : net) {
				i.update();
			}
		}
		this.router.update();
	}
	
	/** 
	 * Tears down all connections for this host.
	 */
	private void tearDownAllConnections() {
		for (NetworkInterface i : net) {
			// Get all connections for the interface
			List<Connection> conns = i.getConnections();
			if (conns.size() == 0) continue;
			
			// Destroy all connections
			List<NetworkInterface> removeList =
				new ArrayList<NetworkInterface>(conns.size());
			for (Connection con : conns) {
				removeList.add(con.getOtherInterface(i));
			}
			for (NetworkInterface inf : removeList) {
				i.destroyConnection(inf);
			}
		}
	}

	/**
	 * Moves the node towards the next waypoint or waits if it is
	 * not time to move yet
	 * @param timeIncrement How long time the node moves
	 */
	public void move(double timeIncrement) {		
		double possibleMovement;
		double distance;
		double dx, dy;

		if (!isMovementActive() || SimClock.getTime() < this.nextTimeToMove) {
			return; 
		}
		if (this.destination == null) {
			if (!setNextWaypoint()) {
				return;
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = this.location.distance(this.destination);

		while (possibleMovement >= distance) {
			// node can move past its next destination
			this.location.setLocation(this.destination); // snap to destination
			possibleMovement -= distance;
			if (!setNextWaypoint()) { // get a new waypoint
				return; // no more waypoints left
			}
			distance = this.location.distance(this.destination);
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement/distance) * (this.destination.getX() -
				this.location.getX());
		dy = (possibleMovement/distance) * (this.destination.getY() -
				this.location.getY());
		this.location.translate(dx, dy);
	}	

	/**
	 * Sets the next destination and speed to correspond the next waypoint
	 * on the path.
	 * @return True if there was a next waypoint to set, false if node still
	 * should wait
	 */
	private boolean setNextWaypoint() {
		if (path == null) {
			path = movement.getPath();
		}

		if (path == null || !path.hasNext()) {
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.destination = path.getNextWaypoint();
		this.speed = path.getSpeed();

		if (this.movListeners != null) {
			for (MovementListener l : this.movListeners) {
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * @param id Identifier of the message
	 * @param to Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to) {
		this.router.sendMessage(id, to);
	}

	/**
	 * Start receiving a message from another host
	 * @param m The message
	 * @param from Who the message is from
	 * @return The value returned by 
	 * {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from) {
		int retVal = this.router.receiveMessage(m, from); 

		if (retVal == MessageRouter.RCV_OK) {
			m.addNodeOnPath(this);	// add this node on the messages path
		}

		return retVal;	
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a
	 * connection.
	 * @param con The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con) {
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from) {
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * @param id Identifier of the message
	 * @param from From who the message was from
	 * @param bytesRemaining Nrof bytes that were left before the transfer
	 * would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining) {
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * @param m The message to create
	 */
	public void createNewMessage(Message m) {
                if (this.isMobyInstance) {
                        MobyRouter r = (MobyRouter) this.router;
                        r.createNewMessage(m);
                } else {
                        this.router.createNewMessage(m);
                }
	}

	/**
	 * Deletes a message from this host
	 * @param id Identifier of the message
	 * @param drop True if the message is deleted because of "dropping"
	 * (e.g. buffer is full) or false if it was deleted for some other reason
	 * (e.g. the message got delivered to final destination). This effects the
	 * way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop) {
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * @return Host's name
	 */
	public String toString() {
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object
	 * reference
	 * @param otherHost The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost) {
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h) {
		return this.getAddress() - h.getAddress();
	}

        
        public int getNbCommonMobyContacts(String hostName) {
                if (trustElements.containsKey(hostName)) {
                        return trustElements.get(hostName).get(0);
                } else {
                        return -1;
                }
        }

        public int getNbCommonNonMobyContacts(String hostName) {
                if (trustElements.containsKey(hostName)) {
                        return trustElements.get(hostName).get(1);
                } else {
                        return -1;
                }
        }

        public int getNbCommunicationsWith(String hostName) {
                if (trustElements.containsKey(hostName)) {
                        return trustElements.get(hostName).get(2);
                } else {
                        return -1;
                }
        }

        public void incrementNbCommunicationsWith(String hostName) {
                if (trustElements.containsKey(hostName)) {
                        int nbComms = trustElements.get(hostName).get(2);
                        trustElements.get(hostName).set(2, nbComms++);
                        
                        // checking if highestNbCommunications needs updating
                        if (nbComms > this.highestNbCommunications) {
                                this.highestNbCommunications = nbComms;
                        }
                } else {
                        // Error, throw exception? TODO
                }
        }

        public int getActualOrMaxNbMobyContacts() {
                // Counting Moby contacts
                if (nbMobyContacts == -1) {
                        nbMobyContacts = 0;
                        for (Boolean type: contactType.values()) {
                                if (type) {
                                        nbMobyContacts++;
                                }
                        }
                }
                // Checking whether to cap the returned result
                if (nbMobyContacts > maxNbMobyContacts) {
                        return maxNbMobyContacts;
                } else {
                        return nbMobyContacts;
                }
        }
        
        public int getActualOrMaxNbNonMobyContacts() {
                // Counting Moby contacts
                if (nbNonMobyContacts == -1) {
                        nbNonMobyContacts = 0;
                        for (Boolean type: contactType.values()) {
                                if (!type) {
                                        nbNonMobyContacts++;
                                }
                        }
                }
                // Checking whether to cap the returned result
                if (nbNonMobyContacts > maxNbNonMobyContacts) {
                        return maxNbNonMobyContacts;
                } else {
                        return nbNonMobyContacts;
                }
        }
        
        public int getHighestNbCommunications() {
                return highestNbCommunications;
        }

        public boolean hasAlreadyForwarded(String msgId) {
                return allForwardedMsgIds.containsKey(msgId);
        }

        public void addToAlreadyForwardedMsg(String msgId) {
                if (!allForwardedMsgIds.containsKey(msgId)) {
                        allForwardedMsgIds.put(msgId, SimClock.getIntTime() + durationToKeepAlreadyForwardedMsgs);
                }
        }

        public static void updateNbCommonContacts(DTNHost thisHost, DTNHost neighborHost) {
                Set<String> mobyContactsA = thisHost.getMobyContactsRandSubset(thisHost.getMaxNbMobyContacts());
                Set<String> mobyContactsB = neighborHost.getMobyContactsRandSubset(neighborHost.getMaxNbMobyContacts());
                Set<String> commonMobyContacts = new HashSet<>(mobyContactsA);
                commonMobyContacts.retainAll(mobyContactsB);
                int nbCommonMobyContacts = commonMobyContacts.size();
                thisHost.updateNbCommonMobyContacts(neighborHost.toString(), nbCommonMobyContacts);
                neighborHost.updateNbCommonMobyContacts(thisHost.toString(), nbCommonMobyContacts);

                Set<String> nonMobyContactsA = thisHost.getNonMobyContactsRandSubset(thisHost.getMaxNbNonMobyContacts());
                Set<String> nonMobyContactsB = neighborHost.getNonMobyContactsRandSubset(neighborHost.getMaxNbNonMobyContacts());
                Set<String> commonNonMobyContacts = new HashSet<>(nonMobyContactsA);
                commonNonMobyContacts.retainAll(nonMobyContactsB);
                int nbCommonNonMobyContacts = commonNonMobyContacts.size();
                thisHost.updateNbCommonNonMobyContacts(neighborHost.toString(), nbCommonNonMobyContacts);
                neighborHost.updateNbCommonNonMobyContacts(thisHost.toString(), nbCommonNonMobyContacts);
        }

        public void updateNbCommonMobyContacts(String hostName, int nbCommonMobyContacts) {
                if (!trustElements.containsKey(hostName)) {
                        initNewContactTrustElements(hostName);
                }
                trustElements.get(hostName).set(0, nbCommonMobyContacts);
        }

        private void initNewContactTrustElements(String hostName) {
                List<Integer> elements = new ArrayList<>(3);
                elements.add(0);
                elements.add(0);
                elements.add(0);
                trustElements.put(hostName, elements);
        }

        public void updateNbCommonNonMobyContacts(String hostName, int nbCommonNonMobyContacts) {
                if (!trustElements.containsKey(hostName)) {
                        initNewContactTrustElements(hostName);
                }
                trustElements.get(hostName).set(1, nbCommonNonMobyContacts);
        }

        public Set<String> getMobyContacts() {
                Set<String> result = new HashSet<>();
                for (Map.Entry<String, Boolean> contact: contactType.entrySet()) {
                        if (contact.getValue()) {
                                result.add(contact.getKey());
                        }
                }
                return result;
        }

        public Set<String> getMobyContactsRandSubset(int maxNbMobyContacts) {
                List<String> mobyContacts = new ArrayList<>(getMobyContacts());
                if (mobyContacts.size() <= maxNbMobyContacts) {
                        return new HashSet<String>(mobyContacts);
                }

                Set<String> result = new HashSet<>(maxNbMobyContacts);
                for (int i = 0; i < maxNbMobyContacts; i++) {
                        int randIndex = rand.nextInt(mobyContacts.size());
                        result.add(mobyContacts.remove(randIndex));
                }
                return result;
        }

        public Set<String> getNonMobyContacts() {
                Set<String> result = new HashSet<>();
                for (Map.Entry<String, Boolean> contact: contactType.entrySet()) {
                        if (!contact.getValue()) {
                                result.add(contact.getKey());
                        }
                }
                return result;
        }

        public Set<String> getNonMobyContactsRandSubset(int maxNbNonMobyContacts) {
                List<String> nonMobyContacts = new ArrayList<>(getNonMobyContacts());
                if (nonMobyContacts.size() <= maxNbNonMobyContacts) {
                        return new HashSet<String>(nonMobyContacts);
                }

                Set<String> result = new HashSet<>(maxNbNonMobyContacts);
                for (int i = 0; i < maxNbNonMobyContacts; i++) {
                        int randIndex = rand.nextInt(nonMobyContacts.size());
                        result.add(nonMobyContacts.remove(randIndex));
                }
                return result;
        }

        public boolean tooManyForwardsInInterval(int currentTime) {
                // TODO LATER: for now, no rate limiting, always return false
                return false;
        }

        public void removeOldMsgFromAlreadyFowarded(int currentTime) {
                for (String msgId: allForwardedMsgIds.keySet()) {
                        if (allForwardedMsgIds.get(msgId) < currentTime) {
                                allForwardedMsgIds.remove(msgId);
                        }
                }
        }

        public int getMaxNbMobyContacts() {
                return this.maxNbMobyContacts;
        }

        public int getMaxNbNonMobyContacts() {
                return this.maxNbNonMobyContacts;
        }

}
