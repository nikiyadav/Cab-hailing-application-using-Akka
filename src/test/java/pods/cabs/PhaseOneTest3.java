package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.Before;

import com.typesafe.config.*;

//#definition
public class PhaseOneTest3 {

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
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();

        // #Step 1 : cab 101 signs in
        ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(0));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");
        assertEquals(resp2.initialPos, 0);
        assertEquals(resp2.minorState, "available");
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

        // #Step 2 : cab 102 signs in
        ActorRef<Cab.Command> cab102 =  Globals.cabs.get("102");
        if (cab102 != null)
            cab102.tell(new Cab.SignIn(10));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab102.tell(new Cab.GetCabStatus(cabProbe.ref()));
        resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");
    
        // #Step 3 : cab 103 signs in
        ActorRef<Cab.Command> cab103 =  Globals.cabs.get("103");
        if (cab103 != null)
            cab103.tell(new Cab.SignIn(20));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab103.tell(new Cab.GetCabStatus(cabProbe.ref()));
        resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");

        // #Step 4 : cab 104 signs in
        ActorRef<Cab.Command> cab104 =  Globals.cabs.get("104");
        if (cab104 != null)
            cab104.tell(new Cab.SignIn(30));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab104.tell(new Cab.GetCabStatus(cabProbe.ref()));
        resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");

        // #Step 5 : customer 201 requests a ride from 0 to 10.
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        Globals.rideService[0].tell(new RideService.RequestRide("201", 0, 10, rideProbe.ref()));        
        RideService.RideResponse resp = rideProbe.receiveMessage();
        System.out.print("request-1: rideId = ");
        System.out.print(resp.rideId);
        assertNotEquals(resp.rideId, -1);

        // #Step 6 : customer 202 requests a ride from 10 to 20.
        Globals.rideService[0].tell(new RideService.RequestRide("202", 10, 20, rideProbe.ref()));        
        resp = rideProbe.receiveMessage();
        System.out.print("request-2: rideId = ");
        System.out.print(resp.rideId);
        assertNotEquals(resp.rideId, -1);

        // #Step 7 : customer 203 requests a ride from 20 to 30.
        Globals.rideService[0].tell(new RideService.RequestRide("203", 20, 30, rideProbe.ref()));        
        resp = rideProbe.receiveMessage();
        System.out.print("request-3: rideId = ");
        System.out.print(resp.rideId);
        assertNotEquals(resp.rideId, -1);

        // #Step 8 : customer 204 requests a ride from 0 to 30.
        Globals.rideService[0].tell(new RideService.RequestRide("204", 0, 30, rideProbe.ref()));        
        resp = rideProbe.receiveMessage();
        System.out.print("request-4: rideId = ");
        System.out.print(resp.rideId);
        assertNotEquals(resp.rideId, -1);
    }
}