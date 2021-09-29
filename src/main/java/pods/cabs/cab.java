package pods.cabs;

// cab class, used to implement cacheTable 
public class cab {
    public String cabId;                        // cab Id
    public int initialPos = -1;                 // Cab position
    public String minorState = null;            // Can be either available/committed/giving-ride
    public String majorState = "signed-out";    // Can be either signed-in/signed-out
    public int rideID = -1;                     // If giving-ride, then rideId of current ride
    public int sourceLoc = -1;                  // If giving-ride, then source location of current ride
    public int destinationLoc = -1;             // If giving-ride, then destination location of current ride

    cab(String cabId, int initialPos) {
        this.cabId = cabId;
        this.initialPos = initialPos;
    }

    cab(String cabId) {
        this.cabId = cabId;
    }

    int getInitialPos() {
        return this.initialPos;
    }

    String getMajorState() {
        return this.majorState;
    }

    String getMinorState() {
        return this.minorState;
    }

    String getCabId() {
        return this.cabId;
    }

}
