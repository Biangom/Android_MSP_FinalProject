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
    private static final String LOGTAG = "ADC_Step_Monitor";
    AlarmManager am;
    PendingIntent pendingIntent;

    private boolean checkmoving = false;

    private PowerManager.WakeLock wakeLock;
    private CountDownTimer timer;

    private StepMonitor accelMonitor;
    private long period = 10000; // 기본 10초로 생각
    private static final long activeTime = 1000;
    private static final long periodForMoving = 30000; // 기본 30초
    private static final long periodIncrement = 5000; // 원래 5초였음
    private static final long periodMax = 30000;

    int accWalk = 0; // 한 텀에 Moving한 시간 수
    int accStay = 0; // 한 텀에 Stay한 시간 수
    int accStep = 0; // 한 텀에 Step 수
    int totalWalk = 0; // total Moving한 시간 수
    int totalStep = 0; // total Step한 시간 수
    boolean state = false; // 처음엔 stay로 생각
    static final boolean WALK = true;
    static final boolean STAY = false;
    ArrayList<Boolean> stateList = new ArrayList<Boolean>();

    TextFileManager tm = new TextFileManager();

    // 날짜관련
    long mNow;  // time을 저장하는 변수
    Date mDate; // time에 해당하는 날짜를 담을 객체
    // format을 지정해주는 mFormat 객체 생성
    SimpleDateFormat mFormat = new SimpleDateFormat("hh:mm");
    // 이전 날짜값, 현재 날짜값
    String preDate, nowDate;


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

                        // 화면에 움지임 여부를 표시할 수 있도록 브로드캐스트 전송
                        sendDataToActivity(moving);

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
        if(stateList.size() == 0)
            stateList.add(STAY);

        if(moving) {
            if(stateList.get(stateList.size()-1) == STAY) { // 현재가 Walk엿는데 이전에 Stay 엿으면 현재가 잘못될 수도 있으니 재검사
                boolean result = checking(); // 움직였는지 안움직였는지 다시 검사한다.
                if(result == WALK) { // 진짜 움직였으면 O X O X 상황
                    if(accStay >= 50) {
                        nowDate = getTime();
                        tm.save(preDate + "~" + nowDate + " " + accStay / 10 + "분 " + "정지");
                    }
                    accStay = 0;
                    preDate = getTime();
                    // 먼저 5분이상 넘겼는지 확인 후
                    // 이전에 accStay을 저장해야한다.
                    // 이전 Stay값 초기화(갑자기 바뀐거였으므로)
                }
                stateList.add(result);
            }
            else if(state == WALK) {
                accWalk += 5;
                stateList.add(WALK);
            }
            else{
                if(stateList.get(stateList.size()-1) == WALK) {
                    accWalk += 10;
                    stateList.add(WALK);
                }
            }
            //Log.d(LOGTAG, "MOVING!!");
            //period = periodForMoving; // 5초가 최소
            // 파일 입출력 쓰기

        }
        else { // 안움직였으면
            if(stateList.get(stateList.size()-1) == WALK) { // 현재가 Stay엿는데 이전에 Walk 엿으면 재검사
                boolean result = checking();
                if(result == STAY) { // 진짜 Stay이면 O X O X 상황
                    // 이전에 (1분이상 넘겼는지 확인 후) accWalk을 저장해야한다.
                    if(accWalk >= 10 ) {
                        nowDate = getTime();
                        tm.save(preDate + "~" + nowDate + " " + accWalk / 10 + "분 " + "이동");
                    }
                    accWalk = 0;
                    preDate = getTime();
                    // 이전 walk 초기화( 갑자기 바뀐거였으니까)
                }
                // unknon 이다.
                stateList.add(result);
            }
            else if(state == STAY) {
                accStay += 5;
                stateList.add(WALK);
            }
            else{ // 그게 아니라면 이전 stateList들의 상태 9개가 stay인지 검사 해야함.
                if(stateList.size() >= 9) { // 먼저 요소 검사 해주고
                    if (isStayFive()) { // 5분동안 머물었으면
                        accStay += 50;
                        stateList.add(WALK);
                    }
                }

            }
            //Log.d(LOGTAG, "NOT MOVING!!");
            // 파일 입출력 쓰기

            // 값 증가 안할거임
//            period = period + periodIncrement; // 값을 증가 5초씩 증가
//            // 최대 주기를 설정한다. 이 이상을 넘지 말라고
//            if(period >= periodMax) {
//                period = periodMax;
//            }
        }
        Log.d(LOGTAG, "Next alarm: " + period);

        // 다음 alarm 등록
        Intent in = new Intent("kr.ac.koreatech.msp.adcalarm");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, in, 0);
        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period - activeTime, pendingIntent);
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

        Log.d(LOGTAG, "onCreate");

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

        Log.d(LOGTAG, "onStartCommand");
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

    public boolean checking() {
        accelMonitor = new StepMonitor(this);
        accelMonitor.onStart();

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
                checkmoving = accelMonitor.isMoving();
            }
        };
        timer.start(); // 타이머를 가동한다.
        return checkmoving;
    }
}
