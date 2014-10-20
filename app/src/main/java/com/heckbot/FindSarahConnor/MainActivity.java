package com.heckbot.FindSarahConnor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private Camera mCamera;
    private CameraHandlerThread mThread = null;

    private FrameLayout flCameraPreview, flHUD, flResults, flHUDScreen1, flHUDScreen2, flHUDScreen3, flHUDScreen4, flHUDScreen5, flHUDScreen6, flHeadPitch;
    private ImageView ivRedBackground, ivPitch1, ivPitch2, ivPitch3, ivPitch4, ivPitch5, ivPitch6, ivPitch7, ivPitch8, ivTargetReticle, ivPitchArrow, ivHUD3Chart, ivResultsFace, ivResultsInverted, ivResultsFaceOutline, ivResultsFaceWireframe, ivResultsScan;
    private TextView tvHUD2SearchText1, tvHUD2SearchText2, tvHUD1SearchText1, tvHUD1Square, tvHUD1StartText, tvDirectionN, tvDirectionNE, tvDirectionE, tvDirectionSE, tvDirectionS, tvDirectionSW, tvDirectionW, tvDirectionNW, tvQuad1, tvQuad2, tvQuad3, tvQuad4, tv0Pitch, tv90Pitch, tv180Pitch, tv270Pitch, tvHUD3SearchText1, tvHUD3SearchText2, tvHUD3SearchText3, tvHUD3Chart, tvHUD4SearchText1, tvHUD5SearchText1, tvHUD5SearchText2, tvHUD6SearchText1, tvResultsTextSquare, tvResultsText1, tvResultsText2, tvResultsText3, tvResultsText4, tvResultsText5;
    private ImageView ivDebugWorkBmp;
    private FaceFinder mFoundFace;
    public Camera.Size optimalSize;
    public static boolean faceFound = false;
    public static boolean runningFaceFinder = true;
    private CameraPreview mPreview;

    float dpScale;
    int HUD3ChartFullPxHieght;
    private Random rand = new Random();
    private int hudFrame = 0;
    private boolean startTapped = false;
    private int tappedFrame = 0;
    private int selectedHUD = 0;
    List<String> strHUD2SearchList1 = new ArrayList<String>();
    List<String> strHUD2SearchList2 = new ArrayList<String>();
    String strHUD1SearchList1[], strHUD3SearchList1[], strHUD4SearchList1[];

    private Compass mCompass;

    private boolean debug = true;
    private Bitmap debugScaledWorkBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_my);

        initializeHUDVars();

        if (Build.MODEL.startsWith("Glass") && Build.MANUFACTURER.startsWith("Google")) {
            ivRedBackground.setAlpha(1f);
            ivRedBackground.setBackgroundColor(Color.argb(255,255,50,50));
        }

        mCompass = new Compass(this);
        SetupHUD();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopAnimation();
        releaseCamera();
        flCameraPreview.removeView(mPreview);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Create an instance of Camera
        getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);

        flCameraPreview.addView(mPreview, 0);

        startAnimation();
    }

    @Override
    protected void onDestroy()
    {
        releaseCamera();
        super.onDestroy();
    }

    public void onClick(View v) {
        if (faceFound) {
            faceFound = false;
            selectedHUD = 0;
            SetupHUD();
        }
        else {
            if (selectedHUD == 0) {
                startTapped = true;
            }
            else {
                selectedHUD = 0;
            }
            SetupHUD();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            // Stop the preview and release the camera.
            // Execute your logic as quickly as possible
            // so the capture happens quickly.
            releaseCamera();
            return false;
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            onClick(null);
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }


    /** A safe way to get an instance of the Camera object. */
    private void oldGetCameraInstance(){
        try {
            mCamera = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
        }
    }

    // camera on new thread
    private void getCameraInstance(){
        if (mThread == null) {
            mThread = new CameraHandlerThread();
        }

        synchronized (mThread) {
            mThread.openCamera();
        }
    }

    private class CameraHandlerThread extends HandlerThread{
        Handler mHandler = null;
        CameraHandlerThread() {
            super("CameraHandlerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    oldGetCameraInstance();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            }
            catch (InterruptedException e) {
            }
        }
    }

    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setFormat(ImageFormat.NV21);
        }

        public void surfaceCreated(SurfaceHolder holder) {
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            releaseCamera();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
                mCompass.StopCompass();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
                releaseCamera();
            }

            try {
                Camera.Parameters parameters = mCamera.getParameters();
                List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
                optimalSize = getOptimalPreviewSize(sizes, mPreview.getWidth(), mPreview.getHeight());

                List<int[]> fpslist = parameters.getSupportedPreviewFpsRange();

                int[] optimalFPS = getOptimalFrameRate(fpslist);

                parameters.setPreviewFpsRange(optimalFPS[0],optimalFPS[1]);
                parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                mCamera.setParameters(parameters);

                mCamera.setPreviewDisplay(mHolder);

                mCamera.startPreview();
                mCompass.StartCompass();

                int bufSize = optimalSize.width * optimalSize.height *
                        ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
                byte[] cbBuffer = new byte[bufSize];
                mCamera.setPreviewCallbackWithBuffer(this);
                mCamera.addCallbackBuffer(cbBuffer);
            }
            catch (IOException e) {
                releaseCamera();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (!runningFaceFinder && selectedHUD != 0){
                Bitmap mWorkBitmap = Bitmap.createBitmap(optimalSize.width, optimalSize.height, Bitmap.Config.RGB_565);

                // face detection: first convert the image from NV21 to RGB_565
                YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
                        mWorkBitmap.getWidth(), mWorkBitmap.getHeight(), null);
                // TODO: make rect a member and use it for width and height values above
                Rect rect = new Rect(0, 0, mWorkBitmap.getWidth(), mWorkBitmap.getHeight());

                // TODO: use a threaded option or a circular buffer for converting streams?
                ByteArrayOutputStream baout = new ByteArrayOutputStream();
                if (!yuv.compressToJpeg(rect, 100, baout)) {
                }

                BitmapFactory.Options bfo = new BitmapFactory.Options();
                bfo.inPreferredConfig = Bitmap.Config.RGB_565;
                mWorkBitmap = BitmapFactory.decodeStream(
                        new ByteArrayInputStream(baout.toByteArray()), null, bfo);
                if (debug){
                    debugScaledWorkBitmap = Bitmap.createScaledBitmap(mWorkBitmap,mWorkBitmap.getWidth()/4,mWorkBitmap.getHeight()/4,false);
                }
                mFoundFace = new FaceFinder(mWorkBitmap, optimalSize.width, optimalSize.height, flResults.getWidth(), flResults.getHeight(), dpScale, getResources());
            }

            // Requeue the buffer so we get called again
            mCamera.addCallbackBuffer(data);

        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1	;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private int[] getOptimalFrameRate(List<int[]> fpslist) {
        int[] optimalRate = new int[2];
        optimalRate[0] = 0;
        optimalRate[1] = 0;

        for (int[] frameRate : fpslist) {
            if (frameRate[0] == frameRate[1]) {
                if (frameRate[0] > optimalRate[0] && frameRate[1] > optimalRate[1]) {
                    optimalRate[0] = frameRate[0];
                    optimalRate[1] = frameRate[1];
                }
            }
        }
        if (optimalRate[0] > 0 && optimalRate[1] > 0) {
            return optimalRate;
        }
        else {
            for (int[] frameRate : fpslist) {
                if (frameRate[0] != frameRate[1]) {
                    if (frameRate[0] >= optimalRate[0] && frameRate[1] >= optimalRate[1]) {
                        optimalRate[0] = frameRate[0];
                        optimalRate[1] = frameRate[1];
                    }
                }
            }
            return optimalRate;
        }
    }

    private void releaseCamera(){
        if(mCamera != null) {
            try {
                mPreview.getHolder().removeCallback(mPreview);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            } catch (Exception e) {
                e.printStackTrace();
            }

            mCompass.StopCompass();
        }
    }

    long mAnimStartTime;

    Handler mHandler = new Handler();
    Runnable mTick = new Runnable() {
        public void run() {
            animateHUD();
            mHandler.postDelayed(this, 60); // 20ms == 60fps
        }
    };

    void startAnimation() {
        mAnimStartTime = SystemClock.uptimeMillis();
        mHandler.removeCallbacks(mTick);
        mHandler.post(mTick);
    }

    void stopAnimation() {
        mHandler.removeCallbacks(mTick);
    }

    private void animateHUD() {
        if(faceFound && selectedHUD != 99)
        {
            selectedHUD = 99;
            SetupHUD();
        }
        hudFrame++;
        if (debug){
            ivDebugWorkBmp.setImageBitmap(debugScaledWorkBitmap);
        }

        if (hudFrame % 2 == 0){
            float retNewY = ivTargetReticle.getY() + ((float)((rand.nextInt(5) - 2) * 5));
            float retNewX = ivTargetReticle.getX() + ((float)((rand.nextInt(5) - 2) * 5));
            if (retNewY >= (float)(flHUDScreen1.getHeight()/2 - ivTargetReticle.getHeight()/2) + 20
                    || retNewY <= (float)(flHUDScreen1.getHeight()/2 - ivTargetReticle.getHeight()/2) - 20){
                retNewY = ivTargetReticle.getY();
            }
            if (retNewX >= (float)(flHUDScreen1.getWidth()/2 - ivTargetReticle.getWidth()/2) + 20
                    || retNewX <= (float)(flHUDScreen1.getWidth()/2 - ivTargetReticle.getWidth()/2) - 20){
                retNewX = ivTargetReticle.getX();
            }
            ivTargetReticle.setY(retNewY);
            ivTargetReticle.setX(retNewX);
        }
        switch (selectedHUD) {
            case 0:
                if (startTapped) {
                    if (tappedFrame == 3) {
                        tvHUD1StartText.setTextColor(Color.GREEN);
                    }
                    if (tappedFrame >= 3) {
                        tvHUD1StartText.setAlpha(tvHUD1StartText.getAlpha() - .05f);
                    }
                    if (tappedFrame >= 23) {
                        startTapped = false;
                        tappedFrame = 0;
                        runningFaceFinder = false;
                        selectedHUD++;
                        tvHUD1StartText.setWidth(0);
                        tvHUD1StartText.setAlpha(1);
                        tvHUD1StartText.setTextColor(Color.WHITE);
                    }
                    tappedFrame++;
                }
                else {
                    if (tvHUD1StartText.getWidth() < 200 * dpScale) {
                        tvHUD1StartText.setWidth(Math.round(tvHUD1StartText.getWidth() + ((200 * dpScale)/16)));
                    }
                    if (hudFrame % 8 == 0) {
                        tvHUD1Square.setVisibility(View.VISIBLE);
                    }
                    else if (hudFrame % 4 == 0) {
                        tvHUD1Square.setVisibility(View.INVISIBLE);
                    }
                }
            case 1:
                if (hudFrame < 28) {
                    tvHUD1SearchText1.append(strHUD1SearchList1[hudFrame - 1]);
                }
                if (hudFrame >= 28) {
                    if (selectedHUD == 0){
                        CleanHUD();
                    }
                    else {
                        selectedHUD++;
                    }
                    SetupHUD();
                }
                if (hudFrame % 2 == 0) {
                    if(mCompass.compassDegrees >= 337.5 || mCompass.compassDegrees <= 22.5) tvDirectionN.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees > 22.5 && mCompass.compassDegrees < 67.5) tvDirectionNE.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees >= 67.5 && mCompass.compassDegrees <= 112.5) tvDirectionE.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees > 112.5 && mCompass.compassDegrees < 157.5) tvDirectionSE.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees >= 157.5 && mCompass.compassDegrees <= 202.5) tvDirectionS.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees > 202.5 && mCompass.compassDegrees < 247.5) tvDirectionSW.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees >= 247.5 && mCompass.compassDegrees <= 292.5)tvDirectionW.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees > 292.5 && mCompass.compassDegrees < 337.5) tvDirectionNW.setVisibility(View.INVISIBLE);

                    if(mCompass.compassDegrees >= 0 && mCompass.compassDegrees < 90) tvQuad4.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees >= 90 && mCompass.compassDegrees < 180) tvQuad3.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees >= 180 && mCompass.compassDegrees < 270)  tvQuad2.setVisibility(View.INVISIBLE);
                    else if (mCompass.compassDegrees >= 270 && mCompass.compassDegrees <= 360) tvQuad1.setVisibility(View.INVISIBLE);
                }
                else {
                    tvDirectionN.setVisibility(View.VISIBLE);
                    tvDirectionNE.setVisibility(View.VISIBLE);
                    tvDirectionE.setVisibility(View.VISIBLE);
                    tvDirectionSE.setVisibility(View.VISIBLE);
                    tvDirectionS.setVisibility(View.VISIBLE);
                    tvDirectionSW.setVisibility(View.VISIBLE);
                    tvDirectionW.setVisibility(View.VISIBLE);
                    tvDirectionNW.setVisibility(View.VISIBLE);
                    tvQuad1.setVisibility(View.VISIBLE);
                    tvQuad2.setVisibility(View.VISIBLE);
                    tvQuad3.setVisibility(View.VISIBLE);
                    tvQuad4.setVisibility(View.VISIBLE);
                }
                ivPitchArrow.setRotation(mCompass.headPitch);
                switch (hudFrame) {
                    case 0:
                        flHeadPitch.setVisibility(View.VISIBLE);
                        ivPitch1.setVisibility(View.INVISIBLE);
                        ivPitch2.setVisibility(View.INVISIBLE);
                        ivPitch3.setVisibility(View.INVISIBLE);
                        ivPitch4.setVisibility(View.INVISIBLE);
                        ivPitch5.setVisibility(View.INVISIBLE);
                        ivPitch6.setVisibility(View.INVISIBLE);
                        ivPitch7.setVisibility(View.INVISIBLE);
                        ivPitch8.setVisibility(View.INVISIBLE);
                        tv0Pitch.setVisibility(View.INVISIBLE);
                        tv90Pitch.setVisibility(View.INVISIBLE);
                        tv180Pitch.setVisibility(View.INVISIBLE);
                        tv270Pitch.setVisibility(View.INVISIBLE);
                        break;
                    //ToDo: Change to an object array and loop through it
                    case 1:
                        ivPitch1.setVisibility(View.VISIBLE);
                        break;
                    case 2:
                        ivPitch2.setVisibility(View.VISIBLE);
                        break;
                    case 3:
                        ivPitch3.setVisibility(View.VISIBLE);
                        break;
                    case 4:
                        ivPitch4.setVisibility(View.VISIBLE);
                        break;
                    case 5:
                        ivPitch5.setVisibility(View.VISIBLE);
                        break;
                    case 6:
                        ivPitch6.setVisibility(View.VISIBLE);
                        break;
                    case 7:
                        ivPitch7.setVisibility(View.VISIBLE);
                        break;
                    case 8:
                        ivPitch8.setVisibility(View.VISIBLE);
                        break;
                    case 9:
                        tv0Pitch.setVisibility(View.VISIBLE);
                        break;
                    case 10:
                        tv90Pitch.setVisibility(View.VISIBLE);
                        break;
                    case 11:
                        tv180Pitch.setVisibility(View.VISIBLE);
                        break;
                    case 12:
                        tv270Pitch.setVisibility(View.VISIBLE);
                        break;
                    default:
                        if (hudFrame % 2 == 0) flHeadPitch.setVisibility(View.VISIBLE);
                        else flHeadPitch.setVisibility(View.INVISIBLE);
                }
                break;
            case 2:
                if (hudFrame < 28) tvHUD2SearchText1.append(strHUD2SearchList1.get(hudFrame - 1));
                if (hudFrame > 13 && hudFrame < 31) tvHUD2SearchText2.append(strHUD2SearchList2.get(hudFrame - 14));

                if (hudFrame >= 32) {
                    selectedHUD++;
                    SetupHUD();
                }
                break;
            case 3:
                if (hudFrame <= 18) {
                    ivHUD3Chart.getLayoutParams().height = HUD3ChartFullPxHieght / 18 * hudFrame;
                    ivHUD3Chart.requestLayout();
                    tvHUD3SearchText1.append(strHUD3SearchList1[hudFrame - 1]);
                }
                else if (hudFrame <32) {
                    tvHUD3SearchText1.append(strHUD3SearchList1[hudFrame - 1]);
                    if(hudFrame % 2 == 0) tvHUD3Chart.setVisibility(View.VISIBLE);
                    else tvHUD3Chart.setVisibility(View.INVISIBLE);
                }
                if(hudFrame % 2 == 0){
                    if (hudFrame >= 22){
                        tvHUD3SearchText2.setVisibility(View.VISIBLE);
                        tvHUD3SearchText3.setVisibility(View.VISIBLE);
                    }
                    else if (hudFrame >= 10) tvHUD3SearchText2.setVisibility(View.VISIBLE);
                }
                else {
                    tvHUD3SearchText2.setVisibility(View.INVISIBLE);
                    tvHUD3SearchText3.setVisibility(View.INVISIBLE);
                }
                if (hudFrame >= 35) {
                    selectedHUD++;
                    SetupHUD();
                }
                break;
            case 4:
                if (hudFrame < 30) tvHUD4SearchText1.append(strHUD4SearchList1[hudFrame - 1]);
                if (hudFrame >= 30) {
                    selectedHUD++;
                    SetupHUD();
                }
            case 5:
                if (hudFrame < 30) tvHUD5SearchText1.append(strHUD1SearchList1[hudFrame]);
