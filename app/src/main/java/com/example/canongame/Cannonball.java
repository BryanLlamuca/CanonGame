package com.example.canongame;

import android.graphics.Canvas;
import android.graphics.Rect;

public class Cannonball extends GameElement{
    private float velocityX;
    private boolean onScreen;

    public Cannonball(CannonView view, int color, int soundId, int x,
                      int y, int radius, float velocityX, float velocityY) {
        super(view, color, soundId, x, y, 2 * radius, 2 * radius,
                velocityY);
        this.velocityX = velocityX;
        onScreen = true;
    }

    private int getRadius(){
        return (shape.right - shape.left) / 2;
    }

    public boolean collidesWith(GameElement element){
        return (Rect.intersects(shape, element.shape) && velocityX > 0);
    }

    public boolean isOnScreen(){
        return onScreen;
    }

    public void reverseVelocityX(){
        velocityX *= -1;
    }

    @Override
    public void update(double interval){
        super.update(interval);

        shape.offset((int) (velocityX * interval), 0);

        if (shape.top < 0 || shape.left < 0 ||
                shape.bottom > view.getScreenHeight() ||
                shape.right > view.getScreenWidth()) {
            onScreen = false;
        }
    }

    @Override
    public void draw(Canvas canvas){
        canvas.drawCircle(shape.left + getRadius(),
                shape.top + getRadius(), getRadius(), paint);
    }
}
