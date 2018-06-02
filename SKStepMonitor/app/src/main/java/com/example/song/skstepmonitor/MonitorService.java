package com.example.song.skstepmonitor;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class MonitorService extends Service {

    private static final String LOGTAG = "ADC_Step_Monitor";

    private PowerManager.WakeLock wakeLock;
    private CountDownTimer timer;
    private StepMonitor accelMonitor;
    private static final long activeTime = 1000;
    private static final long checkingTime = 3000;

    private boolean moving = false;


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

                timer = new CountDownTimer(activeTime, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    public void onFinish() {
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
                timer.start();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public boolean isMoving() {
        accelMonitor = new StepMonitor(this);
        accelMonitor.onStart();

        timer = new CountDownTimer(activeTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                accelMonitor.onStop();

                moving = accelMonitor.isMoving();
            }
        };
        timer.start();
        return moving;
    } // isMoving()
    public boolean checking() {
        accelMonitor = new StepMonitor(this);
        accelMonitor.onStart();

        timer = new CountDownTimer(checkingTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                accelMonitor.onStop();

                moving = accelMonitor.isMoving();
            }
        };
        timer.start();
        return moving;
    } // checking()
}
