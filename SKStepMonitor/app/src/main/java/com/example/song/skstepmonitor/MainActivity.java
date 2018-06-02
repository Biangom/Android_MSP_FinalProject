package com.example.song.skstepmonitor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    final int MY_PERMISSIONS_REQUEST = 1;
    boolean isPermitted = false;
    TextView text_date, text_movingtime, text_steps, text_topplace, text_log;
    Button button_start, button_stop;
    Date dt;
    SimpleDateFormat sdf_date;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRuntimePermission();

        text_date = (TextView)findViewById(R.id.text_date);
        text_movingtime = (TextView)findViewById(R.id.text_movingtime);
        text_topplace = (TextView)findViewById(R.id.text_topplace);
        text_log = (TextView)findViewById(R.id.text_log);
        button_start = (Button)findViewById(R.id.button_start);
        button_stop = (Button)findViewById(R.id.button_stop);

        dt = new Date();
        sdf_date = new SimpleDateFormat("yyyy-MM-dd");
    }

    @Override
    protected void onResume() {
        super.onResume();

        text_date.setText(sdf_date.format(dt).toString());
    }

    private void requestRuntimePermission() {
        //*******************************************************************
        // Runtime permission check
        //*******************************************************************
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
            + ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE},
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
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {

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

    // 사용자의 버튼 클릭 이벤트 리스너
    // Start/Stop 버튼 클릭시 StepMonitor 서비스 시작/종료
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.button_start: {
                Intent intent = new Intent(this, StepMonitor.class);
                startService(intent);
                break;
            }
            case R.id.button_stop: {
                Intent intent = new Intent(this, StepMonitor.class);
                stopService(intent);
                break;
            }
        }
    }

} // MainActivity
