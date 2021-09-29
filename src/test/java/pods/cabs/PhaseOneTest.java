package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.*;

//#definition
public class PhaseOneTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.load());
    // #definition

    private static ActorRef<Void> underTest; 


    @BeforeClass
    public static void setUp() {
        // Config conf = ConfigFactory.load();
        // System.out.println("default-timeout: " + conf.getString("akka.test.default-timeout"));
        TestProbe<Main.Started> testProbe = testKit.createTestProbe();
        underTest = testKit.spawn(Main.create(testProbe.ref()), "defaultActor");
        testProbe.expectMessage(new Main.Started("done"));
    }

    @Test
    public void defaultTest() throws Exception {
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

        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        cab101.tell(new Cab.SignIn(10));
        //Nikita
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");
        // ActorRef<RideService.Command> rideService = Globals.rideService[0];
            // If we are going to raise multiple requests in this script,
            // better to send them to different RideService actors to achieve
            // load balancing.
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();
        Globals.rideService[0].tell(new RideService.RequestRide("201", 10, 100, probe.ref()));
        RideService.RideResponse resp = probe.receiveMessage();
            // Blocks and waits for a response message.
            // There is also an option to block for a bounded period of time
            // and give up after timeout.
        assertNotEquals(resp.rideId, -1);
        cab101.tell(new Cab.RideEnded(resp.rideId));
    }

   // #test
    @Test
    public void pV1() throws Exception {
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

        // Step 1. cab 101 signs in
        ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(0));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        // Step 2 : Customer 201 adds amount to his/her wallet
        ActorRef<Wallet.Command> wallet_201 = Globals.wallets.get("201");
        wallet_201.tell(new Wallet.AddBalance(2000));

        // Step 3: customer 201 requests a ride
        TestProbe<RideService.RideResponse> rideServiceRef = testKit.createTestProbe();
        Globals.rideService[0].tell(new RideService.RequestRide("201", 10, 1100, rideServiceRef.ref()));
        RideService.RideResponse rideResponse = rideServiceRef.receiveMessage();
        assertNotEquals(rideResponse.rideId, -1);

        // Step 4: Customer 201 adds negative amount to his/her wallet
        // This should not be successful but there is no reponse so we cannot check here.
        wallet_201.tell(new Wallet.AddBalance(-2000));

        // Step 5: Get Balnace amoutn in customer 201's wallet
        wallet_201.tell(new Wallet.GetBalance(walletProbe.ref()));
        assertEquals(walletProbe.receiveMessage().balance, 1000);
    }
    // #test

    @Test
    public void pV2() throws Exception {
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

        // Step 1: cab 101 signs in
        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(70));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        // Step 2: cab 102 signs in
        ActorRef<Cab.Command> cab102 = Globals.cabs.get("102");
        if (cab102 != null)
            cab102.tell(new Cab.SignIn(80));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        cab102.tell(new Cab.GetCabStatus(cabProbe.ref()));
        cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        // Step 3: cab 103 signs in
        ActorRef<Cab.Command> cab103 = Globals.cabs.get("103");
        if (cab103 != null)
            cab103.tell(new Cab.SignIn(90));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        cab103.tell(new Cab.GetCabStatus(cabProbe.ref()));
        cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        // Step 4: customer 201 requests a ride
        TestProbe<RideService.RideResponse> rideServiceRef = testKit.createTestProbe();
        Globals.rideService[0].tell(new RideService.RequestRide("201", 10, 100, rideServiceRef.ref()));
        RideService.RideResponse ride1Response = rideServiceRef.receiveMessage();
        assertNotEquals(ride1Response.rideId, -1);

        // Step 5: Customer 202 requests a ride
        Globals.rideService[1].tell(new RideService.RequestRide("202", 10, 110, rideServiceRef.ref()));
        RideService.RideResponse ride2Response = rideServiceRef.receiveMessage();
        assertNotEquals(ride2Response.rideId, -1);

        // Step 6: Customer 203 requests a ride
        Globals.rideService[2].tell(new RideService.RequestRide("203", 10, 120, rideServiceRef.ref()));
        RideService.RideResponse ride3Response = rideServiceRef.receiveMessage();
        assertNotEquals(ride3Response.rideId, -1);

        // Step 7: End Ride1
        cab101.tell(new Cab.RideEnded(ride1Response.rideId));
        TestProbe<Cab.NumRidesResponse> rideProbe2 = testKit.createTestProbe();
        cab101.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num = rideProbe2.receiveMessage();
        assertEquals(num.numRides, 1);

        // Step 8: End ride2
        cab102.tell(new Cab.RideEnded(ride2Response.rideId));
        TestProbe<Cab.NumRidesResponse> rideProbe3 = testKit.createTestProbe();
        cab102.tell(new Cab.NumRides(rideProbe3.ref()));
        Cab.NumRidesResponse num2 = rideProbe3.receiveMessage();
        assertEquals(num2.numRides, 1);

        // Step 9: End ride3
        cab103.tell(new Cab.RideEnded(ride3Response.rideId));
        TestProbe<Cab.NumRidesResponse> rideProbe4 = testKit.createTestProbe();
        cab103.tell(new Cab.NumRides(rideProbe4.ref()));
        Cab.NumRidesResponse num3 = rideProbe4.receiveMessage();
        assertEquals(num3.numRides, 1);

        // Step 10: Cab 104 signs in
        ActorRef<Cab.Command> cab104 = Globals.cabs.get("104");
        if (cab104 != null)
            cab104.tell(new Cab.SignIn(0));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        // Nikita: waiting for response
        cab104.tell(new Cab.NumRides(rideProbe4.ref()));
        Cab.NumRidesResponse num104 = rideProbe4.receiveMessage();
        assertEquals(num104.numRides, 0);

        // Step 11: Customer 201 requests a ride
        Globals.rideService[3].tell(new RideService.RequestRide("201", 100, 10, rideServiceRef.ref()));
        // TODO: Check the error here
        RideService.RideResponse ride4Response = rideServiceRef.receiveMessage();
        assertEquals(ride4Response.rideId, -1);
    }

    @Test
    public void pV3() throws Exception {
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

        // Step 1: Cab 101 signs in
        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(0));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        // Step 2: Customer 201 requests a ride from 1000 to 0
        TestProbe<RideService.RideResponse> rideServiceRef = testKit.createTestProbe();
        Globals.rideService[4].tell(new RideService.RequestRide("201", 1000, 0, rideServiceRef.ref()));
        RideService.RideResponse ride1Response = rideServiceRef.receiveMessage();
        assertEquals(ride1Response.rideId, -1);

        // Step 3 : Check wallet balance for customer 201
        TestProbe<Wallet.ResponseBalance> walletBalanceProbe = testKit.createTestProbe();
        ActorRef<Wallet.Command> wallet_201 = Globals.wallets.get("201");
        wallet_201.tell(new Wallet.GetBalance(walletBalanceProbe.ref()));

        assertEquals(walletBalanceProbe.receiveMessage().balance, 10000);
    }

    @Test
    public void pv4() throws Exception {
        Thread.sleep(2000);
        TestProbe<Cab.NumRidesResponse> resetCab = testKit.createTestProbe();
        Globals.cabs.forEach((k, v) -> {
            v.tell(new Cab.Reset(resetCab.ref()));
            resetCab.receiveMessage();
        });

        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        ActorRef<Cab.Command> cabStatus = Globals.cabs.get("101");
        cabStatus.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-out");
        // assertEquals(resp2.initialPos, 900);
        assertEquals(resp2.minorState, null);
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

        TestProbe<Wallet.ResponseBalance> walletProbe = testKit.createTestProbe();
        Globals.wallets.forEach((k, v) -> {
            v.tell(new Wallet.Reset(walletProbe.ref()));

            assertEquals(walletProbe.receiveMessage().balance, 10000);
        });

        // Step 1: Cab 101 signs in
        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(10));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");
        
        // Step 2: Cab 102 signs in
        ActorRef<Cab.Command> cab102 = Globals.cabs.get("102");
        if (cab102 != null)
            cab102.tell(new Cab.SignIn(30));
        else {
            System.err.println("Sorry that was a null cab reference");
        }
        //Nikita
        cab102.tell(new Cab.GetCabStatus(cabProbe.ref()));
        cabResp = cabProbe.receiveMessage();
        assertEquals(cabResp.majorState, "signed-in");

        // Step 3: Customer 201 requests a ride
        TestProbe<RideService.RideResponse> rideServiceRef = testKit.createTestProbe();
        Globals.rideService[5].tell(new RideService.RequestRide("201", 10, 100, rideServiceRef.ref()));
        RideService.RideResponse ride1Response = rideServiceRef.receiveMessage();
        assertNotEquals(ride1Response.rideId, -1);

        // Step 4: Customer 202 request ride
        Globals.rideService[6].tell(new RideService.RequestRide("201", 10, 110, rideServiceRef.ref()));
        RideService.RideResponse ride2Response = rideServiceRef.receiveMessage();
        assertNotEquals(ride2Response.rideId, -1);

        //Step 5: End ride 1
        cab101.tell(new Cab.RideEnded(ride1Response.rideId));

        TestProbe<Cab.NumRidesResponse> rideProbe2 = testKit.createTestProbe();
        cab101.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num = rideProbe2.receiveMessage();
        assertEquals(num.numRides, 1);

        // Step 6: Customer 201 requests a ride
        Globals.rideService[7].tell(new RideService.RequestRide("201", 100, 10, rideServiceRef.ref()));
        RideService.RideResponse ride3Response = rideServiceRef.receiveMessage();
        assertEquals(ride3Response.rideId, -1);

        // Step 7: Customer 201 requests a ride again
        Globals.rideService[8].tell(new RideService.RequestRide("201", 100, 10, rideServiceRef.ref()));
        RideService.RideResponse ride4Response = rideServiceRef.receiveMessage();
        assertNotEquals(ride4Response.rideId, -1);
    }
}

