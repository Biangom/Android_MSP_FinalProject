package kr.ac.koreatech.swkang.msp16adcstepmonitor;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private TextView mAccelX;
    private TextView mAccelY;
    private TextView mAccelZ;
    private TextView rmsText;
    private TextView movingText;
    private TextView logText;

    private TextView infoText;
    private double rms;

    final int MY_PERMISSIONS_REQUEST = 1;
    boolean isPermitted = false;

    // 파일 매니저 관련 설정
    TextFileManager mFileMgr;
    TextFileManager2 mTestMgr;

    // 주기적으로 함수를 실행하기 위한
    // TimerTask 관련 설정
    Timer timer = new Timer();
    TimerTask timerTask = null;

    private BroadcastReceiver MyStepReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("kr.ac.koreatech.msp.adcstepmonitor.rms")) {
                rms = intent.getDoubleExtra("rms", 0.0);
                rmsText.setText("rms: " + rms);
            } else if(intent.getAction().equals("kr.ac.koreatech.msp.adcstepmonitor.moving")) {
                boolean moving = intent.getBooleanExtra("moving", false);
                if(moving) {
                    movingText.setText("Moving");
                } else {
                    movingText.setText("NOT Moving");
                }
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //mAccelX = (TextView)findViewById(R.id.accelX);
        //mAccelY = (TextView)findViewById(R.id.accelY);
        //mAccelZ = (TextView)findViewById(R.id.accelZ);

        requestRuntimePermission();

        mFileMgr = new TextFileManager();
        mTestMgr = new TextFileManager2();

        rmsText = (TextView)findViewById(R.id.rms);
        movingText = (TextView)findViewById(R.id.moving);
        logText = (TextView)findViewById(R.id.logView);

        infoText = (TextView)findViewById(R.id.info);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("kr.ac.koreatech.msp.adcstepmonitor.rms");
        intentFilter.addAction("kr.ac.koreatech.msp.adcstepmonitor.moving");
        registerReceiver(MyStepReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // timertask를 시작한다.
        logText.setText(mFileMgr.load());
        infoText.setText(mTestMgr.load());
        startTimerTask();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimerTask();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(MyStepReceiver);
    }

    // Start/Stop 버튼을 눌렀을 때 호출되는 콜백 메소드
    // Activity monitoring을 수행하는 service 시작/종료
    public void onClick(View v) {
        if(v.getId() == R.id.startMonitor) {
            Intent intent = new Intent(this, ADCMonitorService.class);
            startService(intent);
        } else if(v.getId() == R.id.stopMonitor) {
            stopService(new Intent(this, ADCMonitorService.class));
        }
    }

    // 주기적으로 log데이터를 load하는 TimerTask함수 정의
    private void startTimerTask() {

        // TimerTask 생성한다
        timerTask = new TimerTask(){
            @Override
            public void run(){
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 파일에서 읽어들여와
                        // logText를 초기화한다.
                        try {
                            String data = mFileMgr.load();
                            logText.setText(data);
                            String data2 = mTestMgr.load();
                            infoText.setText(data2);

                        }catch (NullPointerException e){
                        }
                    }
                });
            }
        };

        // TimerTask를 Timer를 통해 실행시킨다
        // 4초 후에 타이머를 구동하고 5초마다 반복한다
        timer.schedule(timerTask, 2000, 3000);
        //*** Timer 클래스 메소드 이용법 참고 ***//
        // 	schedule(TimerTask task, long delay, long period)
        // http://developer.android.com/intl/ko/reference/java/util/Timer.html
        //***********************************//
    }

    private void stopTimerTask() {
        // 1. 모든 태스크를 중단한다
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void requestRuntimePermission() {
        //*******************************************************************
        // Runtime permission check
        //*******************************************************************
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                + ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                + ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.ACCESS_COARSE_LOCATION},
                        MY_PERMISSIONS_REQUEST);
            }
        } else {
            // Permission granted
            isPermitted = true;
        }
    } // requestRuntimePermission()

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch(requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED
                        && grantResults[2] == PackageManager.PERMISSION_GRANTED) {

                    // ACCESS_FINE_LOCATION, WRITE_EXTERNAL_STORAGE 권한을 얻음
                    isPermitted = true;
                } else {

                    // 권한을 얻지 못하였으므로 요청 작업을 수행할 수 없음
                    isPermitted = false;
                }
                return;
            }
        }
    } // onRequestPermissionsResult()
}
