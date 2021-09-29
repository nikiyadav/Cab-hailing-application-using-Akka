package pods.cabs;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import akka.actor.typed.ActorRef;

public class Globals {
    // Hashmap to store the cab actors in a map of type <String -> actor refs to Cab actors>
    public static Map<String, ActorRef<Cab.Command>> cabs = new HashMap<>();

    // Hashmap to store the wallet actors in a map of type <String -> actor refs to wallet actors>
    public static Map<String, ActorRef<Wallet.Command>> wallets = new HashMap<>();
    
    // ArrayList to store rideService actors
    public static List<ActorRef<RideService.Command>> rideServiceList;
    // Array of rideService actors
    public static ActorRef<RideService.Command>[] rideService;
}
