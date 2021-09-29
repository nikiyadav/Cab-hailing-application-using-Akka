package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.Map;

import java.util.List;
import java.lang.Math;
import java.util.stream.Collectors;

public class FulfillRide extends AbstractBehavior<FulfillRide.Command> {

    public interface Command {
    }

    // WalletBalanceResponse message sent by wallet actor
    public static final class AdaptedWalletBalanceResponse implements Command {
        Wallet.ResponseBalance response;
        
        AdaptedWalletBalanceResponse(Wallet.ResponseBalance response) {
            this.response = response;
        }
    }

    // RequestRideCabResponse message sent by cab actor
    public static final class RequestRideCabResponse implements Command {
        String cabId;
        String response;        // can be "interested" or "not-interested"
        
        RequestRideCabResponse(String cabId, String response) {
            this.response = response;
            this.cabId = cabId;
        }
    }

    // FulfillRideEnded sent by test script to end the ongoing ride
    public static final class FulfillRideEnded implements Command {

    }


    public static Behavior<Command> create(String custId,
                                           int sourceLoc,
                                           int destinationLoc,
                                           ActorRef<RideService.RideResponse> replyTo,
                                           int rideId,
                                           Map<String, cab>cacheTable,
                                           ActorRef<RideService.Command> rideService) {

        return Behaviors.setup(context -> new FulfillRide(custId, sourceLoc,
                                                          destinationLoc, replyTo,
                                                          rideId, cacheTable,
                                                          rideService, context));
    }

    // ride request details
    private String custId;          // customer Id
    private int sourceLoc;          // source location
    private int destinationLoc;     // destination location
    private ActorRef<RideService.RideResponse> replyTo;     // actor reference of test script
    // sent by RideService
    private int rideId;             // ride Id
    private Map<String, cab> cacheTableCopy;        // copy of cache table
    private ActorRef<RideService.Command> parentRideService;        // parent rideService actor reference
    // local variables
    private List<cab> availableCabs;        // list of available cabs
    private String interestedCabId;         // cabId of cab which responded with "interested" status
    private int fare;                       // fare for the ride
    private ActorRef<Wallet.ResponseBalance> responseBalanceWallet;

