package com.bytewisenetworks.mixedtest;

import android.app.Activity;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;
import android.widget.TextView;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

//public class MainActivity extends AppCompatActivity {
public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    /////////////////////////////////////////////////
    // VARIABLES
    /////////////////////////////////////////////////

    // TAG for loggging
    private static final String TAG = MainActivity.class.getName();

    // DJI SDK vars
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static BaseProduct mProduct;
    private Handler mHandler;

    // OpenCV vars
    private static final int       VIEW_MODE_RGBA     = 0;
    private static final int       VIEW_MODE_GRAY     = 1;
    private static final int       VIEW_MODE_CANNY    = 2;
    private static final int       VIEW_MODE_FEATURES = 5;

    private int                    mViewMode;
    private Mat mRgba;
    private Mat                    mIntermediateMat;
    private Mat                    mGray;

    private MenuItem               mItemPreviewRGBA;
    private MenuItem               mItemPreviewGray;
    private MenuItem               mItemPreviewCanny;
    private MenuItem               mItemPreviewFeatures;

    private CameraBridgeViewBase   mOpenCvCameraView;


    /////////////////////////////////////////////////
    // STATIC LIB INCLUDES
    /////////////////////////////////////////////////

    /////////////////////////////////////////////////
    // Used to load the 'native-lib' library on a
    // pplication startup. gonna throw my C++ in this
    // lib
    /////////////////////////////////////////////////
    static {
        System.loadLibrary("native-lib");
    }
    /////////////////////////////////////////////////
    // Load OpenCV
    /////////////////////////////////////////////////
    static {
            if(!OpenCVLoader.initDebug()){
                Log.d(TAG,"OpenCV NOT loaded in MainActivity.java");
            } else {
                Log.d(TAG, "OpenCV loaded in MainActivity.java");
            }
    }

    /////////////////////////////////////////////////
    // METHODS
    /////////////////////////////////////////////////
    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.d(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("mixed_sample");

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        /////////////////////////////////////////////////
        // When the compile and target version is higher
        // than 22, please request the following
        // permission at runtime to ensure the SDK works
        // well.  add comment
        /////////////////////////////////////////////////
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.VIBRATE,
                            android.Manifest.permission.INTERNET,
                            android.Manifest.permission.ACCESS_WIFI_STATE,
                            android.Manifest.permission.WAKE_LOCK,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.ACCESS_NETWORK_STATE,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.CHANGE_WIFI_STATE,
                            android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                            android.Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.mainactivity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.mainactivity_surface_view);
        tv.setText(stringFromJNI());

        //Initialize DJI SDK Manager
        mHandler = new Handler(Looper.getMainLooper());
        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);

    }

    /////////////////////////////////////////////////
    // A native method that is implemented by the
    // 'native-lib' native library,
    // which is packaged with this application.
    // this is an example of how to include my C
    // functions
    /////////////////////////////////////////////////
    public native String stringFromJNI();

    /////////////////////////////////////////////////
    // implement the DJI SDK Manager
    /////////////////////////////////////////////////
    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {
        @Override
        public void onRegister(DJIError error) {
            Log.d(TAG, error == null ? "success" : error.getDescription());
            if(error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "register sdk failed, check if network is available", Toast.LENGTH_LONG).show();
                    }
                });
            }
            Log.e("TAG", error.toString());
        }
        @Override
        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
            mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setBaseProductListener(mDJIBaseProductListener);
            }
            notifyStatusChange();
        }

        private BaseProduct.BaseProductListener mDJIBaseProductListener = new BaseProduct.BaseProductListener() {
            @Override
            public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {
                if(newComponent != null) {
                    newComponent.setComponentListener(mDJIComponentListener);
                }
                notifyStatusChange();
            }
            @Override
            public void onConnectivityChange(boolean isConnected) {
                notifyStatusChange();
            }
        };
        private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
            @Override
            public void onConnectivityChange(boolean isConnected) {
                notifyStatusChange();
            }
        };
        private void notifyStatusChange() {
            mHandler.removeCallbacks(updateRunnable);
            mHandler.postDelayed(updateRunnable, 500);
        }
        private Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
                sendBroadcast(intent);
            }
        };
    };

    /////////////////////////////////////////////////
    // more functions here
    /////////////////////////////////////////////////
    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        final int viewMode = mViewMode;
        switch (viewMode) {
            case VIEW_MODE_GRAY:
                // input frame has gray scale format
                Imgproc.cvtColor(inputFrame.gray(), mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
                break;
            case VIEW_MODE_RGBA:
                // input frame has RBGA format
                mRgba = inputFrame.rgba();
                break;
            case VIEW_MODE_CANNY:
                // input frame has gray scale format
                mRgba = inputFrame.rgba();
                Imgproc.Canny(inputFrame.gray(), mIntermediateMat, 80, 100);
                Imgproc.cvtColor(mIntermediateMat, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
                break;
            case VIEW_MODE_FEATURES:
                // input frame has RGBA format
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();
                FindFeatures(mGray.getNativeObjAddr(), mRgba.getNativeObjAddr());
                break;
        }

        return mRgba;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        if (item == mItemPreviewRGBA) {
            mViewMode = VIEW_MODE_RGBA;
        } else if (item == mItemPreviewGray) {
            mViewMode = VIEW_MODE_GRAY;
        } else if (item == mItemPreviewCanny) {
            mViewMode = VIEW_MODE_CANNY;
        } else if (item == mItemPreviewFeatures) {
            mViewMode = VIEW_MODE_FEATURES;
        }

        return true;
    }
    /////////////////////////////////////////////////
    // A native method
    /////////////////////////////////////////////////
    public native void FindFeatures(long matAddrGr, long matAddrRgba);
}
