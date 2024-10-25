package com.example.facedetection;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.annotation.Nullable;

public class FloatingOverlayService extends Service {
    private WindowManager windowManager;
    private ImageView circularObject;
    private WindowManager.LayoutParams params;

    private BroadcastReceiver movementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("UPDATE_CIRCULAR_OBJECT")) {
                // Receive data
                float deltaX = intent.getFloatExtra("deltaX", 0);
                float deltaY = intent.getFloatExtra("deltaY", 0);
                int color = intent.getIntExtra("color", android.graphics.Color.CYAN);

                // Move the object
                moveCircularObject(deltaX, deltaY);

                // Update the object's appearance
                changeCircularObjectAppearance(25, color);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Create the circular object
        circularObject = new ImageView(this);
        ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
        shapeDrawable.getPaint().setColor(android.graphics.Color.CYAN); // Default color
        shapeDrawable.setIntrinsicWidth(50);
        shapeDrawable.setIntrinsicHeight(50);
        circularObject.setBackground(shapeDrawable);

        // Set layout parameters
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager.addView(circularObject, params);

        // Register receiver to listen for updates
        IntentFilter filter = new IntentFilter("UPDATE_CIRCULAR_OBJECT");
        registerReceiver(movementReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (circularObject != null) {
            windowManager.removeView(circularObject);
        }
        unregisterReceiver(movementReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void moveCircularObject(float deltaX, float deltaY) {
        params.x += deltaX;
        params.y += deltaY;

        // Ensure the object stays within screen bounds
        params.x = Math.max(0, Math.min(params.x, windowManager.getDefaultDisplay().getWidth() - circularObject.getWidth()));
        params.y = Math.max(0, Math.min(params.y, windowManager.getDefaultDisplay().getHeight() - circularObject.getHeight()));

        windowManager.updateViewLayout(circularObject, params);
    }

    public void changeCircularObjectAppearance(int radius, int color) {
        ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
        shapeDrawable.setIntrinsicWidth(radius * 2);
        shapeDrawable.setIntrinsicHeight(radius * 2);
        shapeDrawable.getPaint().setColor(color);
        circularObject.setBackground(shapeDrawable);
    }
}