//                if (hudFrame < 30) tvHUD5SearchText1.append(strHUD1SearchList1.get(hudFrame));
                else if (hudFrame == 47) tvHUD5SearchText1.setText("");
                else if (hudFrame >= 52 && hudFrame <= 81) tvHUD5SearchText1.append(strHUD1SearchList1[hudFrame - 52]);
//                else if (hudFrame >= 52 && hudFrame <= 81) tvHUD5SearchText1.append(strHUD1SearchList1.get(hudFrame - 52));
                else if (hudFrame == 101) tvHUD5SearchText1.setText("");
                else if (hudFrame >= 107 && hudFrame <= 136) tvHUD5SearchText1.append(strHUD1SearchList1[hudFrame - 107]);
//                else if (hudFrame >= 107 && hudFrame <= 136) tvHUD5SearchText1.append(strHUD1SearchList1.get(hudFrame - 107));
                else {
                    if(hudFrame % 2 == 0) {
                        if (hudFrame % 4 == 0) tvHUD5SearchText1.setVisibility(View.INVISIBLE);
                        else tvHUD5SearchText1.setVisibility(View.VISIBLE);
                    }
                }

                if (hudFrame >= 17 && hudFrame <= 52) tvHUD5SearchText2.append(strHUD2SearchList2.get(hudFrame - 17));
                else if (hudFrame == 84){
                    tvHUD5SearchText2.setText("");
                    tvHUD5SearchText2.setVisibility(View.VISIBLE);
                }
                else if (hudFrame >= 85 && hudFrame <= 120) tvHUD5SearchText2.append(strHUD2SearchList2.get(hudFrame - 85));
                else {
                    if (hudFrame % 4 == 0) tvHUD5SearchText2.setVisibility(View.VISIBLE);
                    else tvHUD5SearchText2.setVisibility(View.INVISIBLE);
                }

                if (hudFrame > 137) {
                    selectedHUD++;
                    SetupHUD();
                }
                break;
            case 6:
                tvHUD6SearchText1.setText("pitchX: " + mCompass.pitchX);
                tvHUD6SearchText1.append("\nrollY: " + mCompass.rollY);
                tvHUD6SearchText1.append("\nazimuthZ: " + mCompass.azimuthZ);
                tvHUD6SearchText1.append("\nheadPitch: " + mCompass.headPitch);
                tvHUD6SearchText1.append("\ncompass: " + mCompass.compassDegrees);
                break;
            case 99:
                if (hudFrame >= 6 && ivResultsFaceOutline.getLayoutParams().height < ivResultsFaceWireframe.getLayoutParams().height && ivResultsScan.getY() == ivResultsFace.getY())
                {
                    if (hudFrame % 2 == 0) {
                        ivResultsFaceOutline.getLayoutParams().height = ivResultsFaceOutline.getLayoutParams().height + (ivResultsFaceWireframe.getLayoutParams().height / 18);
                        ivResultsFaceOutline.requestLayout();
                    }
                }
                else if (ivResultsFaceOutline.getLayoutParams().height >= ivResultsFaceWireframe.getLayoutParams().height && hudFrame <= 64)
                {
                    if (hudFrame % 4 == 0) ivResultsFaceWireframe.setVisibility(View.VISIBLE);
                    else if (hudFrame % 2 == 0) ivResultsFaceWireframe.setVisibility(View.INVISIBLE);
                }
                else if (hudFrame >= 70 && (ivResultsScan.getY() + ivResultsScan.getLayoutParams().height) < (ivResultsFace.getY() + ivResultsFace.getLayoutParams().height))
                {
                    ivResultsScan.setVisibility(View.VISIBLE);
                    ivResultsScan.setY(ivResultsScan.getY() + (4 * dpScale));
                }
                else if ((ivResultsScan.getY() + ivResultsScan.getLayoutParams().height) >= (ivResultsFace.getY() + ivResultsFace.getLayoutParams().height))
                {
                    ivResultsScan.setVisibility(View.INVISIBLE);
                    ivResultsFaceOutline.getLayoutParams().height = 0;
                    ivResultsFaceOutline.requestLayout();
                    ivResultsFaceWireframe.setVisibility(View.INVISIBLE);
                }
