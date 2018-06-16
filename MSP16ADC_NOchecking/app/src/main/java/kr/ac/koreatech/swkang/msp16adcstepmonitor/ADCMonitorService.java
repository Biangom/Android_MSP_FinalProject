package kr.ac.koreatech.swkang.msp16adcstepmonitor;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    int state = STAY; // 처음엔 stay로 생각, 현재 상태를 담는다(WALK or STAY)

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


                        accelMonitor = null;
                        // When you finish your job, RELEASE the wakelock
                        wakeLock.release();
                        wakeLock = null;
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

        boolean isCheck = false;

        if(stateList.size() == 0)
            stateList.add(STAY);

        // 움직였다면 현재 상태가 WALK상태
        if(moving) {
            // 현재가 Walk이고 이전에 Stay 엿으면 현재가 잘못될 수도 있으니 재검사

            /*
            if(stateList.get(stateList.size()-1) == STAY) {
                // 재검사 빼버렸음
            }
            // 현재 WALK이고, (1분 이상 걸은 상태)WALK상태이라면
            else
                */
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
            else {
                stateList.add(WALK);
            }
        }
        else { // 안움직였으면
            /*
            if(stateList.get(stateList.size()-1) == WALK) { // 현재가 Stay엿는데 이전에 Walk 엿으면 재검사
                // 재검사 빼버렸음
            }
            // 현재 STAY이고, 여태까지 STAY상태였으면
            else
             */
            if(state == STAY) {
                accStay += 5; //단순하게 30초 STAY 추가
                stateList.add(STAY);
            }
            // 그게 아니라면 이전 stateList들의 상태 9개 이상(5분)이 있는지 부터 인지 검사 해야함.
            else if(stateList.size() >= 9 && isStayFive()) { // 먼저 요소가 9개 넘고, 그 요소들이 모두 STAY상태라면
                accStay += 50;
                stateList.add(STAY);
                state = STAY;
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
                    SystemClock.elapsedRealtime() + period - activeTime , pendingIntent);
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
/*
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
    }*/
}
