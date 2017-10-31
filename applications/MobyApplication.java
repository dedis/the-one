/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.Random;

//import report.PingAppReporter;
import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

/**
 * Simple ping application to demonstrate the application support. The 
 * application can be configured to send pings with a fixed interval or to only
 * answer to pings it receives. When the application receives a ping it sends
 * a pong message in response.
 * 
 * The corresponding <code>PingAppReporter</code> class can be used to record
 * information about the application behavior.
 *
 * Compute a priority score for messages forwarded to the host running it.
 * If the app can access the host's queue (DTNhost.router.messages), make the decision to drop it if the computed priority is too low.
 *
 * On update() at every cycle, check if there is a new neighbor or if interval of time has passed to run forward
 * 
 * @see PingAppReporter
 * @author teemuk
 */
public class MobyApplication extends Application {
	///** Run in passive mode - don't generate pings but respond */
	//public static final String PING_PASSIVE = "passive";
	///** Ping generation interval */
	//public static final String PING_INTERVAL = "interval";
	///** Ping interval offset - avoids synchronization of ping sending */
	//public static final String PING_OFFSET = "offset";
	///** Destination address range - inclusive lower, exclusive upper */
	//public static final String PING_DEST_RANGE = "destinationRange";
	///** Seed for the app's random number generator */
	//public static final String PING_SEED = "seed";
	///** Size of the ping message */
	//public static final String PING_PING_SIZE = "pingSize";
	///** Size of the pong message */
	//public static final String PING_PONG_SIZE = "pongSize";
	
	/** Application ID */
	public static final String APP_ID = "Moby";
	
	// Private vars
	//private double	lastPing = 0;
	//private double	interval = 500;
	//private boolean passive = false;
	//private int		seed = 0;
	//private int		destMin=0;
	//private int		destMax=1;
	//private int		pingSize=1;
	//private int		pongSize=1;
	//private Random	rng;

        private static final String TRUST_WEIGHT_OF_MOBY_CONTACTS_S = "trustWeightMobyContacts";
        private static final String TRUST_WEIGHT_OF_NON_MOBY_CONTACTS_S = "trustWeightMobyContacts";
        private static final String TRUST_WEIGHT_OF_NB_COMMUNICATIONS_S = "trustWeightnrofCommunications";
        private static final String MAX_NB_MOBY_CONTACTS_S = "maxnrofMobyContacts";
        private static final String MAX_NB_NON_MOBY_CONTACTS_S = "maxnrofNonMobyContacts";
        private static final String TTL_MEAN_TIME_S = "ttlMeanTime";
        private static final String TTL_STD_DEV_TIME_S = "ttlStdDevTime";

        /** Importance factor of the number of common Moby contacts in trust score */
        private float alpha = 0.3;
        /** Importance factor of the number of common non-Moby contacts in trust score */
        private float beta = 0.2;
        /** Importance factor of the number of communications in trust score */
        private float gamma = 0.5;
        /** Maximum number of Moby contacts in the input sets of PSI-CA */
        private int maxNbMobyContacts = 100;
        /** Maximum number of non-Moby contacts in the input sets of PSI-CA */
        private int maxNbNonMobyContacts = 100;
        /** Maximum possible TTL (in minutes) for Moby messages */
        private int maxTtl = 96 * 60;
	
	/** 
	 * Creates a new Moby application with the given settings.
	 * 
	 * @param s	Settings to use for initializing the application.
	 */
	public MobyApplication(Settings s) {
                this.alpha = s.getDouble(TRUST_WEIGHT_OF_MOBY_CONTACTS_S);
                Settings.ensurePositiveValue(this.alpha, TRUST_WEIGHT_OF_MOBY_CONTACTS_S);
                this.beta = s.getDouble(TRUST_WEIGHT_OF_NON_MOBY_CONTACTS_S);
                Settings.ensurePositiveValue(this.beta, TRUST_WEIGHT_OF_NON_MOBY_CONTACTS_S);
                this.gamma = s.getDouble(TRUST_WEIGHT_OF_NB_COMMUNICATIONS_S);
                Settings.ensurePositiveValue(this.gamma, TRUST_WEIGHT_OF_NB_COMMUNICATIONS_S);
                this.maxNbMobyContacts = s.getInt(MAX_NB_MOBY_CONTACTS_S);
                Settings.ensurePositiveValue((double)this.maxNbMobyContacts, MAX_NB_MOBY_CONTACTS_S);
                this.maxNbNonMobyContacts = s.getInt(MAX_NB_NON_MOBY_CONTACTS_S);
                Settings.ensurePositiveValue((double)this.maxNbNonMobyContacts, MAX_NB_NON_MOBY_CONTACTS_S);
                int ttlMeanTime = s.getInt(TTL_MEAN_TIME_S);
                Settings.ensurePositiveValue((double)ttlMeanTime, TTL_MEAN_TIME_S);
                int ttlStdDevTime = s.getInt(TTL_STD_DEV_TIME_S);
                Settings.ensurePositiveValue((double)ttlStdDevTime, TTL_STD_DEV_TIME_S);
                this.maxTtl = (ttlMeanTime + ttlStdDevTime) * 60;

		/*if (s.contains(PING_PASSIVE)){*/
			//this.passive = s.getBoolean(PING_PASSIVE);
		//}
		//if (s.contains(PING_INTERVAL)){
			//this.interval = s.getDouble(PING_INTERVAL);
		//}
		//if (s.contains(PING_OFFSET)){
			//this.lastPing = s.getDouble(PING_OFFSET);
		//}
		//if (s.contains(PING_SEED)){
			//this.seed = s.getInt(PING_SEED);
		//}
		//if (s.contains(PING_PING_SIZE)) {
			//this.pingSize = s.getInt(PING_PING_SIZE);
		//}
		//if (s.contains(PING_PONG_SIZE)) {
			//this.pongSize = s.getInt(PING_PONG_SIZE);
		//}
		//if (s.contains(PING_DEST_RANGE)){
			//int[] destination = s.getCsvInts(PING_DEST_RANGE,2);
			//this.destMin = destination[0];
			//this.destMax = destination[1];
		//}
		
		/*rng = new Random(this.seed);*/
		super.setAppID(APP_ID);
	}
	