//                else if (ivResultsFaceOutline.getAlpha() > 0)F
//                {
//                    ivResultsFaceOutline.setAlpha(ivResultsFaceOutline.getAlpha() - .05f);
//                    ivResultsFaceOutline.requestLayout();
//                    ivResultsFaceWireframe.setAlpha(ivResultsFaceWireframe.getAlpha() - .05f);
//                    ivResultsFaceWireframe.requestLayout();
//                }
                //if (hudFrame == 1){((TextView)findViewById(R.id.tvResultsTextSpacer)).setText("Conf: " + mFoundFace.faceConfidence);}
                if (hudFrame >= 44 && tvResultsText1.getWidth() <= (240 * dpScale)) {
                    tvResultsTextSquare.setY(tvResultsText1.getY());
                    tvResultsText1.setWidth(Math.round(tvResultsText1.getWidth() + ((20 * dpScale))));
                }
                else if (hudFrame >= 70 && tvResultsText2.getWidth() <= (240 * dpScale)) {
                    tvResultsTextSquare.setY(tvResultsText2.getY());
                    tvResultsText2.setWidth(Math.round(tvResultsText2.getWidth() + ((20 * dpScale))));
                }
                else if (tvResultsText2.getWidth() >= (240 * dpScale) && ivResultsScan.getVisibility() == View.INVISIBLE && tvResultsText3.getWidth() <= (240 * dpScale)) {
                    tvResultsTextSquare.setY(tvResultsText3.getY());
                    tvResultsText3.setWidth(Math.round(tvResultsText3.getWidth() + ((20 * dpScale))));
                }
                else if (tvResultsText3.getWidth() >= (240 * dpScale) && tvResultsText4.getWidth() <= (240 * dpScale)) {
                    tvResultsTextSquare.setY(tvResultsText4.getY());
                    tvResultsText4.setWidth(Math.round(tvResultsText4.getWidth() + ((20 * dpScale))));
                }
                else if (tvResultsText4.getWidth() >= (240 * dpScale) && tvResultsText5.getWidth() <= (240 * dpScale)) {
                    tvResultsTextSquare.setY(tvResultsText5.getY());
                    tvResultsText5.setWidth(Math.round(tvResultsText5.getWidth() + ((20 * dpScale))));
                }

                if (hudFrame >= 44) {
                    if (hudFrame % 8 == 0) tvResultsTextSquare.setVisibility(View.INVISIBLE);
                    else if (hudFrame % 4 == 0) tvResultsTextSquare.setVisibility(View.VISIBLE);
                }
                break;
            default:
                break;
        }
    }

    void initializeHUDVars(){
        flCameraPreview = (FrameLayout) findViewById(R.id.flCameraPreview);
        flHUD = (FrameLayout) findViewById(R.id.flHUD);
        flResults = (FrameLayout) findViewById(R.id.flResults);
        flHUDScreen1 = (FrameLayout) findViewById(R.id.flHUDScreen1);
        flHUDScreen2 = (FrameLayout) findViewById(R.id.flHUDScreen2);
        flHUDScreen3 = (FrameLayout) findViewById(R.id.flHUDScreen3);
        flHUDScreen4 = (FrameLayout) findViewById(R.id.flHUDScreen4);
        flHUDScreen5 = (FrameLayout) findViewById(R.id.flHUDScreen5);
        flHUDScreen6= (FrameLayout) findViewById(R.id.flHUDScreen6);
        ivRedBackground = (ImageView) findViewById(R.id.ivRedBackground);
        ivTargetReticle = (ImageView) findViewById(R.id.ivTargetReticle);
        tvHUD1Square = (TextView) findViewById(R.id.tvHUD1Square);
        tvHUD1StartText = (TextView) findViewById(R.id.tvHUD1StartText);
        tvHUD1SearchText1 = (TextView) findViewById(R.id.tvHUD1SearchText1);
        tvHUD2SearchText1 = (TextView) findViewById(R.id.tvHUD2SearchText1);
        tvHUD2SearchText2 = (TextView) findViewById(R.id.tvHUD2SearchText2);
        tvDirectionN = (TextView) findViewById(R.id.tvDirectionN);
        tvDirectionNE = (TextView) findViewById(R.id.tvDirectionNE);
        tvDirectionE = (TextView) findViewById(R.id.tvDirectionE);
        tvDirectionSE = (TextView) findViewById(R.id.tvDirectionSE);
        tvDirectionS = (TextView) findViewById(R.id.tvDirectionS);
        tvDirectionSW = (TextView) findViewById(R.id.tvDirectionSW);
        tvDirectionW = (TextView) findViewById(R.id.tvDirectionW);
        tvDirectionNW = (TextView) findViewById(R.id.tvDirectionNW);
        tvQuad1 = (TextView) findViewById(R.id.tvQuad1);
        tvQuad2 = (TextView) findViewById(R.id.tvQuad2);
        tvQuad3 = (TextView) findViewById(R.id.tvQuad3);
        tvQuad4 = (TextView) findViewById(R.id.tvQuad4);
        flHeadPitch = (FrameLayout) findViewById(R.id.flHeadPitch);
        ivPitch1 = (ImageView) findViewById(R.id.ivPitch1);
        ivPitch2 = (ImageView) findViewById(R.id.ivPitch2);
        ivPitch3 = (ImageView) findViewById(R.id.ivPitch3);
        ivPitch4 = (ImageView) findViewById(R.id.ivPitch4);
        ivPitch5 = (ImageView) findViewById(R.id.ivPitch5);
        ivPitch6 = (ImageView) findViewById(R.id.ivPitch6);
        ivPitch7 = (ImageView) findViewById(R.id.ivPitch7);
        ivPitch8 = (ImageView) findViewById(R.id.ivPitch8);
        tv0Pitch = (TextView) findViewById(R.id.tv0Pitch);
        tv90Pitch = (TextView) findViewById(R.id.tv90Pitch);
        tv180Pitch = (TextView) findViewById(R.id.tv180Pitch);
        tv270Pitch = (TextView) findViewById(R.id.tv270Pitch);
        ivPitchArrow = (ImageView) findViewById(R.id.ivPitchArrow);
        tvHUD3SearchText1 = (TextView) findViewById(R.id.tvHUD3SearchText1);
        tvHUD3SearchText2 = (TextView) findViewById(R.id.tvHUD3SearchText2);
        tvHUD3SearchText3 = (TextView) findViewById(R.id.tvHUD3SearchText3);
        ivHUD3Chart = (ImageView) findViewById(R.id.ivHUD3Chart);
        tvHUD3Chart = (TextView) findViewById(R.id.tvHUD3Chart);
        tvHUD4SearchText1 = (TextView) findViewById(R.id.tvHUD4SearchText1);
        tvHUD5SearchText1 = (TextView) findViewById(R.id.tvHUD5SearchText1);
        tvHUD5SearchText2 = (TextView) findViewById(R.id.tvHUD5SearchText2);
        tvHUD6SearchText1 = (TextView) findViewById(R.id.tvHUD6SearchText1);
        tvResultsTextSquare = (TextView) findViewById(R.id.tvResultsTextSquare);
        tvResultsText1 = (TextView) findViewById(R.id.tvResultsText1);
        tvResultsText2 = (TextView) findViewById(R.id.tvResultsText2);
        tvResultsText3 = (TextView) findViewById(R.id.tvResultsText3);
        tvResultsText4 = (TextView) findViewById(R.id.tvResultsText4);
        tvResultsText5 = (TextView) findViewById(R.id.tvResultsText5);
        ivResultsFace = (ImageView) findViewById(R.id.ivResultsFace);
        ivResultsInverted = (ImageView) findViewById(R.id.ivResultsInverted);
        ivResultsFaceOutline = (ImageView) findViewById(R.id.ivResultsFaceOutline);
        ivResultsFaceWireframe = (ImageView) findViewById(R.id.ivResultsFaceWireframe);
        ivResultsScan = (ImageView) findViewById(R.id.ivResultsScan);
        ivDebugWorkBmp = (ImageView) findViewById(R.id.ivDebugWorkBmp);

        strHUD2SearchList1.add("        KEY PERFECT 4 0");
        strHUD2SearchList1.add("\n            RUN ON");
        strHUD2SearchList1.add("\n           OVLY OBJ");
        strHUD2SearchList1.add("\n==============================");
        strHUD2SearchList1.add("\n    CODE");
        strHUD2SearchList1.add("     ADDR# -");
        strHUD2SearchList1.add(" ADDR#");
        strHUD2SearchList1.add("\n  ------     --------------");
        strHUD2SearchList1.add("\n    23EE      90E0 - 912F");
        for (int i = 0; i < 16; i++)
        {
            //between 8192 and 12288 (range of 4096)
            strHUD2SearchList1.add(String.format("\n    %s      %s - %s", Integer.toHexString(rand.nextInt(4096) + 8192).toUpperCase(), Integer.toHexString(rand.nextInt(4096) + 36864).toUpperCase(), Integer.toHexString(rand.nextInt(4096) + 36864).toUpperCase()));
        }
        strHUD2SearchList1.add(String.format("\n      %s      %s - %s", Integer.toHexString(rand.nextInt(239) + 16).toUpperCase(), Integer.toHexString(rand.nextInt(4096) + 36864).toUpperCase(), Integer.toHexString(rand.nextInt(4096) + 36864).toUpperCase()));
        strHUD2SearchList1.add("\nPROGRAM CHECK IS  : 0582");

        Typeface computerFont = Typeface.createFromAsset(this.getAssets(), "fonts/computerfont.ttf");
        tvHUD1StartText.setTypeface(computerFont);
        tvResultsText1.setTypeface(computerFont);
        tvResultsText2.setTypeface(computerFont);
        tvResultsText3.setTypeface(computerFont);
        tvResultsText4.setTypeface(computerFont);
        tvResultsText5.setTypeface(computerFont);

        RandomizeSearchList2();

        strHUD1SearchList1 = getResources().getStringArray(R.array.HUD1SearchList1);
        strHUD3SearchList1 = getResources().getStringArray(R.array.HUD3SearchList1);

        dpScale = getResources().getDisplayMetrics().density;
        HUD3ChartFullPxHieght = Math.round(90 * dpScale);

        Bitmap bmpChart = BitmapFactory.decodeResource(this.getResources(), R.drawable.chart_quarter_sized);
        Bitmap bmpScaledChart = Bitmap.createScaledBitmap(bmpChart, Math.round(130 * dpScale),HUD3ChartFullPxHieght,false);
        ivHUD3Chart.setImageBitmap(bmpScaledChart);

        strHUD4SearchList1 = getResources().getStringArray(R.array.HUD4SearchList1);

        Bitmap bmpFaceOutline = BitmapFactory.decodeResource(this.getResources(), R.drawable.face_outline_quarter_size);
        Bitmap bmpScaledFaceOutline = Bitmap.createScaledBitmap(bmpFaceOutline, ivResultsFaceWireframe.getLayoutParams().width,ivResultsFaceWireframe.getLayoutParams().height,false);
        ivResultsFaceOutline.setImageBitmap(bmpScaledFaceOutline);
    }

    private void RandomizeSearchList2() {
        strHUD2SearchList2.clear();
        for (int i = 136; i <= 173; i++) {
            int intRandParams = rand.nextInt(9);
            if (intRandParams == 0) {
                strHUD2SearchList2.add(String.format("\n%s", i));
                i++;
                strHUD2SearchList2.add(String.format("\n%s", i));
                i++;
                strHUD2SearchList2.add(String.format("\n%s", i));
            } else if (intRandParams >= 1 && intRandParams <= 2) {
                strHUD2SearchList2.add(String.format("\n%s  %s  %s", i, Integer.toHexString(rand.nextInt(4096) + 24576).toUpperCase(), Integer.toHexString(rand.nextInt(239) + 16).toUpperCase()));
            } else if (intRandParams >= 8 && intRandParams <= 9) {
                strHUD2SearchList2.add(String.format("\n%s  %s  %s %s %s", i, Integer.toHexString(rand.nextInt(4096) + 24576).toUpperCase(), Integer.toHexString(rand.nextInt(239) + 16).toUpperCase(), Integer.toHexString(rand.nextInt(239) + 16).toUpperCase(), Integer.toHexString(rand.nextInt(239) + 16).toUpperCase()));
            } else {
                strHUD2SearchList2.add(String.format("\n%s  %s  %s %s", i, Integer.toHexString(rand.nextInt(4096) + 24576).toUpperCase(), Integer.toHexString(rand.nextInt(239) + 16).toUpperCase(), Integer.toHexString(rand.nextInt(239) + 16).toUpperCase()));
            }
        }
    }

    private void CleanHUD() {
        //ToDo: many of these should be set in setup hud switch
        RandomizeSearchList2();
        tvHUD1Square.setVisibility(View.INVISIBLE);
        tvHUD1SearchText1.setText("");
        tvHUD2SearchText1.setText("");
        tvHUD2SearchText2.setText("");
        tvHUD3Chart.setVisibility(View.INVISIBLE);
        ivHUD3Chart.getLayoutParams().height = 0;
        ivHUD3Chart.requestLayout();
        tvHUD3SearchText1.setText("");
        tvHUD4SearchText1.setText("");
        tvHUD5SearchText1.setText("");
        tvHUD5SearchText2.setText("");
        ivResultsInverted.setImageDrawable(null);
        ivResultsFaceOutline.getLayoutParams().height = 0;
        ivResultsFaceOutline.requestLayout();
        ivResultsFaceWireframe.setVisibility(View.INVISIBLE);
        ivResultsScan.setY(ivResultsFace.getY());
        tvResultsText1.setWidth(0);
        tvResultsText2.setWidth(0);
        tvResultsText3.setWidth(0);
        tvResultsText4.setWidth(0);
        tvResultsText5.setWidth(0);
        hudFrame = 0;
    }

    private void SetupHUD() {
        if (selectedHUD >= 6 && selectedHUD != 99) selectedHUD = 1;
        CleanHUD();
        flHUDScreen1.setVisibility(View.INVISIBLE);
        flHUDScreen2.setVisibility(View.INVISIBLE);
        flHUDScreen3.setVisibility(View.INVISIBLE);
        flHUDScreen4.setVisibility(View.INVISIBLE);
        flHUDScreen5.setVisibility(View.INVISIBLE);
        flHUDScreen6.setVisibility(View.INVISIBLE);
        flResults.setVisibility(View.INVISIBLE);

        switch (selectedHUD) {
            case 0:
                ivTargetReticle.setVisibility(View.INVISIBLE);
                flHUDScreen1.setVisibility(View.VISIBLE);
                break;
            case 1:
                ivTargetReticle.setVisibility(View.VISIBLE);
                tvHUD1Square.setVisibility(View.INVISIBLE);
                flHUDScreen1.setVisibility(View.VISIBLE);
                break;
            case 2:
                ivTargetReticle.setVisibility(View.VISIBLE);
                flHUDScreen2.setVisibility(View.VISIBLE);
                break;
            case 3:
                ivTargetReticle.setVisibility(View.VISIBLE);
                flHUDScreen3.setVisibility(View.VISIBLE);
                break;
            case 4:
                ivTargetReticle.setVisibility(View.VISIBLE);
                flHUDScreen4.setVisibility(View.VISIBLE);
                break;
            case 5:
                ivTargetReticle.setVisibility(View.VISIBLE);
                flHUDScreen5.setVisibility(View.VISIBLE);
                break;
            case 6:
                ivTargetReticle.setVisibility(View.INVISIBLE);
                flHUDScreen6.setVisibility(View.VISIBLE);
                break;
            case 99:
                ivTargetReticle.setVisibility(View.INVISIBLE);
                flResults.setVisibility(View.VISIBLE);
                ivResultsFace.setImageBitmap(mFoundFace.Face);
                ivResultsInverted.setImageDrawable(mFoundFace.drawInvertedImage);
        }
    }
}
