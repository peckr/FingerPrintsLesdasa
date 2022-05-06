package com.fgtit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.fgtit.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback {

    private ActivityMainBinding binding;

    private static final int PERMISSION_REQUEST_BLUETHOTHCONECT= 0;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // BEGIN_INCLUDE(onRequestPermissionsResult)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_BLUETHOTHCONECT) {
            // Request for bluetooth permission.
            if (grantResults.length > 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted. Start camera preview Activity.

              accionesMain();
            } else {
                // Permission request was denied.
                requestBluetoothPermission();
            }
        }
        // END_INCLUDE(onRequestPermissionsResult)
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
            accionesMain();
            // versiones con android 6.0 o superior
        } else{
            // para versiones anteriores a android 6.0

        if ( ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission is already available, start camera preview
            if ( ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                if ( ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                        == PackageManager.PERMISSION_GRANTED) {
               accionesMain();
                }
                else {
                    // Permission is missing and must be requested.
                    requestBluetoothPermission();
                }

            }
            else {
                // Permission is missing and must be requested.
                requestBluetoothPermission();
             }

                } else {
                    // Permission is missing and must be requested.
                    requestBluetoothPermission();
                }

        }


    }

    private void accionesMain() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
        Toast.makeText(getApplicationContext(),"Permiso Bluetooth concedido ", Toast.LENGTH_LONG);

    }
    private void requestBluetoothPermission() {
        // Permission has not been granted and must be requested.
        if ((ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.BLUETOOTH_CONNECT)) ) {

                    // Request the permission
            String[] permission_list= new String[3];
            permission_list[0]= Manifest.permission.BLUETOOTH_CONNECT;
            permission_list[1]= Manifest.permission.BLUETOOTH_SCAN;
            permission_list[2]= Manifest.permission.BLUETOOTH;


                    ActivityCompat.requestPermissions(MainActivity.this,
                            permission_list,
                            PERMISSION_REQUEST_BLUETHOTHCONECT);

        } else {
           // Snackbar.make(mLayout, R.string.camera_unavailable, Snackbar.LENGTH_SHORT).show();
            // Request the permission. The result will be received in onRequestPermissionResult().
            String[] permission_list= new String[3];
            permission_list[0]= Manifest.permission.BLUETOOTH_CONNECT;
            permission_list[1]= Manifest.permission.BLUETOOTH_SCAN;
            permission_list[2]= Manifest.permission.BLUETOOTH;

            ActivityCompat.requestPermissions(this,
                    permission_list, PERMISSION_REQUEST_BLUETHOTHCONECT);
        }
    }







}