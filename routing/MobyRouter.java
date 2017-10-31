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

        private static final String MAX_NB_CONNECTIONS_FOR_FORWARD_S = "maxNbConnectionsForForward";
        private static final String TTL_MEAN_TIME_S = "ttlMeanTime";
        private static final String TTL_STD_DEV_TIME_S = "ttlStdDevTime";

        /** The lowest priority among all the messages currently in the queue */
        private float lowestPriority;
        /** Number of connections/hosts in communication range, above which we
         * select the most trustworthy ones to forward our message queue. */
        private integer maxNbConnectionsForForward;
        private int ttlMeanTime; // in seconds
        private int ttlStdDevTime; // in seconds
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public MobyRouter(Settings s) {
		super(s);
                this.lowestPriority = 1.0;
                this.maxNbConnectionsForForward = s.getInt(MAX_NB_CONNECTIONS_FOR_FORWARD_S);
                Settings.ensurePositiveValue((double)this.maxNbConnectionsForForward, MAX_NB_CONNECTIONS_FOR_FORWARD_S);
                this.ttlMeanTime = s.getInt(TTL_MEAN_TIME_S);
                Settings.ensurePositiveValue((double)this.ttlMeanTime, TTL_MEAN_TIME_S);
                this.ttlStdDevTime = s.getInt(TTL_STD_DEV_TIME_S);
                Settings.ensurePositiveValue((double)this.ttlStdDevTime, TTL_STD_DEV_TIME_S);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected MobyRouter(MobyRouter r) {
		super(r);
                this.lowestPriority = r.lowestPriority;
                this.maxNbConnectionsForForward = r.maxNbConnectionsForForward;
                this.ttlMeanTime = r.ttlMeanTime;
                this.ttlStdDevTime = r.ttlStdDevTime;
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
		if (cons.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}

		List<Message> messages = 
			new ArrayList<Message>(this.getMessageCollection());
		this.sortByQueueMode(messages);
                setMsgsPriorityAsForwarderPriority(messages);

		return tryHarderMessagesToConnections(messages, cons);
        }

        public static void setMsgsPriorityAsForwarderPriority(List<Message> messages) {
                for (Message msg: messages) {
                        float priority = (float)msg.getProperty("priority");
                        if (priority != null) {
                                msg.updateForwarderPriority(priority);
                        } else {
                                msg.updateForwarderPriority(0.0);
                        }
                }
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
                // TTL randomization must be done after call to parent classes methods,
                // otherwise the TTL is overwritten in parent classes.
                randomizeTtl(m, this.ttlMeanTime, this.ttlStdDevTime, this.rand);
                m.addProperty("type", "Moby"); // so that MobyApplication.handle() recognizes this is a Moby msg
                this.getHost().incrementNbCommunicationsWith(m.getTo().toString());
                return added;
        }

        public static void randomizeTtl(Message m, int meanTime, int stdDevTime, Random rand) {
                int ttl = meanTime + (rand.nextGaussian() * stdDevTime); // TTL in seconds
                m.setTtl(ttl / 60); // TTL in minutes
        }

        public void removeLowestPriorityMessage() {
                boolean reasonForRemoval = true; // true = message dropped, false = message delivered to final recipeint
                deleteMessage(getNextMessageToRemove(false).getId(), reasonForRemoval);
        }

}
