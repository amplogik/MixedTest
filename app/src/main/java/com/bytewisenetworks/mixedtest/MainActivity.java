package com.bytewisenetworks.mixedtest;

// Java Libs
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

// OpenCV
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
//import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

// Android Libs
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera.Size;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.realname.AircraftBindingState;
import dji.common.realname.AppActivationState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.realname.AppActivationManager;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;


public class MainActivity extends Activity implements CvCameraViewListener2, OnTouchListener {

    /* tag for logging */
    private static final String TAG = MainActivity.class.getName();

    /* DJI vars */
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static BaseProduct mProduct;
    private Handler mHandler;

    /* UX vars */
    protected Button loginBtn;
    protected Button logoutBtn;
    protected TextView bindingStateTV;
    protected TextView appActivationStateTV;
    private AppActivationManager appActivationManager;
    private AppActivationState.AppActivationStateListener activationStateListener;
    private AircraftBindingState.AircraftBindingStateListener bindingStateListener;

    /* General Class vars */
    //private static final String TAG = "OCVSample::MainActivity";
    private MainView mOpenCvCameraView;
    private List<Size> mResolutionList;
    private MenuItem[] mEffectMenuItems;
    private SubMenu mColorEffectsMenu;
    private MenuItem[] mResolutionMenuItems;
    private SubMenu mResolutionMenu;
    private MediaPlayer mp;
    private int frameNumber;

    /* OpenCV vars */
    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    //private Mat                  lastFrame = new Mat();
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    // private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private org.opencv.core.Size SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;

    // move Instruction vars
    private boolean colorFieldMatch = false;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");

        super.onCreate(savedInstanceState);

        /////////////////////////////////////////////////
        // When the compile and target version is higher
        // than 22, please request the following
        // permission at runtime to ensure the SDK works
        // well.  add comment
        /////////////////////////////////////////////////
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /*
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            }
            */

