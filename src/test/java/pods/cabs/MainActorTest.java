package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;

import com.typesafe.config.*;

//#definition
public class MainActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.load());
    // #definition

    private static ActorRef<Void> underTest; 

    @BeforeClass
    public static void setUp() {
        TestProbe<Main.Started> testProbe = testKit.createTestProbe();
        underTest = testKit.spawn(Main.create(testProbe.ref()), "defaultActor");
        testProbe.expectMessage(new Main.Started("done"));
    }

    // #test
    @Test
    public void Test1() throws Exception {
        Thread.sleep(2000);
        TestProbe<Cab.NumRidesResponse> resetCab = testKit.createTestProbe();
        Globals.cabs.forEach((k, v) -> {
                v.tell(new Cab.Reset(resetCab.ref()));
                resetCab.receiveMessage();
            });

        TestProbe<Wallet.ResponseBalance> walletProbe = testKit.createTestProbe();
        Globals.wallets.forEach((k, v) -> {
                v.tell(new Wallet.Reset(walletProbe.ref()));

                assertEquals(walletProbe.receiveMessage().balance, 10000);
        });

        ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(100));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        //Nikita
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        ActorRef<Cab.Command> cab102 =  Globals.cabs.get("102");
        if (cab102 != null)
            cab102.tell(new Cab.SignIn(0));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        //Nikita
        cab102.tell(new Cab.GetCabStatus(cabProbe.ref()));
        cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        System.out.print("cabs signed-in, sending rideRequest");

        // Thread.sleep(2000);
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        // ActorRef<RideService.Command> rideTest = testKit.spawn(RideService.create(), "rideServiceActor");
        System.out.print(Globals.rideService[0]);
        Globals.rideService[0].tell(new RideService.RequestRide("201", 0, 10, rideProbe.ref()));        
        RideService.RideResponse resp = rideProbe.receiveMessage();
        assertNotEquals(resp.rideId, -1);

        cab102.tell(new Cab.RideEnded(resp.rideId));
        TestProbe<Cab.NumRidesResponse> rideProbe2 = testKit.createTestProbe();
        cab102.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num = rideProbe2.receiveMessage();
        assertEquals(num.numRides, 1);
    }
    // #test
}

