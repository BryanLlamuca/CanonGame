package com.example.canongame;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Random;

public class CannonView extends SurfaceView implements SurfaceHolder.Callback{
    private static final String TAG = "CannonView";

    private static final int MISS_PENALTY = 2;
    private static final int HIT_REWARD = 3;

    private static final double CANNON_BALL_RADIUS_PERCENT = 3.0 / 40;
    private static final double CANNON_BARREL_WIDTH_PERCENT = 3.0 / 40;
    private static final double CANNON_BARREL_LENGTH_PERCENT = 1.0 / 10;

    public static final double CANNONBALL_RADIUS_PERCENT = 3.0 / 80;
    public static final double CANNONBALL_SPEED_PERCENT = 3.0 / 2;

    public static final double TARGET_WIDTH_PERCENT = 1.0 / 40;
    public static final double TARGET_LENGTH_PERCENT = 3.0 / 20;
    public static final double TARGET_FIRST_X_PERCENT = 3.0 / 5;
    public static final double TARGET_SPACING_PERCENT = 1.0 / 60;
    public static final double TARGET_PIECES = 9;
    public static final double TARGET_MIN_SPEED_PERCENT = 3.0 / 4;
    public static final double TARGET_MAX_SPEED_PERCENT = 6.0 / 4;

    public static final double BLOCKER_WIDTH_PERCENT = 1.0 / 40;
    public static final double BLOCKER_LENGTH_PERCENT = 1.0 / 4;
    public static final double BLOCKER_X_PERCENT = 1.0 / 2;
    public static final double BLOCKER_SPEED_PERCENT = 1.0;

    public static final double TEXT_SIZE_PERCENT = 1.0 / 18;

    private CannonThread cannonThread;
    private Activity activity;
    private boolean dialogIsDisplayed = false;
    private Cannon cannon;
    private Blocker blocker;
    private ArrayList<Target> targets;

    private int screenWidth;
    private int screenHeight;

    private boolean gameOver;
    private double timeLeft;
    private int shotsFired;
    private double totalElapsedTime;

    public static final int CANNON_SOUND_ID = 1;

    public static final int TARGET_SOUND_ID = 0;
    public static final int BLOCKER_SOUND_ID = 2;
    private SoundPool soundPool;
    private SparseIntArray soundMap;

    private Paint textPaint;
    private Paint backgroundPaint;

    public CannonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        activity = (Activity) context;

        getHolder().addCallback(this);
        AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
        attrBuilder.setUsage(AudioAttributes.USAGE_GAME);

        SoundPool.Builder builder = new SoundPool.Builder();
        builder.setMaxStreams(1);
        builder.setAudioAttributes(attrBuilder.build());
        soundPool = builder.build();

        soundMap = new SparseIntArray(3);
        soundMap.put(TARGET_SOUND_ID,
                soundPool.load(context, R.raw.target_hit, 1));
        soundMap.put(TARGET_SOUND_ID,
                soundPool.load(context, R.raw.cannon_fire, 1));
        soundMap.put(TARGET_SOUND_ID,
                soundPool.load(context, R.raw.blocker_hit, 1));

        textPaint = new Paint();
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        screenWidth = w;
        screenHeight = h;