    private FulfillRide(String custId,
                        int sourceLoc,
                        int destinationLoc,
                         ActorRef<RideService.RideResponse> replyTo,
                        int rideId,
                        Map<String, cab>cacheTable,
                        ActorRef<RideService.Command> rideService,
                        ActorContext<Command> context) {
        super(context);
        context.getLog().info("FulfillRide actor created");
        this.custId = custId;
        this.sourceLoc = sourceLoc;
        this.destinationLoc = destinationLoc;
        this.replyTo = replyTo;
        this.cacheTableCopy = cacheTable;
        this.rideId = rideId;
        this.parentRideService = rideService;
        this.interestedCabId = null;
        this.fare = 0;

        List<cab> cabsAll = new ArrayList<cab>();
        cacheTableCopy.forEach((k,v) -> cabsAll.add(v));

        availableCabs = cabsAll.stream()
            .filter(cab -> cab.getMajorState().equals("signed-in") && cab.getMinorState().equals("available"))
            .sorted((cab1, cab2) -> new Integer(Math.abs(cab1.getInitialPos() - sourceLoc))
                    .compareTo(new Integer(Math.abs(cab2.getInitialPos() - sourceLoc))))
            .limit(3).collect(Collectors.toList());

        if (availableCabs.size() > 0 ) {
            cab availableCab = availableCabs.get(0);
            ActorRef<Cab.Command> cabActor = Globals.cabs.get(availableCab.cabId);
            getContext().getLog().info("cabActor ref {}", cabActor);
            cabActor.tell(new Cab.RequestRide(availableCab.cabId, this.sourceLoc,
                                                this.rideId, this.destinationLoc,
                                                getContext().getSelf()));
                                                
            availableCabs.remove(availableCab);
        }
        else {
            getContext().getSelf().tell(new RequestRideCabResponse(null, "not-interested"));
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(RequestRideCabResponse.class, this::onRequestRideCabResponse)
            .onMessage(AdaptedWalletBalanceResponse.class, this::onAdaptedWalletBalanceResponse)
            .onMessage(FulfillRideEnded.class, this::onFulfillRideEnded)
            .build();
    }

    // FulfillRideEnded message handler
    // Sends UpdateFromFulfillRide message to parent ride service actor and stops itself
    private Behavior<Command> onFulfillRideEnded(FulfillRideEnded command) {
        this.parentRideService.tell(new RideService.UpdateFromFulfillRide(this.interestedCabId, "available", this.destinationLoc,
        -1, -1, -1));
        return Behaviors.stopped();
    }

    // RequestRideCabResponse message handler
    // RequestRideCabResponse is sent by a cab actor as a response to RequestRide message
    // It has a response field which can be "interested" or "not-interested"
    // If response is "interested", then wallet deduct is attempted else RequestRide message is sent to next cab actor
    private Behavior<Command> onRequestRideCabResponse(RequestRideCabResponse command) {
        getContext().getLog().info("FulfillRIde.onRequestRideCabResponse cabId {}", command.cabId);
        if (command.response.equals("interested")) {
            // response is "interested", save the cabId in interestedCabId field.
            this.interestedCabId = command.cabId;
            ActorRef<Wallet.Command> walletActor = Globals.wallets.get(this.custId);
            this.fare = (Math.abs(cacheTableCopy.get(command.cabId).initialPos - this.sourceLoc)
                        + Math.abs(this.sourceLoc - this.destinationLoc)) * 10;
            this.responseBalanceWallet = getContext().messageAdapter(Wallet.ResponseBalance.class, AdaptedWalletBalanceResponse::new);
            // deduct balance from wallet
            walletActor.tell(new Wallet.DeductBalance(fare, this.responseBalanceWallet));
        }
        else {
            // response is "not-interested" or "busy"
            // if some available cab is left then send RequestRide message to it
            if (availableCabs.size() > 0 ) {
                cab availableCab = availableCabs.get(0);
                ActorRef<Cab.Command> cabActor = Globals.cabs.get(availableCab.cabId);
                getContext().getLog().info("cabActor ref {}", cabActor);
                cabActor.tell(new Cab.RequestRide(availableCab.cabId, this.sourceLoc,
                                                    this.rideId, this.destinationLoc,
                                                    getContext().getSelf()));

                availableCabs.remove(availableCab);
            }
            else {
                // If not cab is available, then respond with rideId as -1 to testProbe and stop itself
                getContext().getLog().info("FulfillRIde.onRequestRideCabResponse: No cab found! returning -1");
                this.replyTo.tell(new RideService.RideResponse(-1, null, -1, null));
                return Behaviors.stopped();
            }
        }
        return this;
    }

    // WalletBalanceResponse message handler
    private Behavior<Command> onAdaptedWalletBalanceResponse (AdaptedWalletBalanceResponse response) {
        getContext().getLog().info("FulfillRide.onAdaptedWalletBalanceReponse balance {}", response.response.balance);
        if (response.response.balance != -1) {
            // If deduct was successful then send RideStarted message to cab actor, updateFromfulfillRide message to
            // parent ride service actor, and success response to test script
            if (this.interestedCabId != null) {
                ActorRef<Cab.Command> cabActor = Globals.cabs.get(this.interestedCabId);
                getContext().getLog().info("cabActor ref {}", cabActor);

                // telling cabActor to start the ride
                cabActor.tell(new Cab.RideStarted(this.interestedCabId, this.rideId));

                // telling rideService to update the cache table
                // PhaseOneTest3: comment following line to test PhaseOneTest3
                this.parentRideService.tell(new RideService.UpdateFromFulfillRide(this.interestedCabId, "giving-ride", this.sourceLoc,
                this.rideId, this.sourceLoc, this.destinationLoc));

                // response to test script
                this.replyTo.tell(new RideService.RideResponse(rideId, this.interestedCabId, this.fare ,
                 getContext().getSelf()));
            }
        }
        else {
            // If deduct was unsuccessful, send RideCancelled message to cab actor, and rideId = -1 response to testProbe
            // and stop itself
            if (this.interestedCabId != null) {
                ActorRef<Cab.Command> cabActor = Globals.cabs.get(this.interestedCabId);
                getContext().getLog().info("cabActor ref {}", cabActor);
                cabActor.tell(new Cab.RideCancelled(this.interestedCabId, this.rideId));
                this.replyTo.tell(new RideService.RideResponse(-1, null, -1, null));
                return Behaviors.stopped();
            } 
        }
        return this;
    }
}
