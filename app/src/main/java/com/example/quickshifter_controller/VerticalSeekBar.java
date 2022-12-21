package com.example.quickshifter_controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

@SuppressLint("AppCompatCustomView")
public class VerticalSeekBar extends SeekBar {

    public VerticalSeekBar(Context context) {
        super(context);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(h, w, oldh, oldw);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    protected void onDraw(Canvas c) {
        c.rotate(-90);
        c.translate(-getHeight(), 0);
        super.onDraw(c);
    }

    boolean move_break = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (move_break)
                    break;
                setProgress(getMax() - (int) ((getMax() - getMin()) * event.getY() / getHeight()));
                onSizeChanged(getWidth(), getHeight(), 0, 0);
                onChangeListener.onProgressChanged(this, getProgress(), false);
                break;
            case MotionEvent.ACTION_DOWN:
                float slider_position = getHeight() - event.getY();
                move_break = !(getThumb().getBounds().left - 100 < slider_position && getThumb().getBounds().right + 100 > slider_position);
                onChangeListener.onStartTrackingTouch(this);
                break;
            case MotionEvent.ACTION_UP:
                if (move_break)
                    break;
                onChangeListener.onStopTrackingTouch(this);
                break;
            case MotionEvent.ACTION_CANCEL:
                break;
        }

        return true;
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    OnSeekBarChangeListener onChangeListener;

    public ACTION_UP_Callback ACTION_UP_Callback;

    public interface ACTION_UP_Callback {
        void onStartTrackingTouch();
    }
}