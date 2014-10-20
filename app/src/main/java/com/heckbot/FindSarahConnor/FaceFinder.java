package com.heckbot.FindSarahConnor;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.FaceDetector;
import android.util.Log;
import java.util.Arrays;

public class FaceFinder{
    public Bitmap Face = null;
    public Drawable drawInvertedImage;
    public float faceConfidence;

    public FaceFinder(Bitmap workBitmap, int optimalWidth, int optimalHeight, int destW, int destH, float dpScale, Resources resources) {
        MainActivity.runningFaceFinder = true;

        int NUM_FACES = 1; // max is 64
        PointF eyesMidPts[] = new PointF[NUM_FACES];
        float eyesDistance[] = new float[NUM_FACES];

        FaceDetector mFaceDetector = new FaceDetector(optimalWidth, optimalHeight, NUM_FACES);

        FaceDetector.Face[] mFaces = new FaceDetector.Face[NUM_FACES];
        FaceDetector.Face face;

        Arrays.fill(mFaces, null);        // use arraycopy instead?
        Arrays.fill(eyesMidPts, null);        // use arraycopy instead?

        mFaceDetector.findFaces(workBitmap, mFaces);
        for (int i = 0; i < mFaces.length; i++) {
            face = mFaces[i];
            try {
                PointF eyesMP = new PointF();
                // TODO: Convert from array to item
                face.getMidPoint(eyesMP);
                eyesDistance[i] = face.eyesDistance();
                eyesMidPts[i] = eyesMP;
                faceConfidence = face.confidence();
                if (faceConfidence < .515f){
//                    Log.d("FaceFinder","Probably not a face:" + faceConfidence);
                    break;
                }

                Log.i("Face",
                        i + " " + faceConfidence + " " + face.eyesDistance() + " "
                                + "Pose: (" + face.pose(FaceDetector.Face.EULER_X) + ","
                                + face.pose(FaceDetector.Face.EULER_Y) + ","
                                + face.pose(FaceDetector.Face.EULER_Z) + ")"
                                + "Eyes Midpoint: (" + eyesMidPts[i].x + "," + eyesMidPts[i].y + ")"
                );
                //ToDo: decide if I want to stop the preview and compass ever
                Log.d("FaceFinder", "calculating face");

                //Left and Right were 1.75f
                float mFaceLeft = eyesMidPts[0].x - (eyesDistance[0] * 2f);
                //Top and Bottom were 2.0f
                float mFaceTop = eyesMidPts[0].y - (eyesDistance[0] * 2f);
                float mFaceRight = eyesMidPts[0].x + (eyesDistance[0] * 2f);
                float mFaceBottom = eyesMidPts[0].y + (eyesDistance[0] * 2f);
                if (mFaceLeft < 0 || mFaceTop < 0 || mFaceRight > destW || mFaceBottom > destH) {
                    break;
                }
                MainActivity.faceFound = true;

                Bitmap mCroppedFace = Bitmap.createBitmap(workBitmap,
                        (int) mFaceLeft,
                        (int) mFaceTop,
                        (int) (mFaceRight - mFaceLeft),
                        (int) (mFaceBottom - mFaceTop));
                int mScaled = Math.round(150 * dpScale);
                Bitmap mScaledBitmap = Bitmap.createScaledBitmap(mCroppedFace, mScaled, mScaled, false);

                Face = mScaledBitmap;

                //To generate negative image
                float[] colorMatrix_Negative = {
                        -1.0f, 0, 0, 0, 255, //red
                        0, -1.0f, 0, 0, 255, //green
                        0, 0, -1.0f, 0, 255, //blue
                        0, 0, 0, 1.0f, 0 //alpha
                };

                ColorFilter colorFilter_Negative = new ColorMatrixColorFilter(colorMatrix_Negative);

                Drawable drawBitmap = new BitmapDrawable(resources, workBitmap);
                drawBitmap.setColorFilter(colorFilter_Negative);

                drawInvertedImage = drawBitmap;
            } catch (Exception e) {
//                if (true) Log.e("Face", i + " is null");
            }
        }
        if (!MainActivity.faceFound) {
            Face = null;
            MainActivity.runningFaceFinder = false;
        }
    }
}