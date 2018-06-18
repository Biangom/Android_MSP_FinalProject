package com.example.ngn01.sub;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ADCMonitorService extends Service {
    private static final String LOGTAG = "ADC_Monitor_Service";
    AlarmManager am;
    PendingIntent pendingIntent;

    private boolean checkmoving = false;

    private PowerManager.WakeLock wakeLock;
    private CountDownTimer timer;

    private StepMonitor accelMonitor;

    private long period = 10000; // 기본 10초로 생각, 이 부분을 30초로 바꾸어야함.
    private static final long activeTime = 1000;
    private static final long periodForMoving = 30000; // 기본 30초
    private static final long periodIncrement = 5000; // 원래 5초였음
    private static final long periodMax = 30000;

    int accWalk = 0; // 한 텀에 Moving한 시간 수
    int accStay = 0; // 한 텀에 Stay한 시간 수

    int accStep = 0; // 한 텀에 Step 수
    int totalWalk = 0; // total Moving한 시간 수
    int totalStep = 0; // total Step한 시간 수

    static final int STAY = 0; // 현재가 가만히 있는 상태
    static final int WALK = 1; // 현재가 걷고 있는 상태
    static final int UN = 2; // 1분이상 걷지도않고 5분이상 쉬지도 않았을때 상태

    // 1분이상 걸었으면 WALK
    // 5분이상 쉬었으면 STAY
    // 둘 중 아무것도 아니면 UN
    int state = UN; // 처음엔 stay로 생각, 현재 상태를 담는다(WALK or STAY)

    ArrayList<Integer> stateList = new ArrayList<Integer>();
    Boolean[] stateListTwo = new Boolean[10];
    int listCount = 0;

    TextFileManager tm = new TextFileManager();
    TextFileManager2 tm2 = new TextFileManager2();

    // 날짜관련
    long mNow;  // time을 저장하는 변수
    Date mDate; // time에 해당하는 날짜를 담을 객체
    // format을 지정해주는 mFormat 객체 생성
    SimpleDateFormat mFormat = new SimpleDateFormat("hh:mm");
    // 이전 날짜값, 현재 날짜값
    String preDate, nowDate;


    //************************************************************************
    // 여기부터 Location find 수정 2018.06.16
    // 추가 수정 wakelock 고려 2018.06.17
    // 추가 수정 Wifi AP, GPS 좌표 2018.06.18
    //************************************************************************
    // Location find 추가
    WifiManager wifiManager;
    LocationManager locationManager;
    List<ScanResult> scanResultList;
    String msp_location = "";
    int locationCount = 0;
    double latitude, longitude;
    // 추가 수정 wakelock
    boolean locationCheck = false;
    CountDownTimer locationTimer;
    CountDownTimer locationTimer_gps;
    final int locationTime_wifi = 4000;
    final int locationTime_gps = 4000;
    // 추가 수정 Wifi AP, GPS 좌표
    final double ground_lat = 36.762581;
    final double ground_lon = 127.284527;
    final double square_lat = 36.764215;
    final double square_lon = 127.282173;
    int ticOnce = 0;
    int unknownCount = 0;
    final int locationTime = 8000;
    final String LTAG = "LocationFind";
    final int stayCondition = 3;


    // Wifi Scan Result Broadcast 를 수신하는 Receiver
    // 해당 Broadcast 수신하면 getWifiInfo() 호출
    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                getWifiInfo();
            }
        }
    };

    // Wifi AP로 현재위치를 판단하는 함수
    // ScanResult 와 미리 저장한 Wifi AP Fingerprint 를 비교
    public void getWifiInfo() {
        tm.save("getWifiInfo\n");
        Log.d(LTAG, "getWifiInfo");
        scanResultList = wifiManager.getScanResults();

        unregisterReceiver(wifiReceiver);

        int is_401 = 0;
        int is_dasan = 0;

        // 401 근거리의 와이파이를 등록
        // 일정 범위 레벨의 와이파이 신호가 잡히면 관련 is_**++
        // is_** 값의 크기가 일정 이상이면 해당 위치로 판단
        // 와이파이 중심에서 거리는 같지만 방향이 다른 위치를 배제
        // 401 강의실 Wifi AP Fingerprint
        // 조건1 - SSID: ces5G    BSSID: 64:25:99:db:05:cc  level: 51 +-8
        // 조건2 - SSID: KUTAP    BSSID: 18:80:90:c6:7b:20  level: 56 +-12
        // 조건3 - SSID: KUTAP    BSSID: 18:80:90:c6:7b:2f  level: 62 +-22
        // 조건4 - SSID: (null)   BSSID: 40:01:7a:de:11:32  level: 68 +-8
        // 조건5 - SSID: KUTAP_N  BSSID: 40:01:7a:de:11:31  level: 67 +-8
        // 다산정보관 Wifi AP Fingerprint
        // 조건1 - BSSID: 20:3a:07:9e:a6:ce  level: 54 +-8
        // 조건2 - BSSID: 20:3a:07:9e:a6:05  level: 56 +-8
        // 조건3 - BSSID: 20:3a:07:9e:a6:ca  level: 55 +-8
        // 조건4 - BSSID: 20:3a:07:49:50:ee  level: 60 +-8
        // 조건5 - BSSID: 20:3a:07:9e:a6:cf  level: 55 +-8
        for(int i = 0; i < Math.min(scanResultList.size(), 15); i++) {
            ScanResult result = scanResultList.get(i);

            if(result.BSSID.equals("64:e5:99:db:05:c8")) { // 401-1
                if(result.level > -60 && result.level < -48) {
                    is_401++;
                    //tfManager.save("401.1 ");
                }
            } else if(result.BSSID.equals("18:80:90:c6:7b:22")) { // 401-2
                if(result.level > -60 && result.level < -44) {
                    is_401++;
                    //tfManager.save("401.2 ");
                }
            } else if(result.BSSID.equals("18:80:90:c6:7b:21")) { // 401-3
                if(result.level > -60 && result.level < -44) {
                    is_401++;
                    //tfManager.save("401.3 ");
                }
            } else if(result.BSSID.equals("18:80:90:c6:7b:20")) { // 401-4
                if(result.level > -60 && result.level < -44) {
                    is_401++;
                    //tfManager.save("401.4 ");
                }
            }
            /******
            if(result.BSSID.equals("64:25:99:db:05:cc")) { // 401.1
                if(result.level > -59 && result.level < -43) {
                    is_401++;
                    Log.d(LTAG, "is_401++ con.1");
                }
            } else if(result.BSSID.equals("18:80:90:c6:7b:20")) { // 401.2
                if(result.level > -68 && result.level < -44) {
                    is_401++;
                    Log.d(LTAG, "is_401++ con.2");
                }
            } else if(result.BSSID.equals("18:80:90:c6:7b:2f")) { // 401.3
                if(result.level > -84 && result.level < -40) {
                    is_401++;
                    Log.d(LTAG, "is_401++ con.3");
                }
            } else if(result.BSSID.equals("40:01:7a:de:11:32")) { // 401.4
                if(result.level > -76 && result.level < -60) {
                    is_401++;
                    Log.d(LTAG, "is_401++ con.4");
                }
            } else if(result.BSSID.equals("40:01:7a:de:11:31")) { // 401.5
                if(result.level > -75 && result.level < -59) {
                    is_401++;
                    Log.d(LTAG, "is_401++ con.5");
                }
            }
            *******/
             else if(result.BSSID.equals("20:3a:07:9e:a6:ce")) { // 다산.1
                if(result.level > -62 && result.level < -46) {
                    is_dasan++;
                    Log.d(LTAG, "is_dasan++ con.1");
                }
            } else if(result.BSSID.equals("20:3a:07:9e:a6:05")) {
                if(result.level > -64 && result.level < -48) { // 다산.2
                    is_dasan++;
                    Log.d(LTAG, "is_dasan++ con.2");
                }
            } else if(result.BSSID.equals("20:3a:07:9e:a6:ca")) {
                if (result.level > -63 && result.level < -47) { // 다산.3
                    is_dasan++;
                    Log.d(LTAG, "is_dasan++ con.3");
                }
            } else if(result.BSSID.equals("20:3a:07:49:50:ee")) {
                if (result.level > -68 && result.level < -52) { // 다산.4
                    is_dasan++;
                    Log.d(LTAG, "is_dasan++ con.4");
                }
            } else if(result.BSSID.equals("20:3a:07:9e:a6:cf")) {
                if (result.level > -63 && result.level < -57) { // 다산.5
                    is_dasan++;
                    Log.d(LTAG, "is_dasan++ con.5");
                }
            }
        } // for
        // is_** 값이 3 이상이면 해당 위치로 판단
        // 전역변수 msp_location 에 판단된 위치 저장
        Log.d(LTAG, "before Wifi decision");
        if(is_401 >= 3) {
            Log.d(LTAG, "if 401");
            tm.save("if 401\n");
            msp_location = "401강의실";
        } else if(is_dasan >= 3) {
            Log.d(LTAG, "if dasan");
            tm.save("if dasan\n");
            msp_location = "다산정보관";
        } else {
            Log.d(LTAG, "wifi unknown");
            tm.save("else 401\n");
            msp_location = "Unknown";
        }
        Log.d(LTAG, "after Wifi decision");
    }

    // GPS update 등록 요청함수
    public void getGPSInfo() {
        tm.save("getGPSInfo\n");
        Log.d(LTAG, "getGPSInfo");
        try {
            locationCount = 0;
            latitude = 0.0;
            longitude = 0.0;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        } catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    // GPS 센서 값을 수신하는 listener
    // 일정 횟수 update 될 때까지 얻은 위도, 경도 값들을 평균함
    // 평균값과 미리 지정된 위치와의 거리를 비교하여 현재 위치 판단
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(LTAG, "locationChanged");
            // 지금까지 얻었던 위도, 경도값과 현재 얻은 값들을 합하여 평균
            latitude = (latitude * locationCount / (double)(locationCount + 1)) + (location.getLatitude() / (double)(locationCount + 1));
            longitude = (longitude * locationCount / (double)(locationCount + 1)) + (location.getLongitude() / (double)(locationCount + 1));
            // 값을 4회까지 얻고 location update 종료
            locationCount++;
            if(locationCount == 4) {
                locationManager.removeUpdates(locationListener);
                tm.save(latitude + " / " + longitude);
            }
            // 미리 지정된 위치와 센서를 통해 얻은 위도, 경도 값 비교
            float[] results_gr = new float[3];
            float[] results_sq = new float[3];
            Location.distanceBetween(ground_lat, ground_lon, latitude, longitude, results_gr);
            Location.distanceBetween(square_lat, square_lon, latitude, longitude, results_sq);
            if(results_gr[0] < 80.0) {
                msp_location = "운동장";
            } else if(results_sq[0] < 50.0) {
                msp_location = "잔디광장";
            } else {
                msp_location = "Unknown";
            }
            tm.save(msp_location + "\n");
            Log.d(LTAG, "now location: " + msp_location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };


    //************************************************************************
    // 여기까지 Location find 수정 2018.06.16
    //************************************************************************

    final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    // Alarm 시간이 되었을 때 안드로이드 시스템이 전송해주는 broadcast를 받을 receiver 정의
    // 움직임 여부에 따라 기준에 맞게 다음 alarm이 발생하도록 설정한다.
    private BroadcastReceiver AlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("kr.ac.koreatech.msp.adcalarm")) {
                Log.d(LOGTAG, "Alarm fired!!!!");
                //-----------------
                // Alarm receiver에서는 장시간에 걸친 연산을 수행하지 않도록 한다
                // Alarm을 발생할 때 안드로이드 시스템에서 wakelock을 잡기 때문에 CPU를 사용할 수 있지만
                // 그 시간은 제한적이기 때문에 애플리케이션에서 필요하면 wakelock을 잡아서 연산을 수행해야 함
                //-----------------

                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AdaptiveDutyCyclingStepMonitor_Wakelock");
                // ACQUIRE a wakelock here to collect and process accelerometer data
                wakeLock.acquire();

                accelMonitor = new StepMonitor(context);
                accelMonitor.onStart();

                // 1초뒤에 onFinish가 있으므로, 1초뒤에 onStop이 호출된다
                // 이 뜻은 1초동안 센서 값을 받겠다라는 의미

                // activeTime 시간 내에서 1초마다 onTick과 onFinish가 콜된다.
                // 카운트 다운 타이머마다 onTick이 호출이되고, 타이머가 종ㄹ가 되면 onFinish
                // 둘다 똑같은 시점에 호출이 된다.
                timer = new CountDownTimer(activeTime, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }
                    @Override
                    public void onFinish() {
                        // 이 함수는 onReceive가 끝나고 1초 뒤에 실행한다.

                        Log.d(LOGTAG, "1-second accel data collected!!");
                        // stop the accel data update
                        accelMonitor.onStop();

                        // 움직임 여부에 따라 다음 alarm 설정
                        boolean moving = accelMonitor.isMoving();


                        setNextAlarm(moving);
                        // stay 상태라면 ~~추가하기

                        // 화면에 움지임 여부를 표시할 수 있도록 브로드캐스트 전송
                        sendDataToActivity(moving);

                        //*****************************************************
                        // 여기서부터 Location find(wakelock 고려) 수정 2018.06.17
                        // 추가수정 Wifi, GPS 독립구현 2018.06.18
                        //*****************************************************

                        /*
                        // When you finish your job, RELEASE the wakelock
                        wakeLock.release();
                        wakeLock = null;
                        */

                        if(locationCheck == true || (msp_location.equals("Unknown") && unknownCount >= 0)) {
                            IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                            registerReceiver(wifiReceiver, intentFilter);
                            Log.d(LTAG, "wifi scan");
                            wifiManager.startScan();
                            locationTimer = new CountDownTimer(locationTime_wifi, locationTime_wifi) {
                                @Override
                                public void onTick(long l) {

                                }

                                @Override
                                public void onFinish() {
                                    Log.d(LTAG, "onFinish_WifiRelease");
                                    try {
                                        unregisterReceiver(wifiReceiver);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    locationTimer_gps = new CountDownTimer(locationTime_gps, locationTime_gps) {
                                        @Override
                                        public void onTick(long l) {

                                        }

                                        @Override
                                        public void onFinish() {
                                            Log.d(LTAG, "onFinish_gpsRelease");
                                            locationManager.removeUpdates(locationListener);
                                            if(wakeLock != null && wakeLock.isHeld()) {
                                                wakeLock.release();
                                                wakeLock = null;
                                            }
                                        }
                                    };
                                    getGPSInfo();
                                    unknownCount--;
                                    locationTimer_gps.start();
                                }
                            };
                            locationTimer.start();
                        } else {
                            wakeLock.release();
                            wakeLock = null;
                        }
                        //*****************************************************
                        // 여기까지 Location find(wakelock 고려) 수정 2018.06.17
                        //*****************************************************
                    }
                };
                timer.start(); // 타이머를 가동한다.
            }
        }
    };

    private void setNextAlarm(boolean moving) {

        // 움직임이면 5초 period로 등록
        // 움직임이 아니면 5초 증가, max 30초로 제한
        // 움직이면 5초가 최소


        //*****************************************************
        // 여기서부터 Location find(wakelock 고려) 수정 2018.06.17
        //*****************************************************
        locationCheck = false;
        //*****************************************************
        // 여기까지 Location find(wakelock 고려) 수정 2018.06.17
        //*****************************************************


        boolean isCheck = false;

        if(stateList.size() == 0)
            stateList.add(STAY);

        // 움직였다면 현재 상태가 WALK상태
        if(moving) {
            // 현재가 Walk이고 이전에 Stay 엿으면 현재가 잘못될 수도 있으니 재검사
            if(stateList.get(stateList.size()-1) == STAY) {
                int result = checking(); // 움직였는지 안움직였는지 다시 검사한다.
                isCheck = true; // 바뀌었으니 true로 바꾼다.
                // 진짜 움직였으면 X X X O or . . X O 상황일 수도 있다.
                if(result == WALK) { // S W인 상황
                    stateList.add(WALK); // 먼저 상태 저장
                    // 그게 5분을 넘겼을 때만 기록한다.
                    // (X X . . (5분이상) . X O인 상태일때
                    if(accStay >= 50) {
                        nowDate = getTime();
                        //..
                        // 이전에 accStay을 저장해야한다.
                        tm.save(preDate + "~" + nowDate + " " + accStay / 10 + "분 " + "정지\n");
                        state = UN;
                    }
                    // (. (5분미만) . X O 인 상태)
                    else
                        state = UN;
                    // 이전 Stay값 초기화(이미 X X X O or . . X O)이므로
                    accStay = 0;
                    preDate = getTime();
                    // 먼저 5분이상 넘겼는지 확인 후
                }
                // 다시 검사했는데 진짜 안움직였으면(바뀌엇음) . . X X 인 상황
                else if(result == STAY) {
                    //stateList.add(result);
                    // 현재 STAY이고, 여태까지 STAY상태였으면
                    if(state == STAY) {
                        accStay += 5; //단순하게 30초 STAY 추가
                        stateList.add(STAY);
                    }
                    // 그게 아니라면 이전 stateList들의 상태 9개 이상(5분)이 있는지 부터 인지 검사 해야함.
                    else if(stateList.size() >= 9) { // 먼저 요소 검사 해주고
                        if (isStayFive()) { // 5분동안 머물었으면
                            accStay += 50;
                            stateList.add(STAY);
                            state = STAY;
                        }
                    }
                }
            }
            // 현재 WALK이고, (1분 이상 걸은 상태)WALK상태이라면
            else if(state == WALK) {
                accWalk += 5; // 30초 추가후
                stateList.add(WALK); // WALK 저장
            }
            // 위 블록을 빠져나오려면 state가 STAY거나 UNKNOWN일 것이다.
            // 그때에 대한 처리
            // 현재 WALK이고, 이전(30초전)에 WALK이면
            else if(stateList.get(stateList.size()-1) == WALK) {
                accWalk += 10; // 총 1분 추가
                stateList.add(WALK); // WALK 저장
                state = WALK; // 그리고 state를 WALK상태로 바꾼다. 1분이상 걸었으므로
            }
            else {
                stateList.add(WALK);
            }
        }
        else { // 안움직였으면
            if(stateList.get(stateList.size()-1) == WALK) { // 현재가 Stay엿는데 이전에 Walk 엿으면 재검사
                int result = checking();
                isCheck = true;
                if(result == STAY) { // 진짜 Stay이면 (. . X O X) or ( . . O O X) 인상황
                    stateList.add(STAY);
                    // 이전에 (1분이상 넘겼는지 확인 후) accWalk을 저장해야한다.
                    // ( . .O O X) 인 상황
                    if(accWalk >= 10 ) {
                        nowDate = getTime();
                        //....이 사이에 장소 checking 해야함
                        tm.save(preDate + "~" + nowDate + " " + accWalk / 10 + "분 " + "이동\n");
                        state = UN;
                    }
                    // . X O X 인 상태, 이전 값이 1분 미만의 WALK이라면
                    else
                        state = UN;

                    accWalk = 0;
                    preDate = getTime();
                    // 이전 walk 초기화( 갑자기 바뀐거였으니까)
                }
                // 다시 검사했는데 WALK 상태(바뀌엇음)라면
                else if(result == WALK) {
                    // 현재 WALK이고, (1분 이상 걸은 상태)WALK상태이라면
                    if(state == WALK) {
                        accWalk += 5; // 30초 추가후
                        stateList.add(WALK); // WALK 저장
                    }
                    // 위 블록을 빠져나오려면 state가 STAY거나 UNKNOWN일 것이다.
                    // 그때에 대한 처리
                    // 현재 WALK이고, 이전(30초전)에 WALK이면
                    else if(stateList.get(stateList.size()-1) == WALK) {
                        accWalk += 10; // 총 1분 추가
                        stateList.add(WALK); // WALK 저장
                        state = WALK; // 그리고 state를 WALK상태로 바꾼다. 1분이상 걸었으므로
                    }
                }
            }
            // 현재 STAY이고, 여태까지 STAY상태였으면
            else if(state == STAY) {
                accStay += 5; //단순하게 30초 STAY 추가
                stateList.add(STAY);
            }
            // 그게 아니라면 이전 stateList들의 상태 9개 이상(5분)이 있는지 부터 인지 검사 해야함.
            else if(stateList.size() >= stayCondition && isStayFive()) { // 먼저 요소가 9개 넘고, 그 요소들이 모두 STAY상태라면
                accStay += 50;
                stateList.add(STAY);
                state = STAY;
                //*****************************************************
                // 여기서부터 Location find(wakelock 고려) 수정 2018.06.17
                // 추가 수정 wifi, gps 독립 2018.06.18
                //*****************************************************
                locationCheck = true;
                unknownCount = 2;
                Log.d(LTAG, "State STAY in");
                //*****************************************************
                // 여기까지 Location find(wakelock 고려) 수정 2018.06.17
                //*****************************************************
            }
            else {
                stateList.add(STAY);
            }
        }


        Log.d(LOGTAG, "Finaly Data: " + stateList.get( stateList.size()-1 ).toString());
        Log.d(LOGTAG, "Next alarm: " + period);

        // 결과값 확인
        tm2.delete();
        for(int i = 0; i < stateList.size() ; i++) {
            if(stateList.get(i) == WALK)
                tm2.save("W");
            else
                tm2.save("S");
        }

        // 다음 alarm 등록
        Intent in = new Intent("kr.ac.koreatech.msp.adcalarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);

        if(isCheck == false ) {
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + period - activeTime, pendingIntent);
        }
        else { // true이면 checking 에 진입했으므로 activetime을 2번빼준다.
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + period - activeTime - activeTime, pendingIntent);
        }
    }

    private void sendDataToActivity(boolean moving) {
        // 화면에 정보 표시를 위해 activity의 broadcast receiver가 받을 수 있도록 broadcast 전송
        Intent intent = new Intent("kr.ac.koreatech.msp.adcstepmonitor.moving");
        intent.putExtra("moving", moving);
        // broadcast 전송
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        //Log.d(LOGTAG, "onCreate");

        // Alarm 발생 시 전송되는 broadcast를 수신할 receiver 등록
        IntentFilter intentFilter = new IntentFilter("kr.ac.koreatech.msp.adcalarm");
        registerReceiver(AlarmReceiver, intentFilter);

        // AlarmManager 객체 얻기
        am = (AlarmManager)getSystemService(ALARM_SERVICE);

        // 초기 날짜 설정
        preDate = getTime();

        //*****************************************
        wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        locationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);
        //*****************************************

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intent: startService() 호출 시 넘기는 intent 객체
        // flags: service start 요청에 대한 부가 정보. 0, START_FLAG_REDELIVERY, START_FLAG_RETRY
        // startId: start 요청을 나타내는 unique integer id

        //Log.d(LOGTAG, "onStartCommand");
        Toast.makeText(this, "Activity Monitor 시작", Toast.LENGTH_SHORT).show();

        // Alarm이 발생할 시간이 되었을 때, 안드로이드 시스템에 전송을 요청할 broadcast를 지정
        Intent in = new Intent("kr.ac.koreatech.msp.adcalarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);

        // Alarm이 발생할 시간 및 alarm 발생시 이용할 pending intent 설정
        // 설정한 시간 (period=10000 (10초)) 후 alarm 발생
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period, pendingIntent);

        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        Toast.makeText(this, "Activity Monitor 중지", Toast.LENGTH_SHORT).show();

        try{
            accelMonitor.onStop(); // 이 소스를 추가해야됌
        } catch(NullPointerException e) {
            e.printStackTrace();
        }


        try {
            // Alarm 발생 시 전송되는 broadcast 수신 receiver를 해제
            unregisterReceiver(AlarmReceiver);
        } catch(IllegalArgumentException ex) {
            ex.printStackTrace();
        }
        // AlarmManager에 등록한 alarm 취소
        am.cancel(pendingIntent);

        // release all the resources you use
        if(timer != null)
            timer.cancel();
        if(wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        if(locationTimer != null) {
            locationTimer.cancel();
        }
        if(locationTimer_gps != null) {
            locationTimer_gps.cancel();
        }
        tm.save("종료\n");
    }

    // 내가 만든 함수
    public boolean isStayFive() {
        boolean stayFive = true;
        for(int i = stateList.size()-1; i >= stateList.size()-stayCondition; i-- ) { // 9개 검사.

            if(stateList.get(i) == WALK)
                return false; // 하나라도 WALK면 5분이상 stay한게 아니므로 false 반환
        }
        // for문을 무사히 통과했으면 5분동안 머무른 것이므로 true 반환
        return stayFive;
    }

    // 시간 반환하는 함수
    private String getTime(){
        // mNow에 시간을 생성한 뒤
        // mDate에 그 시간에 해당하는 날짜를 생성한다.
        mNow = System.currentTimeMillis();
        mDate = new Date(mNow);

        // 지정한 포맷으로 날짜데이터를 string으로 변환하여 반환
        return mFormat.format(mDate);
    }

    public int checking() {
        accelMonitor = new StepMonitor(this);
        accelMonitor.onStart();


        timer = new CountDownTimer(activeTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }
            @Override
            public void onFinish() {
                // 이 함수는 onReceive가 끝나고 1초 뒤에 실행한다.

                //Log.d(LOGTAG, "1-second accel data collected!!");
                // stop the accel data update
                accelMonitor.onStop();

                // 움직임 여부에 따라 다음 alarm 설정
                checkmoving = accelMonitor.isMoving();
            }
        };
        timer.start(); // 타이머를 가동한다.


        if(checkmoving) return WALK;
        else return STAY;
    }
}
