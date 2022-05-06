package com.fgtit.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fgtit.BluetoothReaderService;
import com.fgtit.DeviceListActivity;
import com.fgtit.R;
import com.fgtit.data.Configuraciones;
import com.fgtit.databinding.FragmentHomeBinding;
import com.fgtit.fpcore.FPMatch;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.regex.Pattern;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;


    // Debugging
    private static final String TAG = "BluetoothReader";
    private static final boolean D = true;
    private static final int REQUEST_PERMISSION_ACCESS_LOCATION = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 2;

    // Layout Views
    private TextView mTitle;
    private ListView mConversationView;
    private ImageView fingerprintImage;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothReaderService mReaderService = null;

    public byte mRefData[] = new byte[512];
    public int mRefSize = 0;
    public byte mMatData[] = new byte[512];
    public int mMatSize = 0;

    public byte mCardSn[] = new byte[4];
    public byte mCardData[] = new byte[4096];
    public int mCardSize = 0;
    public byte mBat[] = new byte[2];
    public byte mVersion[] = new byte[8];
    public long mTimeStart, mTimeEnd;
    private androidx.appcompat.widget.Toolbar mToolbar;
    public int fingerNumber = 0;
    private String sDirectory = "";
    private int mRefCount = 0;
    public byte mRefList[][] = new byte[2048][512];
    Button botonpro;
    String iddispositivo;
    TextView txtResultado;
    String resultado;
    JornadaTask jornadaTask;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        //final TextView textView = root.findViewById(R.id.text_home);



        //toolbar
        mToolbar = root.findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.dispositivo);
        mToolbar.setSubtitle("No conectado");
        mToolbar.inflateMenu(R.menu.option_menu);
        botonpro = root.findViewById(R.id.buttonpro);
        txtResultado = root.findViewById(R.id.textViewResultado);

        botonpro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AddStatusList("Buscar ...");

               // AddStatusList("Device SN  ...");

                mReaderService.SearchInModule();

            }
        });


        mToolbar.setOnMenuItemClickListener(onMenuItemClick);
        requestPermission();
        checkPermission();
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(getContext(), "Bluetooth is not available", Toast.LENGTH_LONG).show();
            getActivity().finish();
            //return;
        }

        if (FPMatch.getInstance().InitMatch(1, "http://www.hfcctv.com/") != 1)
            Toast.makeText(getContext(), "Init Matcher Fail!", Toast.LENGTH_LONG).show();

        CreateDirectory();
        ReadDataFromFile();


        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // binding = null;
    }


    public void CreateDirectory() {
        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED)) {
            sDirectory = Environment.getExternalStorageDirectory() + "/FingerprintReader";
            File destDir = new File(sDirectory);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
        }
    }

    public void ReadDataFromFile() {
        File f = new File(sDirectory + "/fp.dat");
        if (f.exists()) {
        }
        try {
            RandomAccessFile randomFile = new RandomAccessFile(sDirectory + "/fp.dat", "rw");
            long fileLength = randomFile.length();
            mRefCount = (int) (fileLength / 512);
            if (mRefCount > 2000)
                mRefCount = 2000;
            for (int i = 0; i < mRefCount; i++) {
                randomFile.read(mRefList[i]);
            }
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void WriteDataToFile() {
        File f = new File(sDirectory + "/fp.dat");
        if (f.exists()) {
            f.delete();
        }
        new File(sDirectory + "/fp.dat");
        try {
            RandomAccessFile randomFile = new RandomAccessFile(sDirectory + "/fp.dat", "rw");
            for (int i = 0; i < mRefCount; i++) {
                randomFile.write(mRefList[i]);
            }
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void DeleteDataFile() {
        File f = new File(sDirectory + "/fp.dat");
        if (f.exists()) {
            f.delete();
        }
        mRefCount = 0;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            //判断是否有权限
            if (ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //请求权限
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_PERMISSION_ACCESS_LOCATION);
                //向用户解释，为什么要申请该权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                        Manifest.permission.READ_CONTACTS)) {
                    Toast.makeText(getContext(), "Should Show Request Permission Rationale", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // TODO Auto-generated method stub
        if (requestCode == REQUEST_PERMISSION_ACCESS_LOCATION) {
            if (permissions[0].equals(Manifest.permission.ACCESS_COARSE_LOCATION)
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户同意使用该权限
            } else {
                // 用户不同意，向用户展示该权限作用
                if (!ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    //showTipDialog("用来扫描附件蓝牙设备的权限，请手动开启！");
                    return;
                }
            }
        }
    }

    private void checkPermission() {
        //检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //用户已经拒绝过一次，再次弹出权限申请对话框需要给用户一个解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission
                    .WRITE_EXTERNAL_STORAGE)) {
            }
            //申请权限
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);

        } else {
        }
    }

    private androidx.appcompat.widget.Toolbar.OnMenuItemClickListener onMenuItemClick =
            new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {

        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.scan:
                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                    startActivityForResult(serverIntent, BluetoothReaderService.REQUEST_CONNECT_DEVICE);
                    return true;
                case R.id.discoverable:
                    // Ensure this device is discoverable by others
                    ensureDiscoverable();
                    return true;
            }
            return true;
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if (D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, BluetoothReaderService.REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mReaderService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mReaderService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mReaderService.getState() == BluetoothReaderService.STATE_NONE) {
                // Start the Bluetooth chat services
                mReaderService.start();

            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getContext(), R.layout.message);
        //  mConversationView = (ListView) findViewById(R.id.in);
        // mConversationView.setAdapter(mConversationArrayAdapter);
/*
        fingerprintImage = (ImageView) findViewById(R.id.imageView1);


        final Button btnEnrolToHost = (Button) findViewById(R.id.btnEnrolToHost);
        btnEnrolToHost.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Enrol Host ...");
                mReaderService.EnrolToHost();
            }
        });

        final Button btnCaptureToHost = (Button) findViewById(R.id.btnCaptureToHost);
        btnCaptureToHost.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Capture Host ...");
                mReaderService.CaptureToHost();
            }
        });

        /*final Button btbMatcTemplate = (Button) findViewById(R.id.btbMatcTemplate);
        btbMatcTemplate.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	//Match In Device
            	mReaderService.MatchInDevice(mRefData,mMatData);
            	//Match In System
            	//int score=mReaderService.MatchTemplate(mRefData,mMatData);
           		//AddStatusList("Match Score:"+String.valueOf(score));
            }
        });*/
/*
        final Button btnGetImage = (Button) findViewById(R.id.btnGetImage);
        btnGetImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Get Image ...");
                //mReaderService.GetImageData(BluetoothReaderService.IMAGESIZE_152_200);
                mReaderService.GetImageData(BluetoothReaderService.IMAGESIZE_256_288);
                //mReaderService.GetImageData(BluetoothReaderService.IMAGESIZE_256_360);
                //mReaderService.GetImageSize();
                mTimeStart = SystemClock.uptimeMillis();
            }
        });

        final Button btnGetTemplate = (Button) findViewById(R.id.btnGetTemplate);
        btnGetTemplate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Get Data ...");
                mReaderService.GetTemplate();
            }
        });

        final Button btnEnrolInModule = (Button) findViewById(R.id.btnEnrolInModule);
        btnEnrolInModule.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Enrol ID ...");
                mReaderService.EnrolInModule(1);
            }
        });

        final Button btnVerifyInModule = (Button) findViewById(R.id.btnVerifyInModule);
        btnVerifyInModule.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Verify ID ...");
                mReaderService.VerifyInModule(1);
            }
        });

        final Button btnIdentifyInModule = (Button) findViewById(R.id.btnIdentifyInModule);
        btnIdentifyInModule.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Search ...");
                mReaderService.SearchInModule();
            }
        });


        final Button btnDeleteInModule = (Button) findViewById(R.id.btnDeleteInModule);
        btnDeleteInModule.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Delete ID ...");
                mReaderService.DeleteInModule(1);
            }
        });

        final Button btnClearInModule = (Button) findViewById(R.id.btnClearInModule);
        btnClearInModule.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Clear ...");
                mReaderService.ClearModule();
            }
        });


        final Button btnGetSN = (Button) findViewById(R.id.btnGetSN);
        btnGetSN.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Device SN  ...");
                mReaderService.GetDeviceSn();
            }
        });

        final Button btnGetBatVal = (Button) findViewById(R.id.btnGetBatVal);
        btnGetBatVal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Battery Value ...");
                mReaderService.GetBatVal();
            }
        });

        final Button btnCloseDevice = (Button) findViewById(R.id.btnCloseDevice);
        btnCloseDevice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Close Device ...");
                mReaderService.CloseDevvice();
            }
        });

        final Button btnGetVersion = (Button) findViewById(R.id.btnGetVersion);
        btnGetVersion.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Get Version ...");
                mReaderService.GetDeviceVer();
            }
        });

    /*    final Button btnTestSendRev = (Button) findViewById(R.id.btnTestSendRev);
        btnTestSendRev.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            }
        });
        */
/*
        final Button btnClearTemplate = (Button) findViewById(R.id.btnClearTemplate);
        btnClearTemplate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AddStatusList("Clear Template ...");
                DeleteDataFile();
            }
        });

  */
        mReaderService = new BluetoothReaderService(getContext(), mHandler);    // Initialize the BluetoothChatService to perform bluetooth connections
        mOutStringBuffer = new StringBuffer("");                    // Initialize the buffer for outgoing messages
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mReaderService != null) mReaderService.stop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);

    }

    private void ensureDiscoverable() {
        if (D) Log.d(TAG, "ensure discoverable");
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    public void AddStatusList(String text) {
        mConversationArrayAdapter.add(text);
        Log.d(TAG, text);
    }

    private void AddStatusListHex(byte[] data, int size) {
        String text = "";
        for (int i = 0; i < size; i++) {
            text = text + "," + Integer.toHexString(data[i] & 0xFF).toUpperCase();
        }
        mConversationArrayAdapter.add(text);
    }

    private void AddLogListHex(byte[] data, int size) {
        //String text="";
        //for(int i=0;i<size;i++) {
        //	text=text+","+Integer.toHexString(data[i]&0xFF).toUpperCase();
        //}
        //Log.d(TAG, text);
    }

    public void SaveTextToFile(String data, String filename) {
        File file = new File(filename);
        try {
            file.createNewFile();

            FileOutputStream out = new FileOutputStream(file);
            out.write(data.getBytes());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (D) Log.e(TAG, ">>>Save To File");
    }

    // The Handler that gets information back from the BluetoothChatService
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothReaderService.MESSAGE_STATE_CHANGE:
                    if (D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothReaderService.STATE_CONNECTED:
                            mToolbar.setSubtitle(/*R.string.title_connected_to +":"+ */mConnectedDeviceName);
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothReaderService.STATE_CONNECTING:
                            mToolbar.setSubtitle(R.string.title_connecting);
                            break;
                        case BluetoothReaderService.STATE_LISTEN:
                        case BluetoothReaderService.STATE_NONE:
                            mToolbar.setSubtitle(R.string.title_not_connected);
                            break;
                    }
                    break;
                case BluetoothReaderService.MESSAGE_WRITE:
                    //byte[] writeBuf = (byte[]) msg.obj;
                    //AddStatusListHex(writeBuf,writeBuf.length);
                    break;
                case BluetoothReaderService.MESSAGE_READ:
                    //byte[] readBuf = (byte[]) msg.obj;
                    //AddStatusList("Read Len="+Integer.toString(msg.arg1));
                    //AddStatusListHex(readBuf,msg.arg1);
                    //AddLogListHex(readBuf,msg.arg1);
                    break;
                case BluetoothReaderService.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(BluetoothReaderService.DEVICE_NAME);
                    Toast.makeText(getContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    mReaderService.GetDeviceSn();
                    break;
                case BluetoothReaderService.MESSAGE_TOAST:
                    Toast.makeText(getContext(), msg.getData().getString(BluetoothReaderService.TOAST), Toast.LENGTH_SHORT).show();
                    break;

                case BluetoothReaderService.CMD_GETIMAGE:
                    if (msg.arg1 == 1) {
                        byte[] bmpdata = null;
                        switch (msg.arg2) {
                            case BluetoothReaderService.IMAGESIZE_152_200:
                                bmpdata = mReaderService.getFingerprintImage((byte[]) msg.obj, 152, 200, 0);
                                break;
                            case BluetoothReaderService.IMAGESIZE_256_288:
                                bmpdata = mReaderService.getFingerprintImage((byte[]) msg.obj, 256, 288, 0);
                                break;
                            case BluetoothReaderService.IMAGESIZE_256_360:
                                bmpdata = mReaderService.getFingerprintImage((byte[]) msg.obj, 256, 360, 0);
                                break;
                        }
                        Bitmap image = BitmapFactory.decodeByteArray(bmpdata, 0, bmpdata.length);
                        fingerprintImage.setImageBitmap(image);
                        mTimeEnd = SystemClock.uptimeMillis();
                        AddStatusList("Display Image:" + String.valueOf((mTimeEnd - mTimeStart)) + "ms");
                    } else {
                    }
                    break;
                case BluetoothReaderService.CMD_ENROLID:
                    if (msg.arg1 == 1) {
                        AddStatusList("Enrol Succeed:" + String.valueOf(msg.arg2));
                    } else
                        AddStatusList("Enrol Fail");
                    break;
                case BluetoothReaderService.CMD_VERIFY:
                    if (msg.arg1 == 1)
                        AddStatusList("Verify Succeed");
                    else
                        AddStatusList("Verify Fail");
                    break;
                case BluetoothReaderService.CMD_IDENTIFY:
                    if (msg.arg1 == 1){
                        AddStatusList("Search Result:" + String.valueOf(msg.arg2));
                       jornadaTask = new JornadaTask(String.valueOf(msg.arg2).trim(),iddispositivo);
                       jornadaTask.execute();}

                    else
                        AddStatusList("Search Fail");
                        txtResultado.setText("Empleado no encontrado");
                    break;
                case BluetoothReaderService.CMD_DELETEID:
                    if (msg.arg1 == 1)
                        AddStatusList("Delete Succeed");
                    else
                        AddStatusList("Delete Fail");
                    break;
                case BluetoothReaderService.CMD_CLEARID:
                    if (msg.arg1 == 1)
                        AddStatusList("Clear Succeed");
                    else
                        AddStatusList("Clear Fail");
                    break;
                case BluetoothReaderService.CMD_ENROLHOST:
                    if (msg.arg1 == 1) {
                        //byte[] readBuf = (byte[]) msg.obj;
                        mRefSize = msg.arg2;
                        if (IsTemplateEnrol((byte[]) msg.obj)) {
                            AddStatusList("Already enroled!");
                            break;
                        }
                        mReaderService.memcpy(mRefData, 0, (byte[]) msg.obj, 0, msg.arg2);
                        AddStatusList("Enrol Succeed");
                        if (mRefCount < 512) {
                            System.arraycopy(mRefData, 0, mRefList[mRefCount], 0, 512);
                            mRefCount++;
                            WriteDataToFile();
                            AddStatusList("Enrol OK:" + String.valueOf(mRefCount));
                        }
                    } else
                        AddStatusList("Enrol Fail");
                    break;
                case BluetoothReaderService.CMD_CAPTUREHOST:
                    if (msg.arg1 == 1) {
                        ReadDataFromFile();
                        //byte[] readBuf = (byte[]) msg.obj;
                        mMatSize = msg.arg2;
                        //mReaderService.memcpy(mMatData, 0, (byte[]) msg.obj, 0, msg.arg2);
                        // AddStatusList("Capture Succeed");
                        boolean bok = false;
                        Log.d(TAG, "mRefCount:" + mRefCount);
                        for (int i = 0; i < mRefCount; i++) {
                            int score = mReaderService.MatchTemplate(mRefList[i], (byte[]) msg.obj);
                            if (score >= 60) {
                                AddStatusList("Match Score:" + String.valueOf(score) + "  ID:" + String.valueOf(i + 1));
                                bok = true;
                                break;
                            }
                        }
                        if (!bok)
                            AddStatusList("Capture Fail");
                    } else
                        AddStatusList("Capture Fail");
                    break;
                case BluetoothReaderService.CMD_MATCH:
                    if (msg.arg1 == 1)
                        AddStatusList("Match Succeed:" + String.valueOf(msg.arg2));
                    else
                        AddStatusList("Match Fail");
                    break;
                case BluetoothReaderService.CMD_WRITEFPCARD:
                    if (msg.arg1 == 1)
                        AddStatusList("Write Fingerprint Card Succeed");
                    else
                        AddStatusList("Write Fingerprint Card Fail");
                    break;
                case BluetoothReaderService.CMD_READFPCARD:
                    if (msg.arg1 == 1) {
                        mReaderService.memcpy(mCardData, 0, (byte[]) msg.obj, 0, msg.arg2);
                        mCardSize = msg.arg2;
                        AddStatusList("Read Fingerprint Card Succeed");
                    } else
                        AddStatusList("Read Fingerprint Card Fail");
                    break;
                case BluetoothReaderService.CMD_FPCARDMATCH:
                    if (msg.arg1 == 1) {
                        AddStatusList("Fingerprint Match Succeed");
                        int size = msg.arg2;
                        byte[] tmpbuf = new byte[size];
                        mReaderService.memcpy(tmpbuf, 0, (byte[]) msg.obj, 8, size);
                        AddStatusList("Len=" + String.valueOf(size));
                        AddStatusListHex(tmpbuf, size);
                        String txt = new String(tmpbuf);
                        AddStatusList(txt);
                    } else
                        AddStatusList("Fingerprint Match Fail");
                    break;
                case BluetoothReaderService.CMD_UPCARDSN:
                case BluetoothReaderService.CMD_CARDSN:
                    if (msg.arg1 == 1) {
                        mReaderService.memcpy(mCardSn, 0, (byte[]) msg.obj, 0, msg.arg2);
                        AddStatusList("Read Card SN Succeed:" + Integer.toHexString(mCardSn[0] & 0xFF) + Integer.toHexString(mCardSn[1] & 0xFF) + Integer.toHexString(mCardSn[2] & 0xFF) + Integer.toHexString(mCardSn[3] & 0xFF));
                    } else
                        AddStatusList("Read Card SN Fail");
                    break;
                case BluetoothReaderService.CMD_WRITEDATACARD:
                    if (msg.arg1 == 1)
                        AddStatusList("Write Card Data Succeed");
                    else
                        AddStatusList("Write Card Data Fail");
                    break;
                case BluetoothReaderService.CMD_READDATACARD:
                    if (msg.arg1 == 1) {
                        mReaderService.memcpy(mCardData, 0, (byte[]) msg.obj, 0, msg.arg2);
                        mCardSize = msg.arg2;
                        AddStatusList("Read Card Data Succeed");
                    } else
                        AddStatusList("Read Card Data Fail");
                    break;
                case BluetoothReaderService.CMD_GETSN:
                    if (msg.arg1 == 1) {
                        byte[] snb = new byte[32];
                        mReaderService.memcpy(snb, 0, (byte[]) msg.obj, 0, msg.arg2);
                        String sn = null;
                        try {
                            sn = new String(snb, 0, msg.arg2, "UNICODE");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        AddStatusList("SN:" + sn);
                        iddispositivo =sn;

                    } else
                        AddStatusList("Get SN Fail");
                    break;
                case BluetoothReaderService.CMD_GETBAT:
                    if (msg.arg1 == 1) {
                        mReaderService.memcpy(mBat, 0, (byte[]) msg.obj, 0, msg.arg2);
                        AddStatusList("Battery Value:" + Integer.toString(mBat[0] / 10) + "." + Integer.toString(mBat[0] % 10) + "V");
                    } else
                        AddStatusList("Get Battery Value Fail");
                    break;
                case BluetoothReaderService.CMD_GETVERSION: {
                    if (msg.arg1 == 1) {
                        mReaderService.memcpy(mVersion, 0, (byte[]) msg.obj, 0, msg.arg2);
                        String ver = null;
                        try {
                            ver = new String(mVersion, 0, msg.arg2, "ASCII");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        AddStatusList("Ver:" + ver);
                    } else
                        AddStatusList("Fail");
                }
                break;
                case BluetoothReaderService.CMD_SHUTDOWNDEVICE: {
                    if (msg.arg1 == 1) {
                        AddStatusList("Shutdown Device Succeed");
                    } else
                        AddStatusList("Shutdown Device  Fail");
                }
                break;
                case BluetoothReaderService.CMD_GETCHAR:
                    if (msg.arg1 == 1) {
                        mReaderService.memcpy(mMatData, 0, (byte[]) msg.obj, 0, msg.arg2);
                        mMatSize = msg.arg2;
                        AddStatusList("Len=" + String.valueOf(mMatSize));
                        AddStatusList("Get Data Succeed");
                        AddStatusListHex(mMatData, mMatSize);
                    } else
                        AddStatusList("Get Data Fail");
                    break;
                case BluetoothReaderService.CMD_GETIMAGESIZE:
                    if (msg.arg1 == 1) {
                        AddStatusList("Total Image Size:" + String.valueOf(msg.arg2));
                        SystemClock.sleep(100);
                        mReaderService.GetImageDataEx(128);
                    } else {
                        AddStatusList("Get Image Size Fail");
                    }
                    break;
                case BluetoothReaderService.CMD_GETIMAGEDATA:
                    if (msg.arg1 == 1) {
                        byte[] bmpdata = mReaderService.getFingerprintImage((byte[]) msg.obj, 256, 288, 0/*18*/);
                        Bitmap image = BitmapFactory.decodeByteArray(bmpdata, 0, bmpdata.length);
                        fingerprintImage.setImageBitmap(image);
                        mTimeEnd = SystemClock.uptimeMillis();
                        AddStatusList("Display Image:" + String.valueOf((mTimeEnd - mTimeStart)) + "ms");
                    } else if (msg.arg1 == 2) {
                        SystemClock.sleep(100);
                        mReaderService.GetImageDataEx(128);
                    } else
                        AddStatusList("Get Image Data Fail");
                    break;
            }
        }
    };

    private boolean IsTemplateEnrol(byte[] isodata) {
        for (int i = 0; i < mRefCount; i++) {
            int score = mReaderService.MatchTemplate(mRefList[i], isodata);
            if (score > 50) {
                return true;
            }
        }
        return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case BluetoothReaderService.REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mReaderService.connect(device);
                }
                break;
            case BluetoothReaderService.REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getContext(), R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
                break;
        }
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getContext(), DeviceListActivity.class);
                startActivityForResult(serverIntent, BluetoothReaderService.REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
        }
        return false;
    }

    public class JornadaTask extends AsyncTask<Void, Void, String> {


        private final String id_huella;
        private final String id_dispositivo;


        private String dataecriptada = "";

        URL url = null;
        BufferedReader bufferedReader = null;
        BufferedWriter bufferedWriter = null;
        InputStreamReader streamReader = null;
        InputStream inputStream;
        //  AesCipher seguridad;
        String keypro = "0123456789abcdef";
        String iv = "abcdef9876543210";
        //  AesCipher encrypted;
        //  AesCipher decrypted ;


        JornadaTask(String _id_huella, String _id_dispositivo) {

            id_huella = _id_huella;
            id_dispositivo= _id_dispositivo;


        }

        @Override
        protected String doInBackground(Void... params) {
            // TODO: attempt authentication against a network service

            JSONObject parametros = new JSONObject();
            String data = id_huella.trim()+"|"+id_dispositivo.trim();
            // Log.d("data",data);
          /*  try {
                encrypted=  seguridad.encrypt(keypro,data);
                dataecriptada = encrypted.getData();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //    Log.d("dataencriptada",dataecriptada);
            decrypted = seguridad.decrypt(keypro, encrypted.getData());*/
            // Log.d("datadesencriptada",decrypted.getData());
            //  Log.d("initvector",encrypted.getInitVector());

            try {
                url = new URL(Configuraciones.urlServer + "marcarx?params=" + data.trim());
            } catch (MalformedURLException e) {
                e.printStackTrace();

                Toast.makeText(getContext(), "Opps problemas de conexión", Toast.LENGTH_LONG);
                return "E";
            }
            Log.d("coxion)", "entra a conectar");
            HttpURLConnection urlConnection = null;
            try {
                urlConnection = (HttpURLConnection) url.openConnection();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Opps problemas de conexión", Toast.LENGTH_LONG);
                return "E";
            }
            try {
                urlConnection.setRequestMethod("GET");
            } catch (ProtocolException e) {

                e.printStackTrace();
                Toast.makeText(getContext(), "Opps problemas de conexión", Toast.LENGTH_LONG);
                return "E";
            }

            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            //  Log.d("Connect)","ok");


            try {
                inputStream = urlConnection.getInputStream();
                Log.d("inputStream)", "ok");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("inputStream)", e.toString());
                Toast.makeText(getContext(), "Opps problemas de conexión", Toast.LENGTH_LONG);
                return "E";
            }


            try {
                streamReader = new InputStreamReader(inputStream, "UTF-8");
                Log.d("InputStreamReader", "OK");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.d("InputStreamReader)", e.toString());
            }

            bufferedReader = new BufferedReader(streamReader);

            StringBuffer buffer = new StringBuffer();
            String line = null;
            try {
                line = bufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (line != null) {

                buffer.append(line);
                break;
            }

            try {
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            resultado = buffer.toString().replaceAll("\"", "");
            Log.d("perfil", resultado.toString());
            // TODO: register the new account here.
            String[] paramsx = resultado.toString().split(Pattern.quote("|"));
            if (paramsx[0].trim().toString().equals("S")) {
                txtResultado.setText(paramsx[1].trim()+" MARCO: "+paramsx[2].trim());
                return "S";
            } else {
                txtResultado.setText(paramsx[1].trim());
                return "N"; }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);


        }

        public String getPostDataString(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            boolean first = true;

            Iterator<String> itr = params.keys();

            while (itr.hasNext()) {

                String key = itr.next();
                Object value = params.get(key);

                if (first)
                    first = false;
                else
                    result.append("&");

                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(value.toString(), "UTF-8"));

            }

            Log.d("from", result.toString());
            return result.toString();
        }

        public Boolean getResultjson(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            boolean first;
            first = params.getBoolean("status");
            return first;
        }

        public String getResultjsonError(JSONObject params) throws Exception {

            StringBuilder result = new StringBuilder();
            String first;
            first = params.getString("data");
            return first;
        }

    }
}