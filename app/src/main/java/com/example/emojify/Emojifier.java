package com.example.emojify;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.util.concurrent.Callable;

class Emojifier implements Callable<MyData> {


    private Context context;
    private Bitmap bitmap;
    //resultBitmap is the picture with all emoji added
    private MyData myData = new MyData();
    private  Bitmap resultBitmap;
    private  int numOfFaces;

    Emojifier(Context context, Bitmap bitmap) {
        this.context = context;
        this.bitmap = bitmap;
        this.resultBitmap = bitmap;
    }

    @Override
    public MyData call()  {

        // Time consuming operation in another thread

        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        if (!detector.isOperational()) {
            Log.i("XZ","No detector.");
        } else {
            Log.i("XZ","Got detector.");
            Log.i("XZ",Thread.currentThread().getName()+"");
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<Face> faces = detector.detect(frame);

            numOfFaces = faces.size();
            for (int i = 0; i < numOfFaces; i++) {
                // Each face needs one emojiBitmap
                Face face =  faces.valueAt(i);
                Bitmap emojiBitmap;

                switch (whichEmoji(face)) {
                    case SMILE:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.smile);
                        break;
                    case FROWN:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.frown);
                        break;
                    case LEFT_WINK:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.leftwink);
                        break;
                    case RIGHT_WINK:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.rightwink);
                        break;
                    case LEFT_WINK_FROWN:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.leftwinkfrown);
                        break;
                    case RIGHT_WINK_FROWN:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.rightwinkfrown);
                        break;
                    case CLOSED_EYE_SMILE:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.closed_smile);
                        break;
                    case CLOSED_EYE_FROWN:
                        emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.closed_frown);
                        break;
                    default:
                        emojiBitmap = null;
                        Toast.makeText(context, R.string.no_emoji, Toast.LENGTH_SHORT).show();
                }

                // Add the emojiBitmap to the proper position in the original image
                resultBitmap  = addBitmapToFace(resultBitmap, emojiBitmap, face);

            }

        }

        detector.release();
        Log.i("XZ","Detector dismissed.");

        myData.setI(numOfFaces);
        myData.setBitmap(resultBitmap);

        return myData;
    }


    /**
     * Determines the closest emoji to the expression on the face, based on the
     * odds that the person is smiling and has each eye open.
     *
     * @param face The face for which you pick an emoji.
     */
    private  Emoji whichEmoji(Face face) {

        float isLeftEyeOpenProbability = face.getIsLeftEyeOpenProbability();
        float isRightEyeOpenProbability = face.getIsRightEyeOpenProbability();
        float isSmilingProbability = face.getIsSmilingProbability();

        double EYE_OPEN_PROB_THRESHOLD = .5;
        boolean leftEyeClosed = isLeftEyeOpenProbability < EYE_OPEN_PROB_THRESHOLD;
        boolean rightEyeClosed = isRightEyeOpenProbability < EYE_OPEN_PROB_THRESHOLD;
        double SMILING_PROB_THRESHOLD = .15;
        boolean smiling = isSmilingProbability > SMILING_PROB_THRESHOLD;

        // Determine the appropriate emoji
        Emoji emoji;
        if(smiling) {
            if (leftEyeClosed && !rightEyeClosed) {
                emoji = Emoji.LEFT_WINK;
            }  else if(rightEyeClosed && !leftEyeClosed){
                emoji = Emoji.RIGHT_WINK;
            } else if (leftEyeClosed){
                emoji = Emoji.CLOSED_EYE_SMILE;
            } else {
                emoji = Emoji.SMILE;
            }
        } else {
            if (leftEyeClosed && !rightEyeClosed) {
                emoji = Emoji.LEFT_WINK_FROWN;
            }  else if(rightEyeClosed && !leftEyeClosed){
                emoji = Emoji.RIGHT_WINK_FROWN;
            } else if (leftEyeClosed){
                emoji = Emoji.CLOSED_EYE_FROWN;
            } else {
                emoji = Emoji.FROWN;
            }
        }

        // Log the chosen Emoji
        Log.i("XZ", "whichEmoji: " + emoji.name());
        return emoji;

    }

    // Enum for all possible Emojis
    private enum Emoji {
        SMILE,
        FROWN,
        LEFT_WINK,
        RIGHT_WINK,
        LEFT_WINK_FROWN,
        RIGHT_WINK_FROWN,
        CLOSED_EYE_SMILE,
        CLOSED_EYE_FROWN
    }


    /**
     * Combines the original picture with the emoji bitmaps
     *
     * @param backgroundBitmap The original picture
     * @param emojiBitmap      The chosen emoji
     * @param face             The detected face
     * @return The final bitmap, including the emojis over the faces
     */
    private Bitmap addBitmapToFace(Bitmap backgroundBitmap, Bitmap emojiBitmap, Face face) {

        // Initialize the results bitmap to be a mutable copy of the original image
        Bitmap resultBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(),
                backgroundBitmap.getHeight(), backgroundBitmap.getConfig());

        // Determine the size of the emoji to match the width of the face and preserve aspect ratio
        float EMOJI_SCALE_FACTOR = .9f;
        int newEmojiWidth = (int) (face.getWidth() * EMOJI_SCALE_FACTOR);
        int newEmojiHeight =  newEmojiWidth * emojiBitmap.getHeight() / emojiBitmap.getWidth();

        // Scale the emoji
        emojiBitmap = Bitmap.createScaledBitmap(emojiBitmap, newEmojiWidth, newEmojiHeight, false);

        // Determine the emoji position so it best lines up with the face
        float emojiPositionX =
                face.getPosition().x + face.getWidth() / 2f - emojiBitmap.getWidth() / 2f;
        float emojiPositionY =
                face.getPosition().y + face.getHeight() / 2f - emojiBitmap.getHeight() / 2f;

        // Create the canvas and draw the bitmaps to it
        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        canvas.drawBitmap(emojiBitmap, emojiPositionX, emojiPositionY, null);

        return resultBitmap;
    }
}


