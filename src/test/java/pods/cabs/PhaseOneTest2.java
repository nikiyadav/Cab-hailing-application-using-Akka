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
public class PhaseOneTest2 {

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
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();

        // #Step 1 : cab 101 signs in
        ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(900));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");
        assertEquals(resp2.initialPos, 900);
        assertEquals(resp2.minorState, "available");
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

        // #Step 2 : customer 201 requests a ride from 1000 to 0.
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        Globals.rideService[0].tell(new RideService.RequestRide("201", 1000, 0, rideProbe.ref()));        
        RideService.RideResponse resp = rideProbe.receiveMessage();
        assertEquals(resp.rideId, -1);

        // #Step 3 : Checks wallet balance for the Customer 201.
        ActorRef<Wallet.Command> wallet_201 = Globals.wallets.get("201");
        wallet_201.tell(new Wallet.GetBalance(walletProbe.ref()));
        assertEquals(walletProbe.receiveMessage().balance, 10000);

        // #Step 4 : customer 201 requests a ride from 100 to 0.
        // Ride is denied because cab had shown interest in giving last ride (1000,0), this time it is not interested
        Globals.rideService[1].tell(new RideService.RequestRide("201", 100, 0, rideProbe.ref()));        
        resp = rideProbe.receiveMessage();
        assertEquals(resp.rideId, -1);
    }

    @Test
    public void pV4() throws Exception {
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
            cab101.tell(new Cab.SignIn(10));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");
        assertEquals(resp2.initialPos, 10);
        assertEquals(resp2.minorState, "available");
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

        // #Step 2 : cab 102 signs in
        ActorRef<Cab.Command> cab102 =  Globals.cabs.get("102"); 
        if (cab102 != null)
            cab102.tell(new Cab.SignIn(30));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab102.tell(new Cab.GetCabStatus(cabProbe.ref()));
        resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");
        assertEquals(resp2.initialPos, 30);
        assertEquals(resp2.minorState, "available");
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

        // #Step 3 : customer 201 requests a ride
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        Globals.rideService[0].tell(new RideService.RequestRide("201", 10, 100, rideProbe.ref()));        
        RideService.RideResponse resp1 = rideProbe.receiveMessage();
        assertNotEquals(resp1.rideId, -1);

        // #Step 4 : customer 202 requests a ride
        Globals.rideService[1].tell(new RideService.RequestRide("202", 10, 110, rideProbe.ref()));        
        RideService.RideResponse resp3 = rideProbe.receiveMessage();
        assertNotEquals(resp3.rideId, -1);

        // #Step 5 : End ride1
        cab101.tell(new Cab.RideEnded(resp1.rideId));
        TestProbe<Cab.NumRidesResponse> rideProbe2 = testKit.createTestProbe();
        cab101.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num = rideProbe2.receiveMessage();
        assertEquals(num.numRides, 1);

        // #Step 6 : customer 201 requests a ride
        // Problem is that request is not coming to cab 101 as stale cacheTable shows that it is giving-ride
        Globals.rideService[2].tell(new RideService.RequestRide("201", 100, 10, rideProbe.ref()));        
        RideService.RideResponse resp4 = rideProbe.receiveMessage();
        assertEquals(resp4.rideId, -1);

        // #Step 7 : customer 201 requests a ride again
        // Now if request comes to cab 101 it will deny since it should deny alternate requests
        Globals.rideService[3].tell(new RideService.RequestRide("201", 100, 10, rideProbe.ref()));        
        resp4 = rideProbe.receiveMessage();
        assertNotEquals(resp4.rideId, -1);
    }

    // #test
    @Test
    public void pV5() throws Exception {
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

        // #Step 1 :  sign in of Invalid Cab
        ActorRef<Cab.Command> cab901 =  Globals.cabs.get("901");
        if (cab901 != null)
            cab901.tell(new Cab.SignIn(100));
        else {
            System.out.println("Sorry that was a null cab reference");
        }

        // #Step 2 :  sign Out of Cab which is already Signed Out
        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp0 = cabProbe.receiveMessage();
        assertEquals(resp0.majorState, "signed-out");

        if (cab101 != null)
            cab101.tell(new Cab.SignOut());
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp1 = cabProbe.receiveMessage();
        assertEquals(resp1.majorState, "signed-out");

        // #Step 3 :  sign in 101
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(900));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp2 = cabProbe.receiveMessage();
        assertEquals(resp2.majorState, "signed-in");
        assertEquals(resp2.initialPos, 900);
        assertEquals(resp2.minorState, "available");
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

        // #Step 4 :  sign in of Cab which is already Signed in
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(100));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp3 = cabProbe.receiveMessage();
        assertEquals(resp3.majorState, "signed-in");
        assertEquals(resp3.initialPos, 900);
        assertEquals(resp2.minorState, "available");
        assertEquals(resp2.rideId, -1);
        assertEquals(resp2.numRides, 0);

    }

    @Test
    public void pV6() throws Exception {
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

        // #Step 2 : cab 101 signs in
        ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
        if (cab101 != null)
            cab101.tell(new Cab.SignIn(0));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp1 = cabProbe.receiveMessage();
        assertEquals(resp1.majorState, "signed-in");

        // #Step 3 : cab 102 signs in
        ActorRef<Cab.Command> cab102 =  Globals.cabs.get("102");
        if (cab102 != null)
            cab102.tell(new Cab.SignIn(100));
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab102.tell(new Cab.GetCabStatus(cabProbe.ref()));
        resp1 = cabProbe.receiveMessage();
        assertEquals(resp1.majorState, "signed-in");

        // #Step 4 : customer 201 requests a ride
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        Globals.rideService[5].tell(new RideService.RequestRide("201", 10, 100, rideProbe.ref()));        
        RideService.RideResponse resp = rideProbe.receiveMessage();
        assertNotEquals(resp.rideId, -1);

        // #Step 4.1: Cannot sign-out because cab is giving ride
        if (cab101 != null)
            cab101.tell(new Cab.SignOut());
        else {
            System.out.println("Sorry that was a null cab reference");
        }
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        resp1 = cabProbe.receiveMessage();
        assertEquals(resp1.majorState, "signed-in");

        // #Step 5 :  Numrides for of Cab 101
        TestProbe<Cab.NumRidesResponse> rideProbe2 = testKit.createTestProbe();
        cab101.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num = rideProbe2.receiveMessage();
        assertEquals(num.numRides, 1);

        // #Step 6 :  Numrides for of Cab 102
        // TestProbe<Cab.NumRidesResponse> rideProbe2 = testKit.createTestProbe();
        cab102.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num2 = rideProbe2.receiveMessage();
        assertEquals(num2.numRides, 0);

        // #Step 7 :  Numrides for of Cab 103 ---in Signed OUT state
        ActorRef<Cab.Command> cab103 =  Globals.cabs.get("103");
        cab103.tell(new Cab.NumRides(rideProbe2.ref()));
        Cab.NumRidesResponse num3 = rideProbe2.receiveMessage();
        assertEquals(num3.numRides, 0);


        // cab101.tell(new Cab.RideEnded(resp.rideId));
    }

    @Test
    public void pV7() throws Exception {
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

         // #Step 2 : cab 101 signs in
         ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
         if (cab101 != null)
             cab101.tell(new Cab.SignIn(0));
         else {
             System.out.println("Sorry that was a null cab reference");
         }
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp1 = cabProbe.receiveMessage();
        assertEquals(resp1.majorState, "signed-in");
        assertEquals(resp1.minorState, "available");
        
        // #Step 2 : customer 201 requests a ride and it is denied because of insufficient balance
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        Globals.rideService[6].tell(new RideService.RequestRide("201", 10, 1100, rideProbe.ref()));        
        RideService.RideResponse resp = rideProbe.receiveMessage();
        assertEquals(resp.rideId, -1);
    }

    @Test
    public void pV8() throws Exception {
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

        // #Step 1 : cab 101 signs in
        ActorRef<Cab.Command> cab101 =  Globals.cabs.get("101");
         if (cab101 != null)
             cab101.tell(new Cab.SignIn(0));
         else {
             System.out.println("Sorry that was a null cab reference");
         }
        TestProbe<Cab.CabStatus> cabProbe = testKit.createTestProbe();
        cab101.tell(new Cab.GetCabStatus(cabProbe.ref()));
        Cab.CabStatus resp1 = cabProbe.receiveMessage();
        assertEquals(resp1.majorState, "signed-in");
        assertEquals(resp1.minorState, "available");

        // #Step 2 : Customer 201 deducts amount from his/her wallet
        ActorRef<Wallet.Command> wallet_201 = Globals.wallets.get("201");
        wallet_201.tell(new Wallet.DeductBalance(-2000, walletProbe.ref()));
        assertEquals(walletProbe.receiveMessage().balance, -1);

        // #Step 3 : customer 201 requests a ride
        TestProbe<RideService.RideResponse> rideProbe = testKit.createTestProbe();
        Globals.rideService[1].tell(new RideService.RequestRide("201", 10, 1100, rideProbe.ref()));        
        RideService.RideResponse resp = rideProbe.receiveMessage();
        assertEquals(resp.rideId, -1);

        // #Step 4 : Get balance amount in Customer 201's wallet
        wallet_201.tell(new Wallet.GetBalance(walletProbe.ref()));
        assertEquals(walletProbe.receiveMessage().balance, 10000);
    }

    // #test
}

