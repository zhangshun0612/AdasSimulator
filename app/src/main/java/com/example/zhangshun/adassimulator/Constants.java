package com.example.zhangshun.adassimulator;

import android.os.Environment;

import java.io.File;

/**
 * Created by ZhangShun on 2018/3/30.
 */

public class Constants {
    public static final String DIR_IN_SDCARD = "AdasData";
    public static final File DIR = new File(Environment.getExternalStorageDirectory() + File.separator + Constants.DIR_IN_SDCARD);

}
