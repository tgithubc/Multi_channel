package com.tgithubc.multi_channel_tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * 获取打包写入Assets中的渠道信息，包括渠道名-生成apk包的系统时间
 */
public class ChannelInfoUtils {

    private static final String FILE = "channel_info";
    private static final String SPLIT_REGULAR = "-";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";


    /**
     * 获取渠道信息
     * @param context
     * @return
     */
    public static String getChannel(Context context) {
        String channelInfo = getChannelInfo(context);
        if (TextUtils.isEmpty(channelInfo)) {
            return "";
        }
        String[] infos = channelInfo.split(SPLIT_REGULAR);
        if (infos.length > 1) {
            return channelInfo.split(SPLIT_REGULAR)[0];
        } else {
            return "";
        }
    }

    /**
     * 获取打包时间
     * @param context
     * @return
     */
    public static String getPackageTime(Context context) {
        String channelInfo = getChannelInfo(context);
        if (TextUtils.isEmpty(channelInfo)) {
            return "";
        }
        String[] infos = channelInfo.split(SPLIT_REGULAR);
        if (infos.length > 1) {
            return formatDate(Long.parseLong(channelInfo.split(SPLIT_REGULAR)[1]));
        } else {
            return "";
        }
    }

    @SuppressLint("SimpleDateFormat")
    private static String formatDate(long time) {
        Date nowTime = new Date(time);
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(nowTime);
    }

    private static String getChannelInfo(Context context) {
        String channelInfo = "";
        try {
            InputStream is = context.getResources().getAssets().open(FILE);
            channelInfo = convertStreamToString(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return channelInfo;
    }

    private static String convertStreamToString(InputStream is) throws IOException {
        Writer writer = new StringWriter();
        char[] buffer = new char[2048];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(is,
                    "UTF-8"));
            int len;
            while ((len = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, len);
            }
        } finally {
            is.close();
        }
        return writer.toString();
    }
}
