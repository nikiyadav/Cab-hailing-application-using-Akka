package pods.cabs;

import java.io.File;
import java.util.Scanner;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.lang.Object;

public class Main extends AbstractBehavior<Main.Command> {

    public interface Command {
    }

    public static final class Started implements Command {
        public String status;

        Started(String status) {
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Started started = (Started) o;
            return Objects.equals(status, started.status);
        }

    }

    public static final class Start implements Command {
        ActorRef<Main.Started> replyTo;

        Start(ActorRef<Main.Started> replyTo) {
            this.replyTo = replyTo;
        }
    }

    private Main(ActorContext<Command> context) {
        super(context);
        context.getLog().info("Main actor being created");
    }

    public static Behavior<Void> create(ActorRef<Main.Started> testProbe) {
        return Behaviors.setup(context -> {

            try {
                File myFile = new File("./IDs.txt");
                Scanner myReader = new Scanner(myFile);
                myReader.nextLine();
                while (myReader.hasNext()) {
                    String id = myReader.nextLine().trim();
                    if (id.equals("****"))
                        break;
                    // spawning cab actor storing it in Globals.cabs map
                    ActorRef<Cab.Command> cabActor = context.spawn(Cab.create(id), "cab-" + id);
                    Globals.cabs.put(id, cabActor);
                }
                List<String> customers = new ArrayList<>();
                while (myReader.hasNext()) {
                    String id = myReader.nextLine().trim();
                    if (id.equals("****"))
                        break;
                    customers.add(id);
                }
                for (String cust : customers) {
                    String id = myReader.nextLine().trim();

                    // spawning wallet actor and storing it in Globals.wallets map
                    ActorRef<Wallet.Command> walletActor = context.spawn(Wallet.create(cust, Integer.parseInt(id)),
                            "wallet-" + cust);
                    Globals.wallets.put(cust, walletActor);

                }
                myReader.close();
            } catch (Exception e) {
                e.getMessage();
            }

            // Creating rideService actors
            Globals.rideServiceList = new ArrayList<>();
            for(int id=0; id<10; id++) {
                // Spawning rideService actor and sending id (range 0 to 9) to create method
                Globals.rideServiceList.add(context.spawn(RideService.create(id), "ride-service-"+id));
            }
            Globals.rideService = new ActorRef[Globals.rideServiceList.size()];
            // Storing actors in rideService array
            Globals.rideService = Globals.rideServiceList.toArray(Globals.rideService);

            // Return a Started message to signify all the actors have been spawned
            context.getLog().info("Sending done message to testProbe");
            testProbe.tell(new Main.Started("done"));

            return Behaviors.empty(); // it does want to receive any messages

        });
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().onMessage(Start.class, this::onStart).build();
    }

    private Behavior<Command> onStart(Start command) {
        getContext().getLog().info("Received start message");
        command.replyTo.tell(new Started("done"));
        return Behaviors.empty();
    }
}
