package kr.ac.koreatech.swkang.msp16adcstepmonitor;


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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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
    String location;
    int locationCount = 0;
    double latitude, longitude;
    // 추가 수정 wakelock
    boolean locationCheck = false;
    CountDownTimer locationTimer;
    final int locationTime = 10000;
    int unknownCount = 0;


    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                getWifiInfo();
            }
        }
    };

    public void getWifiInfo() {
        Log.d("Location", "getWifiInfo");
        scanResultList = wifiManager.getScanResults();

        unregisterReceiver(wifiReceiver);

        int is_401 = 0;
        boolean is_401_bool = true;

        // 401 근거리의 와이파이를 등록
        // 일정 범위 레벨의 와이파이 신호가 잡히면 해당 is_401++
        // is_401_bool 조건을 추가하여
        // 와이파이 중심에서 거리는 같지만 방향이 다른 위치를 배제
        // 조건1 - SSID: cse2.4G  BSSID: 64:e5:99:db:05:c8
        // 조건2 - SSID: (null)   BSSID: 18:80:90:c6:7b:22
        // 조건3 - SSID: KUTAP_N  BSSID: 18:80:90:c6:7b:21
        // 조건4 - SSID: KUTAP    BSSID: 18:80:90:c6:7b:20
        // bool1 - SSID: 406      BSSID: 00:08:9f:52:b0:e4
        // bool2 - SSID: KUTAP_N  BSSID: 40:01:7a:de:11:31
        for(int i = 0; i < scanResultList.size(); i++) {
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
            } else if(result.BSSID.equals("00:08:9f:52:b0:e4")) { // bol-1
                if(result.level > -60) {
                    is_401_bool = false;
                    //tfManager.save("bol.1 ");
                }
            } else if(result.BSSID.equals("40:01:7a:de:11:31")) { // bol-2
                if(result.level > -45) {
                    is_401_bool = false;
                    //tfManager.save("bol.2");
                }
            }
        } // for
        Log.d("Location", "before 401");
        if(is_401 >= 3 && is_401_bool) {
            Log.d("Location", "if 401");
            location = "401강의실";
        } else {
            Log.d("Location", "else 401");
            getGPSInfo();
        }
        Log.d("Location", "after 401");
    }

    public void getGPSInfo() {
        Log.d("Location", "getGPSInfo");
        try {
            locationCount = 0;
            latitude = 0.0;
            longitude = 0.0;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        } catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d("Location", "locationChanged");
            latitude = (latitude * locationCount / (double)(locationCount + 1)) + (location.getLatitude() / (double)(locationCount + 1));
            longitude = (longitude * locationCount / (double)(locationCount + 1)) + (location.getLongitude() / (double)(locationCount + 1));
            locationCount++;
            if(locationCount == 2) {
                locationManager.removeUpdates(locationListener);
                Toast.makeText(getApplicationContext(), latitude + " / " + longitude , Toast.LENGTH_SHORT).show();
                tm2.save(latitude + " / " + longitude);
            }
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
                        //*****************************************************

                        /*
                        // When you finish your job, RELEASE the wakelock
                        wakeLock.release();
                        wakeLock = null;
                        */

                        if(locationCheck == false) {
                            wakeLock.release();
                            wakeLock = null;
                        } else {
                            IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                            registerReceiver(wifiReceiver, intentFilter);
                            Log.d("Loaction", "wifi scan");
                            wifiManager.startScan();
                            locationTimer = new CountDownTimer(locationTime, 10000) {
                                @Override
                                public void onTick(long l) {

                                }

                                @Override
                                public void onFinish() {
                                    if(wakeLock != null && wakeLock.isHeld()) {
                                        wakeLock.release();
                                        wakeLock = null;
                                        unregisterReceiver(wifiReceiver);
                                        locationManager.removeUpdates(locationListener);
                                    }
                                }
                            };
                            locationTimer.start();
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
            else if(stateList.size() >= 9 && isStayFive()) { // 먼저 요소가 9개 넘고, 그 요소들이 모두 STAY상태라면
                accStay += 50;
                stateList.add(STAY);
                state = STAY;
                //*****************************************************
                // 여기서부터 Location find(wakelock 고려) 수정 2018.06.17
                //                // 추가 수정 wifi, gps 독립 2018.06.18
                //*****************************************************
                locationCheck = true;
                unknownCount = 2;
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

        accelMonitor.onStop(); // 이 소스를 추가해야됌


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
    }

    // 내가 만든 함수
    public boolean isStayFive() {
        boolean stayFive = true;
        for(int i = stateList.size()-1; i >= stateList.size()-9; i-- ) { // 9개 검사.

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