	/** 
	 * Copy-constructor
	 * 
	 * @param a
	 */
	public MobyApplication(MobyApplication a) {
		super(a);
		/*this.lastPing = a.getLastPing();*/
		//this.interval = a.getInterval();
		//this.passive = a.isPassive();
		//this.destMax = a.getDestMax();
		//this.destMin = a.getDestMin();
		//this.seed = a.getSeed();
		//this.pongSize = a.getPongSize();
		//this.pingSize = a.getPingSize();
		/*this.rng = new Random(this.seed);*/

                this.alpha = a.alpha;
                this.beta = a.beta;
                this.gamma = a.gamma;
                this.maxNbMobyContacts = a.maxNbMobyContacts;
                this.maxNbNonMobyContacts = a.maxNbNonMobyContacts;
                this.maxTtl = a.maxTtl;
	}
	
	/** 
	 * Handles an incoming message. Compute its priority and decide 
         * whether to drop it based on queue occupancy and message priority.
	 * 
	 * @param msg	message received by the router
	 * @param host	host to which the application instance is attached
	 */
	@Override
	public Message handle(Message msg, DTNHost host) {
                // Returning null in this function tells the caller to drop msg
                
		String type = (String)msg.getProperty("type");
		if (type==null) return msg; // Not a Moby message
		
		if (type.equalsIgnoreCase("Moby")) {
                        // Check TTL is not too long
                        if (msg.getTtl() > maxTtl) {
                                return null;
                        }
                        
                        // Check host did not already forward msg in the recent past
                        String msgId = msg.getId();
                        if (host.hasAlreadyForwarded(msgId)) {
                                return null;
                        }
                        
                        // Compute host's trust in the forwarderHost
                        List<DTNHost> hostsOnPathOfMsg = msg.getHops();
                        DTNHost forwarderHost = hostsOnPathOfMsg.get(hostsOnPathOfMsg.size() - 1);
                        // Assuming nbCommon*Contacts have already been updated via PSI computation at this point
                        float trustInForwarder = computeTrustScore(host, forwarderHost);

                        // Compute msg's priority
                        float msgPriority = computeMsgPriority(msg, trustInForwarder);
                        msg.updatePriority(msgPriority);
                        
                        // Check msg is already in host's queue & with what priority
                        MobyRouter hostRouter = (MobyRouter)host.getRouter();
                        if (hostRouter.hasMessage(msgId)) {
                                Message msg2 = hostRouter.getMessage(msgId);
                                float msg2Priority = (float)msg2.getProperty("priority");
                                if (msg2Priority != null && msg2Priority > msgPriority) {
                                        msg.updatePriority(msg2Priority);
                                }
                                return null;
                        }

                        // Check queue occupancy & msgPriority to decide whether to add or drop msg
                        if (host.getBufferOccupancy() >= 100) {
                                if (msgPriority <= hostRouter.getLowestPriority()) {
                                        return null;
                                } else {
                                        hostRouter.removeLowestPriorityMessage();
                                }
                        }

                        // If host is the receiver, update some counter(s), and keep msg in the queue because 
                        // MessageRouter.messageTransferred(), which invokes handle(), will put msg only to 
                        // deliveredMessages.
                        if (host.equals(msg.getTo())) {
                                host.incrementNbCommunicationsWith(msg.getFrom().toString());
                                hostRouter.addToMessages(msg, false); // keep msg in the queue
                        }



                        //// Send event to listeners
			//super.sendEventToListeners("GotPing", null, host);
			//super.sendEventToListeners("SentPong", null, host);
		}
		
		return msg;
	}

