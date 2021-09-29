package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Random;

public class Cab extends AbstractBehavior<Cab.Command> {

    public interface Command {
    }

    public static Behavior<Command> create(String cabId) {
        return Behaviors.setup(context -> new Cab(context, cabId));
    }

    // RideEnded message is sent by test script to end a ride
    public static final class RideEnded implements Command {
        int rideId;

        RideEnded(int rideId) {
            this.rideId = rideId;
        }
    }

    // SignIn message is sent by a test script to sign-in the cab
    public static final class SignIn implements Command {
        int initialPos;

        SignIn(int initialPos) {
            this.initialPos = initialPos;
        }
    }

    // SignOut message is sent by a test script to sign-out the cab
    public static final class SignOut implements Command {

    }

    // GetCabStatus is sent by test script to ask the cab status, added for test purpose
    public static final class GetCabStatus implements Command {
        ActorRef<Cab.CabStatus> replyTo;

        GetCabStatus(ActorRef<Cab.CabStatus> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Reply to GetCabStatus message
    public static final class CabStatus implements Command {
        String majorState;
        String minorState;
        int initialPos;
        int rideId;
        int numRides;

        CabStatus(String majorState, String minorState, int pos, int rideId, int numRides) {
            this.majorState = majorState;
            this.minorState = minorState;
            this.initialPos = pos;
            this.rideId = rideId;
            this.numRides = numRides;
        }
    }

    // RequestRide message is sent a fulfillRide actor to request a ride
    public static final class RequestRide implements Command {
        String cabId;
        int sourceLoc;
        int destinationLoc;
        int rideId;
        ActorRef<FulfillRide.Command> replyTo;

        RequestRide(String cabId, int sourceLoc, int rideId, int destinationLoc, ActorRef<FulfillRide.Command> replyTo) {
            this.cabId = cabId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.rideId = rideId;

            this.replyTo = replyTo;
        }
    }

    // RideStarted message is sent by fulfillRide actor to start the ride
    public static final class RideStarted implements Command {
        String cabId;
        int rideId;

        RideStarted(String cabId, int rideId) {
            this.cabId = cabId;
            this.rideId = rideId;
        }
    }

    // RideCancelled message is sent by fulfillRide actor to cancel the ride
    public static final class RideCancelled implements Command {
        String cabId;
        int rideId;

        RideCancelled(String cabId, int rideId) {
            this.cabId = cabId;
            this.rideId = rideId;
        }
    }

    // NumRides message is sent by test script to get number of rides
    public static final class NumRides implements Command {
        ActorRef<NumRidesResponse> replyTo;

        NumRides(ActorRef<NumRidesResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // Reply to NumRides message, sends the number of rides
    public static final class NumRidesResponse implements Command {
        int numRides;

        NumRidesResponse(int numRides) {
            this.numRides = numRides;
        }
    }

    // Reply to RequestRide message, tells if cab is interested in giving ride or not.
    public static final class RequestRideResponse implements Command {
        String isInterested;
        String cabId;
            
        RequestRideResponse(String cabId, String response) {
            this.cabId = cabId;
            this.isInterested = response;
        }
    }

    // Reset message is sent by test script to reset the cab
    public static final class Reset implements Command {
        ActorRef<NumRidesResponse> replyTo;

        Reset(ActorRef<NumRidesResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    private String cabId;      // cab Id
    private int initialPos;    // Cab position
    private String minorState; // Can be either available/committed/giving-ride
    private String majorState; // Can be either signed-in/signed-out
    private Boolean lastRide; // Whether last request was fulfilled
    // lastRide = true indicate that cab accepted the last ride request
    // lastRide = false indicate that cab declined the last ride request
    // cab accepts every alternate request
    private int rideID; // If giving-ride, then rideId of current ride
    private int sourceLoc; // If giving-ride, then source location of current ride
    private int destinationLoc; // If giving-ride, then destination location of current ride
    ActorRef<FulfillRide.Command> fulfillRideActor; // If giving-ride, then reference of fulfillRide actor
    private int numRides; // number of rides given by cab since last sign-In

    // constructor
    private Cab(ActorContext<Command> context, String cabId) {
        super(context);
        this.cabId = cabId;
        this.initialPos = -1;
        this.majorState = "signed-out";
        this.minorState = null;
        this.lastRide = false;
        this.rideID = -1;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
        this.numRides = 0;
        this.fulfillRideActor = null;

        context.getLog().info("Cab actor created for id {}", cabId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(RideEnded.class, this::onRideEnded)
            .onMessage(SignIn.class, this::onSignIn)
            .onMessage(SignOut.class, this::onSignOut)
            .onMessage(NumRides.class, this::onNumRides)
            .onMessage(Reset.class, this::onReset)
            .onMessage(RequestRide.class, this::onRequestRide)
            .onMessage(RideStarted.class, this::onRideStarted)
            .onMessage(RideCancelled.class, this::onRideCancelled)
            .onMessage(GetCabStatus.class, this::onGetCabStatus)
            .build();
    }

    // GetCabStatus message handler
    // Sends a CabStatus message to the source actor
    private Cab onGetCabStatus(GetCabStatus command) {
        getContext().getLog().info("getCabStatus message received");
        command.replyTo.tell(new CabStatus(this.majorState, this.minorState, this.initialPos, this.rideID, this.numRides));
        return this;
    }

    // RideEnded message handler
    // If rideId is correct, then sends a RideEnded message to fulfillRide actor and ends the ride.
    private Cab onRideEnded(RideEnded command) {
        getContext().getLog().info("Ride Ended message received by cab {}", command.rideId);
        if (command.rideId != this.rideID) {
            getContext().getLog().info("Received ride Id {}  does not match current ride Id {}, cannot end ride!", command.rideId, this.rideID);
            return this;
        }
        this.initialPos = this.destinationLoc;  // Set cab position to destination location
        this.minorState = "available";          // Set cab minorState to available
        this.rideID = -1;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
        this.fulfillRideActor.tell(new FulfillRide.FulfillRideEnded()); // Tell fulfillRide actor to end the ride
        this.fulfillRideActor = null;
        return this;
    }

    // SignIn message handler
    // If initial position is not negative and cab is not in signed-in state, then signs in the cab
    private Cab onSignIn(SignIn command) {
        getContext().getLog().info("Sign in happening of {} at initialPos {}", this.cabId, command.initialPos);
        // check initialPos is non-negative
        if (command.initialPos < 0) {
            getContext().getLog().info("Received negative initial pos, exiting..");
            return this;
        }
        // check cab is not already signed-in
        if (this.majorState.equals("signed-in")) {
            getContext().getLog().info("Already signed-in! Exiting..");
            return this;
        }
        this.initialPos = command.initialPos;   // set cab positon to initial position
        this.majorState = "signed-in";          // set majorState to signed-in
        this.minorState = "available";          // set minorState to available
        this.lastRide = false;                  // set lastRide to false
        this.numRides = 0;                      // set numRides to 0
        this.rideID = -1;                       // set rideId to -1

        // send CabSignsIn message to a randomly selected rideService
        Random rand = new Random();
        int rideServiceId = rand.nextInt(10);
        getContext().getLog().info("SignIn: random id = {}", rideServiceId);
        if(Globals.rideService[rideServiceId] != null) {
            getContext().getLog().info("SignIn: Sending cabSignsIn message to rideService-{}", rideServiceId);
            Globals.rideService[rideServiceId].tell(new RideService.CabSignsIn(cabId, initialPos));
        }
        return this;
    }

    // SignOut message handler
    // If  cab is not in signed-out state, then signs out the cab
    private Cab onSignOut(SignOut command) {
        getContext().getLog().info("Sign out request came.");
        // check cab is not in signed-out state
        if (this.majorState.equals("signed-out")) {
            getContext().getLog().info("Already signed-out. Exiting...");
            return this;
        }
        // check cab is in signed-in and available state. Cannot sign-out a cab which is either in committed
        // or giving-ride state
        if (this.majorState.equals("signed-in")
            && this.minorState.equals("available")) {
            // initialise all variables
            this.initialPos = -1;
            this.majorState = "signed-out";
            this.minorState = null;
            this.sourceLoc = -1;
            this.destinationLoc = -1;
            this.numRides = 0;
            this.lastRide = false;
            this.rideID = -1;
            this.fulfillRideActor = null;

            // send CabSignsOut message to a randomly selected rideService
            Random rand = new Random();
            int rideServiceId = rand.nextInt(10);
            getContext().getLog().info("SignOut: random id = {}", rideServiceId);
            if(Globals.rideService[rideServiceId] != null) {
                Globals.rideService[rideServiceId].tell(new RideService.CabSignsOut(cabId));
            }
        }
        else {
            getContext().getLog().info("Cannot sign-out, cab is not in signed-in and available state. Exiting...");
        }
        return this;
    }

    // NumRides message handler
    // Sends NumRidesResponse message to source
    private Cab onNumRides(NumRides command) {
        getContext().getLog().info("Querying number of rides, sending {}", this.numRides);
        command.replyTo.tell(new NumRidesResponse(this.numRides));
        return this;
    }

    // Reset message handler
    // If cab is in giving-ride state, then end the ride
    // Signs-out the cab and sends CabSignsOut message to a randomly selected rideService
    // Responds with number of rides (numRidesResponse) message to source
    private Cab onReset(Reset command) {
        getContext().getLog().info("Reset cab");
        // Cab should behave as if the test program sent it a RideEnded command for the ongoing ride
        if (this.minorState != null && this.minorState.equals("giving-ride")) {
            this.fulfillRideActor.tell(new FulfillRide.FulfillRideEnded());
        }

        // Sign out
        this.initialPos = -1; 
        this.majorState = "signed-out";
        this.minorState = null;
        int result = this.numRides;
        this.numRides = 0;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
        this.rideID = -1;
        this.lastRide = false;
        this.fulfillRideActor = null;

        // Send CabSignsOut message to a randomly selected rideService
        Random rand = new Random();
        int rideServiceId = rand.nextInt(10);
        getContext().getLog().info("Reset: random id = {}", rideServiceId);
        if(Globals.rideService[rideServiceId] != null) {
            Globals.rideService[rideServiceId].tell(new RideService.CabSignsOut(cabId));
        }

        // Respond with number of rides to test script
        command.replyTo.tell(new NumRidesResponse(result));
        return this;
    }

    // RequestRide message handler
    // Cab accepts every alternate request
    private Cab onRequestRide(RequestRide command) {
        getContext().getLog().info("Inside Cab RequestRide {} {}", command.cabId, command.rideId);
        // check sourcLoc and destLoc are non-negative (>=0)
        if (command.sourceLoc < 0 || command.destinationLoc < 0) {
            getContext().getLog().info("Received negative source loc or dest loc, exiting..");
            command.replyTo.tell(new FulfillRide.RequestRideCabResponse(command.cabId, "not-interested"));
            return this;
        }
        // check cab is not in signed-out state
        if (this.majorState.equals("signed-out")) {
            command.replyTo.tell(new FulfillRide.RequestRideCabResponse(command.cabId, "not-interested"));
            return this;
        }
        // check cab is in signed-in and available state, it accepts ride only if lastRide is false
        if (this.majorState.equals("signed-in")
            && this.minorState.equals("available")
            && !this.lastRide) {
            getContext().getLog().info("stmt1: cab {} is signed-in and available, lastRide = {}", command.cabId, this.lastRide);
            this.minorState = "committed";                // set minorState to committed
            this.rideID = command.rideId;                 // set rideId, sourceLoc and destinationLoc
            this.sourceLoc = command.sourceLoc;
            this.destinationLoc = command.destinationLoc;
            this.lastRide = true;                         //set lastRide to true
            // Send response to fulfillRide actor
            command.replyTo.tell(new FulfillRide.RequestRideCabResponse(command.cabId, "interested"));
            // initialising fulfillRide actor ref
            this.fulfillRideActor = command.replyTo;
            return this;
        }
        else if (this.majorState.equals("signed-in") && this.minorState.equals("available") && this.lastRide) {
            getContext().getLog().info("stmt2: cab {} is signed-in and available, lastRide = {}", command.cabId, this.lastRide);
            this.lastRide = false; //set lastRide to false
            // Send response to fulfillRide actor
            command.replyTo.tell(new FulfillRide.RequestRideCabResponse(command.cabId, "not-interested"));
            return this;
        }
        // if cab is in committed/giving-ride, send status "busy"
        getContext().getLog().info("stmt3: cab {} is in committed/giving-ride state", command.cabId);
        command.replyTo.tell(new FulfillRide.RequestRideCabResponse(command.cabId, "busy"));
        return this; 
    }

    // RideStarted message handler
    private Cab onRideStarted(RideStarted command) {
        getContext().getLog().info("Inside Cab RideStarted {} {}", command.cabId, command.rideId);
        if (this.majorState.equals("signed-in")
            && this.minorState.equals("committed")
            && this.rideID == command.rideId
            && this.cabId.equals(command.cabId)) {

            this.minorState = "giving-ride";      // set minorState to giving-ride
            this.numRides++;                      // increment numRides
            getContext().getLog().info("Number of rides updated = {}", this.numRides);
            this.initialPos = this.sourceLoc;     // set cab position to source location
            return this;
        }
        return this;
    }

    // RideCancelled message handler
    private Cab onRideCancelled(RideCancelled command) {
        getContext().getLog().info("Inside Cab RideCancelled {} {}", command.cabId, command.rideId);
        if (this.majorState.equals("signed-in")
            && this.minorState.equals("committed")
            && this.rideID == command.rideId
            && this.cabId.equals(command.cabId)){

            this.minorState = "available";   // set minorState to available
            this.rideID = -1;
            this.sourceLoc = -1;
            this.destinationLoc = -1;
            this.fulfillRideActor = null;
            return this;
        }
        return this;
    }

}