            // verify permissions
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_NETWORK_STATE,
                            android.Manifest.permission.ACCESS_WIFI_STATE,
                            android.Manifest.permission.BLUETOOTH,
                            android.Manifest.permission.BLUETOOTH_ADMIN,
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.CHANGE_WIFI_STATE,
                            android.Manifest.permission.INTERNET,
                            android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_PHONE_STATE,
                            android.Manifest.permission.READ_PHONE_STATE,
                            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                            android.Manifest.permission.VIBRATE,
                            android.Manifest.permission.WAKE_LOCK,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }
                    , 1);
        }

        // setup up window optoins
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // launch main window
        // OpenCV Camera
        setContentView(R.layout.activity_main);

        /*
        // DJI UX
        setContentView(R.layout.dji_view);

        // init DJI UX
        initUI();
        */

        //Initialize DJI SDK Manager
        mHandler = new Handler(Looper.getMainLooper());
        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);

        // bind the camera to the view
        mOpenCvCameraView = findViewById(R.id.mainactivity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        // downscale frame to lower processing requirements
        // native 1440 x 2560 pixels, 16:9 ratio
        //mOpenCvCameraView.setMaxFrameSize(176, 152);
        mOpenCvCameraView.setMaxFrameSize(720, 405);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //@Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_login:{
                break;
            }
            case R.id.btn_logout:{
                break;
            }
            default:
                break;
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void initUI(){
        bindingStateTV = (TextView) findViewById(R.id.tv_binding_state_info);
        appActivationStateTV = (TextView) findViewById(R.id.tv_activation_state_info);
        loginBtn = (Button) findViewById(R.id.btn_login);
        logoutBtn = (Button) findViewById(R.id.btn_logout);
        /*
        loginBtn.setOnClickListener(this);
        logoutBtn.setOnClickListener(this);
        */
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void initData(){
        setUpListener();

        appActivationManager = DJISDKManager.getInstance().getAppActivationManager();

        if (appActivationManager != null) {
            appActivationManager.addAppActivationStateListener(activationStateListener);
            appActivationManager.addAircraftBindingStateListener(bindingStateListener);
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appActivationStateTV.setText("" + appActivationManager.getAppActivationState());
                    bindingStateTV.setText("" + appActivationManager.getAircraftBindingState());

                }
            });
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void setUpListener() {
        // Example of Listener
        activationStateListener = new AppActivationState.AppActivationStateListener() {
            @Override
            public void onUpdate(final AppActivationState appActivationState) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appActivationStateTV.setText("" + appActivationState);
                    }
                });
            }
        };

        bindingStateListener = new AircraftBindingState.AircraftBindingStateListener() {

            @Override
            public void onUpdate(final AircraftBindingState bindingState) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bindingStateTV.setText("" + bindingState);
                    }
                });
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void tearDownListener() {
        if (activationStateListener != null) {
            appActivationManager.removeAppActivationStateListener(activationStateListener);
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appActivationStateTV.setText("Unknown");
                }
            });
        }
        if (bindingStateListener !=null) {
            appActivationManager.removeAircraftBindingStateListener(bindingStateListener);
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bindingStateTV.setText("Unknown");
                }
            });
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        showToast("Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void logoutAccount(){
        UserAccountManager.getInstance().logoutOfDJIUserAccount(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (null == error) {
                    showToast("Logout Success");
                } else {
                    showToast("Logout Error:"
                            + error.getDescription());
                }
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        // used here but never called again.
        //  mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        // have to explicitly define package name because Size is
        // also part of android camera
        SPECTRUM_SIZE = new org.opencv.core.Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void onCameraViewStopped() {
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat lowerRed = new Mat();
        Mat upperRed = new Mat();
        Mat redChannelImage = new Mat();
        Mat green;
        Mat blue;
        Mat orange;
        String LogMessage = new String();


        // only process every  frame

        frameNumber++;



        // convert frame to RGBA image
        mRgba = inputFrame.rgba();
        // Core.flip(mRgba, mRgba, 1);

        // create new image matrix for storing HSV image
        Mat HSVMat = new Mat();

        // Blur RGB image
        Imgproc.medianBlur(mRgba, mRgba, 3);

        // conver RGB image to HSV image
        Imgproc.cvtColor(mRgba, HSVMat, Imgproc.COLOR_RGB2HSV, 0);

        //Log.i(TAG, "onCameraFrame: " + frameNumber);
        //LogMessage = "HSVMat Framesize: "+ String.valueOf(HSVMat.cols()) +  ' ' + String.valueOf(HSVMat.rows());
        //Log.i(TAG, LogMessage );

        // inRange HSV args are Hue Saturation
        Core.inRange(HSVMat,new Scalar(0,100,100),new Scalar(10,255,255),lowerRed);
        Core.inRange(HSVMat,new Scalar(160,100,100),new Scalar(179,255,255),upperRed);
        Core.addWeighted(lowerRed, 1.0, upperRed, 1.0, 0.0, redChannelImage);

        //Log.i(TAG, "onCameraFrame: " + frameNumber);
        //LogMessage = "redChannelImage Framesize: "+ String.valueOf(redChannelImage.cols()) +  ' ' + String.valueOf(redChannelImage.rows());
        //Log.i(TAG, LogMessage );

        /*
        // Use HSV image, and detect lower range red
        Core.inRange(HSVMat, new Scalar(200, 25, 25), new Scalar(250, 50, 50), lowerRed);
        // detect upper range red
        Core.inRange(HSVMat, new Scalar(200, 75 , 75), new Scalar(255, 100, 100), upperRed);
        // add lower and upper ranges o the 'redChannelImage' result
        Core.addWeighted(lowerRed, 1.0, upperRed, 1.0, 0.0, redChannelImage);
        */

        /*
        Core.inRange(HSVMat, new Scalar(200, 100, 100), new Scalar(255, 255, 255), redChannelImage);
        */

        // blur the result to remove noise (9px or under)
        Imgproc.GaussianBlur(redChannelImage, redChannelImage, new org.opencv.core.Size(9, 9), 2, 2);

        /*
        double dp = 1.2d;
        double minDist = 100;
        int minRadius = 0;
        int maxRadius = 0;
        double param1 = 100, param2 = 20;

        // create a circles matrix (draw circles for image)
        Mat circles = new Mat();

        // take the 'redChannelImage' and draw circles image from it.
        Imgproc.HoughCircles(redChannelImage, circles, Imgproc.HOUGH_GRADIENT, dp, redChannelImage.rows() / 8, param1, param2, minRadius, maxRadius);

        // count the number of circles
        int numCircles = (circles.rows() == 0) ? 0 : circles.cols();

        for (int i = 0; i < numCircles; i++) {
            double[] circleCoordinates = circles.get(0, i);
            int x = (int) circleCoordinates[0], y = (int) circleCoordinates[1];
            Point center = new Point(x, y);
            int radius = (int) circleCoordinates[2];
            Imgproc.circle(mRgba, center, radius, new Scalar(0, 255, 0), 4);
        }

        if(numCircles > 0){
            for (int i = 0; i < numCircles; i++){
                final MediaPlayer mp = MediaPlayer.create(this, R.raw.beep2);
                mp.start();
            }
            mp.pause();
        }
        */

        lowerRed.release();
        upperRed.release();
        HSVMat.release();

        // colorFieldMatch
        // test to see if we should continue forwar
        // setMaxFrameSize(720, 405);

        int x = 0;  // half frame height
        int y = 0;  // half frame height
        int w = 200;
        int h = 200;

        // extract centre of frame for testing
        //Rect extractedRect = new Rect (x, y, w, h);

        Rect extractedRect = new Rect (0,0,redChannelImage.cols(),redChannelImage.rows()/2);
        Mat extractedMat = new Mat(redChannelImage, extractedRect);

        //Log.i(TAG, "onCameraFrame: " + frameNumber);
        //LogMessage = "extractedMat Framesize: "+ String.valueOf(extractedMat.cols()) +  ' ' + String.valueOf(extractedMat.rows());
        //Log.i(TAG, LogMessage );
        /*
        Mat extractedMat = new Mat(redChannelImage, extractedRect);
        */


        // count the number of items matching color in the top frame
        double dp = 1.2d;
        double minDist = 100;
        int minRadius = 0;
        int maxRadius = 0;
        double param1 = 100, param2 = 20;
        Mat circles = new Mat();

        Imgproc.HoughCircles(extractedMat, circles, Imgproc.HOUGH_GRADIENT, dp, extractedMat.rows() / 8, param1, param2, minRadius, maxRadius);
        // count the number of circles
        int numCircles = (circles.rows() == 0) ? 0 : circles.cols();
        String ToastMessage = "Matches: " + String.valueOf(numCircles);

        //Log.i(TAG, ToastMessage );


        if(colorFieldMatch == true){
            if(numCircles < 1){
                colorFieldMatch = false;
                // send notice
                //Toast.makeText(this, ToastMessage, Toast.LENGTH_SHORT).show();
                ToastMessage = "Setting false";
                Log.i(TAG, ToastMessage );
            } else {
                colorFieldMatch = true;   // continue on moving in heading direction
            }
        } else {
            if(numCircles > 0){
                ToastMessage = "Setting true";
                Log.i(TAG, ToastMessage );
                colorFieldMatch = true;
            } else {
                colorFieldMatch = false;  // change heading 
                // send message
                //Toast.makeText(this, ToastMessage, Toast.LENGTH_SHORT).show();
            }
        }



        // return the RGB Image for display
        // return mRgba;
        /*
        //return the HSV image for display - this won't work
        return extractedImage;  // must be the same size as the input image in rows/cols
        */


        return redChannelImage;

        //return redChannelImage;

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        List<String> effects = mOpenCvCameraView.getEffectList();

        if (effects == null) {
            Log.e(TAG, "Color effects are not supported by device!");
            return true;
        }

        mColorEffectsMenu = menu.addSubMenu("Color Effect");
        mEffectMenuItems = new MenuItem[effects.size()];

        int idx = 0;
        ListIterator<String> effectItr = effects.listIterator();
        while(effectItr.hasNext()) {
            String element = effectItr.next();
            mEffectMenuItems[idx] = mColorEffectsMenu.add(1, idx, Menu.NONE, element);
            idx++;
        }

        mResolutionMenu = menu.addSubMenu("Resolution");
        mResolutionList = mOpenCvCameraView.getResolutionList();
        mResolutionMenuItems = new MenuItem[mResolutionList.size()];

        ListIterator<Size> resolutionItr = mResolutionList.listIterator();
        idx = 0;
        while(resolutionItr.hasNext()) {
            Size element = resolutionItr.next();
            mResolutionMenuItems[idx] = mResolutionMenu.add(2, idx, Menu.NONE,
                    Integer.valueOf(element.width).toString() + "x" + Integer.valueOf(element.height).toString());
            idx++;
        }

        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item.getGroupId() == 1)
        {
            mOpenCvCameraView.setEffect((String) item.getTitle());
            Toast.makeText(this, mOpenCvCameraView.getEffect(), Toast.LENGTH_SHORT).show();
        }
        else if (item.getGroupId() == 2)
        {
            int id = item.getItemId();
            Size resolution = mResolutionList.get(id);
            mOpenCvCameraView.setResolution(resolution);
            resolution = mOpenCvCameraView.getResolution();
            String caption = Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
            Toast.makeText(this, caption, Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    @SuppressLint("SimpleDateFormat")
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.i(TAG,"onTouch event");

        // play beep
        /*
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.beep2);
        mp.start();
        */

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());
        String fileName = Environment.getExternalStorageDirectory().getPath() +
                "/sample_picture_" + currentDateandTime + ".jpg";
        mOpenCvCameraView.takePicture(fileName);

        // send toast notification window
        Toast.makeText(this, fileName + " saved", Toast.LENGTH_SHORT).show();
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // DJISDKManager
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
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };

}