	/** 
	 * Draws a random host from the destination range
	 * 
	 * @return host
	 */
	//private DTNHost randomHost() {
		//int destaddr = 0;
		//if (destMax == destMin) {
			//destaddr = destMin;
		//}
		//destaddr = destMin + rng.nextInt(destMax - destMin);
		//World w = SimScenario.getInstance().getWorld();
		//return w.getNodeByAddress(destaddr);
	/*}*/
	
	@Override
	public Application replicate() {
		return new MobyApplication(this);
	}

	/** 
	 * Tell host to forget the oldest already-forwarded messages.
	 * 
	 * @param host to which the application instance is attached
	 */
	@Override
	public void update(DTNHost host) {
                host.removeOldMsgFromAlreadyFowarded(SimClock.getIntTime());
	}

	/**
	 * @return the lastPing
	 */
	//public double getLastPing() {
		//return lastPing;
	/*}*/

	/**
	 * @param lastPing the lastPing to set
	 */
	//public void setLastPing(double lastPing) {
		//this.lastPing = lastPing;
	/*}*/

	/**
	 * @return the interval
	 */
	//public double getInterval() {
		//return interval;
	/*}*/

	/**
	 * @param interval the interval to set
	 */
	//public void setInterval(double interval) {
		//this.interval = interval;
	/*}*/

	/**
	 * @return the passive
	 */
	//public boolean isPassive() {
		//return passive;
	/*}*/

	/**
	 * @param passive the passive to set
	 */
	//public void setPassive(boolean passive) {
		//this.passive = passive;
	/*}*/

	/**
	 * @return the destMin
	 */
	//public int getDestMin() {
		//return destMin;
	/*}*/

	/**
	 * @param destMin the destMin to set
	 */
	//public void setDestMin(int destMin) {
		//this.destMin = destMin;
	//}

	/**
	 * @return the destMax
	 */
	//public int getDestMax() {
		//return destMax;
	//}

	/**
	 * @param destMax the destMax to set
	 */
	//public void setDestMax(int destMax) {
		//this.destMax = destMax;
	/*}*/

	/**
	 * @return the seed
	 */
	//public int getSeed() {
		//return seed;
	//}

	/**
	 * @param seed the seed to set
	 */
	//public void setSeed(int seed) {
		//this.seed = seed;
	//}

	/**
	 * @return the pongSize
	 */
	//public int getPongSize() {
		//return pongSize;
	//}

	/**
	 * @param pongSize the pongSize to set
	 */
	//public void setPongSize(int pongSize) {
		//this.pongSize = pongSize;
	//}

	/**
	 * @return the pingSize
	 */
	//public int getPingSize() {
		//return pingSize;
	//}

	/**
	 * @param pingSize the pingSize to set
	 */
	//public void setPingSize(int pingSize) {
		//this.pingSize = pingSize;
	/*}*/

        // This function assumes host.nbCommon*Contacts have already been updated via PSI computation at this point
        public static float computeTrustScore(host, forwarderHost) {
                nbCommonMobyContactsWithForwarder = host.getNbCommonMobyContacts(forwarderHost.toString());
                nbCommonNonMobyContactsWithForwarder = host.getNbCommonNonMobyContacts(forwarderHost.toString());
                nbCommsWithForwarder = host.getNbCommunicationsWith(forwarderHost.toString());

                nbMobyContacts = host.getActualOrMaxNbMobyContacts(maxNbMobyContacts);
                nbNonMobyContacts = host.getActualOrMaxNbNonMobyContacts(maxNbNonMobyContacts);
                highestNbComms = host.getHighestNbCommunications()

                if (nbMobyContacts == 0) {
                        commonMobyContactsFactor = 0.0;
                } else {
                        commonMobyContactsFactor = alpha * (nbCommonMobyContactsWithForwarder / nbMobyContacts);
                }
                if (nbNonMobyContacts == 0) {
                        commonNonMobyContactsFactor = 0.0;
                } else {
                        commonNonMobyContactsFactor = beta * (nbCommonNonMobyContactsWithForwarder / nbNonMobyContacts) 
                }
                if (highestNbComms == 0) {
                        nbCommsFactor = 0.0;
                } else {
                        nbCommsFactor = gamma * (nbCommsWithForwarder / highestNbComms);
                }
                return commonMobyContactsFactor + commonNonMobyContactsFactor + nbCommsFactor;
        }

        public static float computeMsgPriority(msg, trustInForwarder) {
                ttl = msg.getTtl(); // time in minutes
                timeFactor = 0.25 * (1 - ttl / maxTtl);
                
		float forwarderPriority = (float)msg.getProperty("forwarderPriority");
		if (forwarderPriority==null) { // Should not happen
                        forwarderPriorityFactor = 0;
                } else {
                        forwarderPriorityFactor = 0.25 * forwarderPriority;
                }

                return 0.5 * trustInForwarder + timeFactor + forwarderPriorityFactor;
        }

}
