/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.Settings;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class MobyRouter extends ActiveRouter {

        /**
         * - should not need to set or change a MessageTransferAcceptPolicy
         * - should not need to an EnergyModel
         * - the routing protocol (this class) should define a queue order for messages (probably need to define a new queue mode and add a case statement to sortByQueueMode(), in MessageRouter)
         * - make sure that Settings.deleteDelivered = false
         * - implement per-msg TTL instead of 1 TTL value for all msgs in a router/node group
         * - isDeliveredMessage(m) for a user to check if msg m whose receiver is this user, has already been received
         * - isBlacklistedMessage(m.Id) to check if this user's application blacklisted m, meaning the app wants m to be dropped
         * - sendMessage(m, user) to forward a message to a neighbor (and receiveMessage() for the other party)
         * - addToMessages() & removeFromMessages() to add & remove from the queue
         * - ActiveRouter.getConnections() to get the connections to the current physical neighbors
         * - modify ActiveRouter.checkReceiving() to implement rate limiting (if some users tries to forward to us too often)
         * - new neighbor discovery in NetworkInterface.update()
         */

        /** The lowest priority among all the messages currently in the queue */
        private float lowestPriority;
        /** Number of connections/hosts in communication range, above which we
         * select the most trustworthy ones to forward our message queue. */
        private integer maxNbConnectionsForForward;
        private Random rng;
        private int ttlMeanTime; // in seconds
        private int ttlStdDevTime; // in seconds
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public MobyRouter(Settings s) {
		super(s);
		//TODO: init
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected MobyRouter(MobyRouter r) {
		super(r);
                this.lowestPriority = r.lowestPriority;
                this.maxNbConnectionsForForward = r.maxNbConnectionsForForward;
		//TODO: init
	}
			
	@Override
        /* Checks out all sending connections to finalize the ready ones and abort those whose connection went down. Also drops messages whose TTL <= 0 (checking every one simulated minute).
         */
	public void update() {
                // Called by DTNHost.update(), at SimClock times depending on World.nextQueueEventTime
                
		super.update();
                
                if (!this.getHost().tooManyForwardsInInterval(SimClock.getIntTime())) {

                        if (isTransferring() || !canStartTransfer()) {
                                return; // transferring, don't try other connections yet
                        }
                        
                        List<Connection> cons = this.getConnections();
                        if (cons.size() > this.maxNbConnectionsForForward) {
                                cons = selectMostTrustworthy(cons, this.maxNbConnectionsForForward);
                        }
                                
                        // try to send all messages to all the selected connections
                        this.tryHarderAllMessagesToConnections(cons);
                }
	}

        /** Returns a list of connections in decreasing order of trust score. */
        protected List<Connection> selectMostTrustworthy(List<Connection> cons, integer maxNbConnections) {
                DTNHost thisHost = this.getHost();
                SortedMap<Float, List<Connection>> consSortedByTrust = buildTrustSortedConnectionMap(cons, thisHost);
                return buildTrustSortedList(consSortedByTrust, maxNbConnections);
        }

        protected static SortedMap<Float, List<Connection>> buildTrustSortedConnectionMap(List<Connection> cons, DTNHost thisHost) {
                SortedMap<Float, List<Connection>> consSortedByTrust = new TreeMap<>();
                for (Connection con: cons) {
                        DTNHost neighborHost = con.getOtherNode(thisHost);
                        DTNHost.updateNbCommonContacts(thisHost, neighorHost);
                        float trustScore = MobyApplication.computeTrustScore(thisHost, neighborHost);
                        consSortedByTrust.putIfAbsent(trustScore, new ArrayList<Connection>());
                        consSortedByTrust.get(trustScore).add(con);
                }
                return consSortedByTrust;
        }

        protected static List<Connection> buildTrustSortedList(SortedMap<Float, List<Connection>> consSortedByTrust, maxNbConnections) {
                List<Connection> mostTrustworthyCons = new ArrayList<>(maxNbConnections);
                reachedMaxNbCons = false;
                while (!reachedMaxNbCons) {
                        List<Connection> consWithHighestTrust = consSortedByTrust.pollLastEntry();
                        if (consWithHighestTrust.size() + mostTrustworthyCons.size() < maxNbConnections) {
                                mostTrustworthyCons.addAll(consWithHighestTrust);
                        } else {
                                for (Connection con: consWithHighestTrust) {
                                        mostTrustworthyCons.add(con);
                                        if (mostTrustworthyCons.size() == maxNbConnections) {
                                                reachedMaxNbCons = true;
                                                break;
                                        }
                                }
                        }
                }

                return mostTrustworthyCons;
        }

        /** Like ActiveRouter.tryAllMessagesToAllConnections() but (1) try only
         * the connections in cons, and (2) does not stop after the first
         * successful transfer to a connection (aka a host currently in
         * communcation range). */
        protected List<Connection> tryHarderAllMessagesToConnections(List<Connection> cons) {
		List<Connection> connections = this.getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> messages = 
			new ArrayList<Message>(this.getMessageCollection());
		this.sortByQueueMode(messages);

		return tryHarderMessagesToConnections(messages, connections);
        }

        /** Like ActiveRouter.tryMessagesToConnections() but does not stop
         * after the first successfully transfered messages. */
        protected List<Connection> tryHarderMessagesToConnections(List<Message> messages,
			List<Connection> connections) {
                List<Connection> successfullyTransmitted = new ArrayList<Connection>();
		for (int i=0, n=connections.size(); i<n; i++) {
			Connection con = connections.get(i);
                        successSendingToCon = tryHarderAllMessages(con, messages);
                        if (successSendingToCon) {
                                successfullyTransmitted.add(con);
                        }
		}
		
		return successfullyTransmitted;
	}

        /** Like ActiveRouter.tryAllMessages() but does not stop after the
         * first successfully transfered message. */
        protected boolean tryHarderAllMessages(Connection con, List<Message> messages) {
                successfullyTransmittedOneOrMoreMessages = false;
                List<Message> msgList = new ArrayList(messages);

                msgListSize = msgList.size();
                while (msgListSize > 0) {
                        m = msgList.get(0)
                        int retVal = startTransferSingleConnection(m, con); 
                        if (retVal == RCV_OK) { // accepted the message
                                msgList.remove(m);
                                msgListSize = msgList.size();
                                successfullyTransmittedOneOrMoreMessages = true;
                                // Add the message ID to this host's list of previously forwarded messages
                                this.getHost().addToAlreadyForwardedMsg(m.getId());
                        } else if (retVal < 0) { // message denied
                                msgList.remove(m);
                                msgListSize = msgList.size();
                        } else if (retVal == TRY_LATER_BUSY) { // other host busy, says to try again later
                                break;
                        }
                }
        
		return successfullyTransmittedOneOrMoreMessages;
	}

        /** Like ActiveRouter.startTransfer() but does not add con to the watch
         * list of active connections if it already is in the watch list. */
        protected int startTransferSingleConnection(Message m, Connection con) {
		int retVal;
		
		if (!con.isReadyForTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		if (!policy.acceptSending(getHost(), 
				con.getOtherNode(getHost()), con, m)) {
			return MessageRouter.DENIED_POLICY;
		}
		
		retVal = con.startTransfer(getHost(), m);
		if (retVal == RCV_OK) { // started transfer
			addToSendingConnectionsIfNotAlreadyThere(con);
		}
		else if (deleteDelivered && retVal == DENIED_OLD && 
				m.getTo() == con.getOtherNode(this.getHost())) {
			/* final recipient has already received the msg -> delete it */
			this.deleteMessage(m.getId(), false);
		}
		
		return retVal;
	}

	protected void addToSendingConnectionsIfNotAlreadyThere(Connection con) {
                if (!this.sendingConnections.contains(con)) {
                        this.sendingConnections.add(con);
                }
	}
	
	
	@Override
	public MobyRouter replicate() {
		return new MobyRouter(this);
	}

        public float getLowestPriority() {
                return lowestPriority;
        }

        /** This function must be called every time a new message is added to the queue. */
        public void updateLowestPriority() {
                Message m = getNextMessageToRemove();
                if (m != null) {
                        float priority = (float)m.getProperty("priority");
                        if (priority != null) {
                                lowestPriority = priority;
                        }
                }
        }

        
	protected void addToMessages(Message m, boolean newMessage) {
                super.addToMessages(m, newMessage);
                updateLowestPriority();
        }

        /** Like ActiveRouter.getNextMessageToRemove() but gets the messages with the lowest priority. */
        protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message lowestPriority = null;
		for (Message m : messages) {
			
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue; // skip the message(s) that router is sending
			}
			
			if (lowestPriority == null ) {
				lowestPriority = m;
			}
			else {
                                float currentPriority = (float)lowestPriority.getProperty("priority");
                                float mPriority = (float)m.getProperty("priority");
                                if (currentPriority != null && mPriority != null && currentPriority > mPriority) {
                                        lowestPriority = m;
                                }
			}
		}
		
		return lowestPriority;
	}

        /** Like ActiveRouter.createNewMessage() but also increment this host's
         * counter of communications with m's final receipient, and randomizes
         * m's TTL. */
        public boolean createNewMessage(Message m) {
                boolean added = super.createNewMessage(m);
                this.getHost().incrementNbCommunicationsWith(m.getTo().toString());
                randomizeTtl(m, ttlMeanTime, ttlStdDevTime, rng);
                return added;
        }

        public static void randomizeTtl(Message m, int meanTime, int stdDevTime, Random rng) {
                int ttl = meanTime + (rng.nextGaussian() * stdDevTime); // TTL in seconds
                m.setTtl(ttl / 60); // TTL in minutes
        }


}
