package com.oec.sdl.vehicle.Http;

import android.os.AsyncTask;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AsyncHttpSender extends AsyncTask<String, Void, String> {
    static final String Url = "https://45nr480dyl.execute-api.ap-northeast-1.amazonaws.com/test-receiver-999-105/receiver";

    // HTTP 通信する際は、Androidはなぜかメインスレッドで実行すると NetworkOnMainThreadException で怒られる
    // だから、Asyncしないといけないらしい。
    // 詳細 → https://shirusu-ni-tarazu.hatenablog.jp/entry/2013/01/20/033030
    void doPost(String json) throws IOException {
        okhttp3.MediaType mediaTypeJson = okhttp3.MediaType.parse("application/json; charset=utf-8");

        RequestBody requestBody = RequestBody.create(mediaTypeJson, json);

        final Request request = new Request.Builder()
                .url(Url)
                .post(requestBody)//POST指定
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        //同期呼び出し
        Response response = client.newCall(request).execute();
    }

    // このメソッドは必ずオーバーライドする必要があるよ
    // ここが非同期で処理される部分みたいたぶん。
    @Override
    protected String doInBackground(String... json) {
        try {
            doPost(json[0]);
        }
        catch (IOException e){
            // FIXME : エラー処理してない！メンゴ！
            System.out.println("Error.");
        }
        return "OK";
    }
}
