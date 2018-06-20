package kr.ac.koreatech.swkang.msp16adcstepmonitor;

/**
 * Created by kss78 on 2018-06-20.
 */

public class placeInfo {
    public placeInfo(String name, int accStay) {
        this.name = name;
        this.accStay = accStay;
    }

    public placeInfo() {
        this.name = null;
        this.accStay = 0;
    }

    String name;
    int accStay;
}