        textPaint.setTextSize((int) (TEXT_SIZE_PERCENT * screenHeight));
        textPaint.setAntiAlias(true);
    }



    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_MOVE){
            alignAndFireCannonball(event);
        }

        return true;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void playSound(int soundId){
        soundPool.play(soundMap.get(soundId), 1 ,1 ,1,0 , 1f);
    }

    public int getScreenWidth() {
        return screenWidth;
    }


    public void stopGame() {
        if (cannonThread != null){
            cannonThread.setRunning(false);
        }
    }

    public void newGame() {
        cannon = new Cannon(this,
                (int) (CANNON_BALL_RADIUS_PERCENT * screenHeight),
                (int) (CANNON_BARREL_LENGTH_PERCENT * screenWidth),
                (int) (CANNON_BARREL_WIDTH_PERCENT * screenHeight));

        Random random = new Random();
        targets = new ArrayList<>();

        int targetX = (int)(TARGET_FIRST_X_PERCENT * screenWidth);
        int targetY = (int)((0.5 - TARGET_LENGTH_PERCENT / 2) * screenHeight);

        for (int n = 0; n < TARGET_PIECES; n++) {

            double velocity = screenHeight * (random.nextDouble() *
                    (TARGET_MAX_SPEED_PERCENT - TARGET_MIN_SPEED_PERCENT) +
                    TARGET_MIN_SPEED_PERCENT);

            int color = (n % 2 == 0) ?
                    getResources().getColor(R.color.dark,
                            getContext().getTheme()) :
                    getResources().getColor(R.color.light,
                            getContext().getTheme());

            velocity *= -1;

            targets.add(new Target(this, color, HIT_REWARD, targetX, targetY,
                    (int)(TARGET_WIDTH_PERCENT * screenWidth),
                    (int)(TARGET_LENGTH_PERCENT * screenHeight),
                    (int) velocity));

            targetX += (TARGET_WIDTH_PERCENT + TARGET_SPACING_PERCENT) *
                    screenWidth;
        }

        blocker = new Blocker(this, Color.BLACK, MISS_PENALTY,
                (int)(BLOCKER_X_PERCENT * screenWidth),
                (int)(0.5 - BLOCKER_LENGTH_PERCENT / 2),
                (int)(BLOCKER_WIDTH_PERCENT * screenWidth),
                (int)(BLOCKER_LENGTH_PERCENT * screenHeight),
                (int)(BLOCKER_SPEED_PERCENT * screenHeight));

        timeLeft = 10;

        shotsFired = 0;
        totalElapsedTime = 0.0;

        if (gameOver) {
            gameOver = false;
            cannonThread = new CannonThread(getHolder());
            cannonThread.start();
        }

        hideSystemBars();
    }

    private void updatePositions(double elapsedTimeMS) {
        double interval = elapsedTimeMS / 1000.0;

        if (cannon.getCannonball() != null)
            cannon.getCannonball().update(interval);

        blocker.update(interval);

        for (GameElement target: targets) {
            target.update(interval);
        }

        timeLeft -= interval;

        if (timeLeft <= 0){
            timeLeft = 0.0;
            gameOver = true;
            cannonThread.setRunning(false);
            showGameOverDialog(R.string.lose);
        }
    }

    public void alignAndFireCannonball(MotionEvent event){
        Point touchPoint = new Point((int) event.getX(), (int) event.getY());

        double centerMinusY = (screenHeight / 2 - touchPoint.y);

        double angle = 0;

        angle = Math.atan2(touchPoint.x, centerMinusY);

        cannon.align(angle);

        if(cannon.getCannonball() == null ||
           !cannon.getCannonball().isOnScreen()){
            cannon.fireCannonball();
            ++shotsFired;
        }
    }

    /*
    public static class DialogGameOver extends DialogFragment{

        private int messageId;
        private int shotsFired;
        private double totalElapsedTime;
        private boolean dialogIsDisplayed;

        public static DialogGameOver newInstance(int title) {
            DialogGameOver message = new DialogGameOver();
            Bundle args = new Bundle();
            args.putInt("title", title);
            message.onCreateDialog(args);
            return message;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            DialogGameOver dgo = new DialogGameOver();
            dgo.onCreateDialog();
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setTitle(getResources().getString(messageId));

            builder.setMessage(getResources().getString(R.string.results_format,
                    shotsFired, totalElapsedTime));

            builder.setPositiveButton(R.string.reset_game,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialogIsDisplayed = false;
                            CannonView.newGame();
                        }
                    }
            );

            return builder.create();
        }
    }*/

    //TODO: Fragment Dialog no se puede instanciar
    private void showGameOverDialog(final int messageId){

        @SuppressLint("ValidFragment") final DialogFragment gameResult =
                new DialogFragment(){
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(getResources().getString(messageId));

                        builder.setMessage(getResources().getString(R.string.results_format,
                                shotsFired, totalElapsedTime));

                        builder.setPositiveButton(R.string.reset_game,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialogIsDisplayed = false;
                                        newGame();
                                    }
                                }
                        );

                        return builder.create();
                    }
                };

        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        showSystemBars();
                        dialogIsDisplayed = true;
                        gameResult.setCancelable(false);
                        gameResult.show(activity.getFragmentManager(), "results");
                    }
                }
        );
    }

    public void drawGameElements(Canvas canvas){
        canvas.drawRect(0,0,canvas.getWidth(),canvas.getHeight(),
                backgroundPaint);

        canvas.drawText(getResources().getString(
                R.string.time_remaining_format, timeLeft), 50,100,textPaint
        );

        cannon.draw(canvas);

        if (cannon.getCannonball() != null &&
                cannon.getCannonball().isOnScreen())
            cannon.getCannonball().draw(canvas);

        blocker.draw(canvas);

        for (GameElement target: targets)
            target.draw(canvas);
    }

    //TODO: Colision con fallas
    public void testForCollisions(){
        if (cannon.getCannonball() != null &&
            cannon.getCannonball().isOnScreen()){
            for (int n = 0; n < targets.size(); n++) {
                if (cannon.getCannonball().collidesWith(targets.get(n))){
                    targets.get(n).playSound();

                    timeLeft += targets.get(n).getHitReward();

                    cannon.removeCannonball();
                    targets.remove(n);
                    --n;
                    break;
                }
            }
        } else {
            cannon.removeCannonball();
        }

        if (cannon.getCannonball() != null &&
            cannon.getCannonball().collidesWith(blocker)){
            blocker.playSound();

            cannon.getCannonball().reverseVelocityX();

            timeLeft -= blocker.getMissPenalty();
        }
    }

    public void releaseResources() {
        soundPool.release();
        soundPool = null;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (!dialogIsDisplayed){
            newGame();
            cannonThread = new CannonThread(holder);
            cannonThread.setRunning(true);
            cannonThread.start();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        boolean retry = true;
        cannonThread.setRunning(false);

        while (retry) {
            try {
                cannonThread.join();
                retry = false;
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted", e);
            }
        }
    }

    private void hideSystemBars(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE
            );
    }

    private void showSystemBars(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
    }

    private class CannonThread extends Thread{
        private SurfaceHolder surfaceHolder;
        private boolean threadIsRunning = true;

        public CannonThread(SurfaceHolder holder){
            surfaceHolder = holder;
            setName("CannonThread");
        }

        public void setRunning(boolean running){
            threadIsRunning = running;
        }

        @Override
        public void run() {
            Canvas canvas = null;
            long previousFrameTime = System.currentTimeMillis();

            while (threadIsRunning){
                try {
                    canvas = surfaceHolder.lockCanvas(null);

                    synchronized (surfaceHolder){
                        long currentTime = System.currentTimeMillis();
                        double elapsedTimeMS = currentTime - previousFrameTime;
                        totalElapsedTime += elapsedTimeMS / 1000.0;
                        updatePositions(elapsedTimeMS);
                        testForCollisions();
                        drawGameElements(canvas);
                        previousFrameTime = currentTime;
                    }
                } finally {
                    if (canvas != null)
                        surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
