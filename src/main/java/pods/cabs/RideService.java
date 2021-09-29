package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import akka.actor.typed.PostStop;

public class RideService extends AbstractBehavior<RideService.Command> {

    public interface Command {}

    // CabSignsIn message, sent by cab actor to signIn
    public static final class CabSignsIn implements Command {
        public String cabId; 
        public int initialPos;

        CabSignsIn(String cabId, int initialPos) {
            this.cabId = cabId;
            this.initialPos = initialPos;
        }
    }

    // CabSignsOut message, sent by cab actor to sign out
    public static final class CabSignsOut implements Command {
        public String cabId; 

        CabSignsOut(String cabId) {
            this.cabId = cabId;
        }
    }

    // Sent by customer to request ride
    // replyTo is customer actor
    public static final class RequestRide implements Command {
        public String custId;
        public int sourceLoc;
        public int destinationLoc;
        public ActorRef<RideService.RideResponse> replyTo;

        RequestRide(String custId, int sourceLoc, int destinationLoc, ActorRef<RideService.RideResponse> 
        replyTo) {
            this.custId = custId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    // Reply to customer actor, containing the actor reference to Fulfill ride actor
    public static final class RideResponse implements Command {
        public int rideId;
        public String cabId;
        public int fare;
        public ActorRef<FulfillRide.Command> fRide;

        RideResponse(int rideId, String cabId, int fare, ActorRef<FulfillRide.Command> fRide) {
            this.rideId = rideId;
            this.cabId = cabId;
            this.fare = fare;
            this.fRide = fRide;
        }
    }

    // Update message from fulfill ride actor, telling the status of cab
    // when requestRide or rideEnded message is handled by it. 
    public static final class UpdateFromFulfillRide implements Command {
        public String cabId;
        public String minorState;
        public int initialPos;
        public int rideId;
        public int sourceLoc;
        public int destinationLoc;

        UpdateFromFulfillRide(String cabId, String minorState, int initialPos, int rideId, 
        int sourceLoc, int destinationLoc) {
            this.cabId = cabId;
            this.minorState = minorState;
            this.initialPos = initialPos;
            this.rideId = rideId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
        }
    }

    // UpdateCabStatus message is internal message sent across rideService instances to achieve 
    // eventual consistency
    public static final class updateCabStatus implements Command {
        public String cabId;
        public int initialPos;
        public String minorState;
        public String majorState;
        public int rideID;
        public int sourceLoc;
        public int destinationLoc;

        updateCabStatus(String cabId, int initialPos, String majorState, String minorState, int rideId,
            int sourceLoc, int destinationLoc) {
                this.cabId = cabId;
                this.initialPos = initialPos;
                this.majorState = majorState;
                this.minorState = minorState;
                this.rideID = rideId;
                this.sourceLoc = sourceLoc;
                this.destinationLoc = destinationLoc;
        }
    }

    private Map<String, cab> cacheTable = new HashMap<>(); // Internal cache table, key is cabId, and value is cab
    private int rideId;   // current rideId
    private int myId;    // Id of this rideService

    private RideService(ActorContext<Command> context, int id) {
        super(context);
        // initialise myId and rideId
        getContext().getLog().info("Rideservice actor {}, initialising...", id);
        this.myId = id;
        this.rideId = id + 1;
        getContext().getLog().info("Rideservice actor {}, myId = {}, rideId = {}", this.myId, this.myId, this.rideId);

        // initialise cacheTable
        List<String> cabsAll = new ArrayList<String>();
        for (Map.Entry<String, ActorRef<Cab.Command>> en : Globals.cabs.entrySet()) {
            cabsAll.add(en.getKey());
        }
        getContext().getLog().info("{}", cabsAll);
        for (String cabId: cabsAll) {
            cab cab1 = new cab(cabId);
            this.cacheTable.put(cabId, cab1);
        }
        context.getLog().info("Ride service actor created");
    }

    public static Behavior<Command> create(int id) {
        return Behaviors.setup(context -> new RideService(context, id));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(RequestRide.class, this::onRequestRide)
            .onMessage(CabSignsIn.class, this::onCabSignsIn)
            .onMessage(CabSignsOut.class, this::onCabSignsOut)
            .onMessage(UpdateFromFulfillRide.class, this::onUpdateFromFulfillRide)
            .onMessage(updateCabStatus.class, this::onupdateCabStatus)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    // UpdateCabStatus message handler
    // UpdateCabStatus message is a cab status update message sent by a rideService to other rideServices.
    // It is sent when CabSignsIn, CabSignsOut and UpdateFromFulfillRide messages are received.
    // When this message is received by the rideService, then it applies the update on its cacheTable.
    private RideService onupdateCabStatus(updateCabStatus command) {
        getContext().getLog().info("(updateCabStatus msg received cabId = {}", command.cabId);

        String cabId = command.cabId;
        // get cacheTable entry
        cab cab1 = cacheTable.get(cabId);
        if (cab1 != null) { // checking that cabId is valid
            cab1.initialPos = command.initialPos;
            cab1.majorState = command.majorState;
            cab1.minorState = command.minorState;
            cab1.rideID = command.rideID;
            cab1.sourceLoc = command.sourceLoc;
            cab1.destinationLoc = command.destinationLoc;

            cacheTable.put(cabId, cab1);
            getContext().getLog().info("updated cacheTable: cab Id {} Loc {} majorState {} minorState {}", cab1.cabId, cab1.initialPos,cab1.majorState, cab1.minorState);
        }
        return this;
    }

    // RequestRide message handler
    // It creates a rideId and spwans a fulfillRide actor
    // It passes rideId, ride details, and a copy of cache table to the fulfillRide actor
    // It passes its own reference to fulfillRide actor to receive updates from it
    private RideService onRequestRide(RequestRide command) {
        getContext().getLog().info("RequestRide msg received from cust {}", command.custId);
        // check sourceLoc and DestLoc are non-negative
        if (command.sourceLoc < 0 || command.destinationLoc < 0) {
            getContext().getLog().info("Received negative source loc or dest loc, exiting..");
            return this;
        }
        
        rideId = rideId + 10;
        // spawned fulfill ride actor
        ActorRef<FulfillRide.Command> fRide =
            getContext().spawn(FulfillRide.create(command.custId,
                                                  command.sourceLoc,
                                                  command.destinationLoc,
                                                  command.replyTo,
                                                  rideId,
                                                  cacheTable,
                                                  getContext().getSelf()),
                               "fRideActor-" + rideId);
        return this;
    }

    // CabSignsIn message handler
    // sends a updateCabStatus message to all rideServices and updates its own cacheTable
    private RideService onCabSignsIn(CabSignsIn command) {
        getContext().getLog().info("CabSignIn {} msg received", command.cabId);

        //send updateSignIn message to all rideServices except itself
        for(int id=0; id<10; id++) {
            if(id != myId) {
                Globals.rideService[id].tell(new updateCabStatus(
                    command.cabId,
                    command.initialPos, 
                    "signed-in", 
                    "available", 
                      -1,
                      -1, 
                      -1
                ));
            }
        }

        // update cacheTable entry
        String cabId = command.cabId;
        int initialPos = command.initialPos;

        // get cacheTable entry
        cab cab1 = cacheTable.get(cabId);
        if (cab1 != null) { // checking that cabId is valid
            cab1.initialPos = initialPos;
            cab1.majorState = "signed-in";
            cab1.minorState = "available";
            cacheTable.put(cabId, cab1);
            getContext().getLog().info("signed-in: cab Id {} Loc {} majorState {} minorState {}", cab1.cabId, cab1.initialPos,cab1.majorState, cab1.minorState);
        }
        return this;
    }

    // CabSignsOut message handler
    // sends a updateCabStatus message to all rideServices and updates its own cacheTable
    private RideService onCabSignsOut(CabSignsOut command) {
        getContext().getLog().info("CabSignOut msg received");

        //send updateSignOut message to all rideServices except itself
        for(int id=0; id<10; id++) {
            if(id != myId) {
                getContext().getLog().info("CabSignOut: sending update to rideService = {}", id);
                Globals.rideService[id].tell(new updateCabStatus(
                    command.cabId,
                     -1, 
                    "signed-out", 
                     null, 
                      -1,
                      -1, 
                      -1
                ));
            }
        }

        //update cacheTable
        String cabId = command.cabId;
        // get cacheTable entry
        cab cab1 = cacheTable.get(cabId);
        if (cab1 != null) { // checking that cabId is valid
            getContext().getLog().info("CabSignOut: updating cache table");
            cab1.initialPos = -1;
            cab1.majorState = "signed-out";
            cab1.minorState = null;
            cab1.rideID = -1;
            cab1.sourceLoc = -1;
            cab1.destinationLoc = -1;
            cacheTable.put(cabId, cab1);
        }
        return this;
    }

    // updateFromfulfillRide message handler
    // fulfillRide actor sends cab status update after handing requestRide and rideEnded messages
    // Message is handled by sending an updateCabStatus message to all rideservices and
    // by updating the local cache table
    private RideService onUpdateFromFulfillRide(UpdateFromFulfillRide command) {
        getContext().getLog().info("UpdateFromFulfillRide msg received cab Id {}, initial Pos {}, minorState {} rideID {}, sourceLoc {}, des {} ", command.cabId, command.initialPos, command.minorState, command.rideId, command.sourceLoc, command.destinationLoc);
        
        //send updateCabStatus message to all rideServices except itself
        for(int id=0; id<10; id++) {
            if(id != myId) {
                Globals.rideService[id].tell(new updateCabStatus(
                    command.cabId,
                    command.initialPos, 
                    "signed-in", 
                    command.minorState, 
                    command.rideId,
                    command.sourceLoc, 
                    command.destinationLoc
                ));
            }
        }
        
        //updateCacheTable
        String cabId = command.cabId;
        // get cacheTable entry
        cab cab1 = cacheTable.get(cabId);
        if (cab1 != null) { // checking that cabId is valid
            cab1.initialPos = command.initialPos;
            cab1.minorState = command.minorState;
            cab1.rideID = command.rideId;
            cab1.sourceLoc = command.sourceLoc;
            cab1.destinationLoc = command.destinationLoc;
            cacheTable.put(cabId, cab1);
        }
        return this;
    }

    // On stop message handler
    private Behavior<Command> onPostStop() {
        getContext().getLog().info("RideService actor {} stopped", this.myId);
        return Behaviors.stopped();
    }

}
