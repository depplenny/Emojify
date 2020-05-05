package com.example.emojify;

import android.graphics.Bitmap;

public class MyData {
    private int i;
    private Bitmap bitmap;

    public MyData(){}

    public MyData(int i, Bitmap bitmap){
        this.i = i;
        this.bitmap =bitmap;
    }

    public void setI(int i) {
        this.i = i;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public int getI() {
        return i;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}

