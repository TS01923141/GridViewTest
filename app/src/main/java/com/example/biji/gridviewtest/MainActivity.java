package com.example.biji.gridviewtest;

import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.GridView;
import android.widget.Toast;

import com.example.biji.gridviewtest.data.Images;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    private static final int RequestPermissionCode = 1;

    /**
     * 用於展示照片牆的GridView
     */
    private GridView mPhotoWall;

    /**
     * GridView的適配器
     */
    private PhotoWallAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (checkPermissions()) {
            mPhotoWall = findViewById(R.id.gridView_main);
            adapter = new PhotoWallAdapter(this, 0, Images.imageThumbUrls, mPhotoWall);
            mPhotoWall.setAdapter(adapter);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 退出程序時結束所有的下載任務
//        adapter.cancelAllTasks();
    }


    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!checkAllPermission()) {
                requestPermission();
            }else return true;
        }
        return false;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]
                {
                        INTERNET,
                        ACCESS_NETWORK_STATE,
                        //check more permissions if you want


                }, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length > 0) {

                    boolean InternetPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean AccessNetworkStatePermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;


                    if (InternetPermission && AccessNetworkStatePermission) {
                        Toast.makeText(MainActivity.this, "Permissions acquired", Toast.LENGTH_LONG).show();

                        mPhotoWall = findViewById(R.id.gridView_main);
                        adapter = new PhotoWallAdapter(this, 0, Images.imageThumbUrls, mPhotoWall);
                        mPhotoWall.setAdapter(adapter);
                    } else {
                        Toast.makeText(MainActivity.this, "One or more permissions denied", Toast.LENGTH_LONG).show();
                        checkPermissions();
                    }
                }

                break;
            default:
                break;
        }
    }

    public boolean checkAllPermission() {

        int FirstPermissionResult = ContextCompat.checkSelfPermission(getApplicationContext(), INTERNET);
        int SecondPermissionResult = ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_NETWORK_STATE);


        return FirstPermissionResult == PackageManager.PERMISSION_GRANTED &&
                SecondPermissionResult == PackageManager.PERMISSION_GRANTED;
    }
}
