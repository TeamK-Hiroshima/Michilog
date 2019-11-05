package com.oec.sdl.vehicle.Http;

import com.oec.sdl.vehicle.DataSender;
import com.oec.sdl.vehicle.Dto.EvaluationDto;
import com.oec.sdl.vehicle.Dto.VehicleEventDto;
import com.oec.sdl.vehicle.Jsons.JsonParser;

public class HttpDataSender extends DataSender {

    @Override
    protected void sendEvaluationsWithGpsCore(EvaluationDto evaluationDto) {

        // JSON に変換
        String json = JsonParser.Parse(evaluationDto);

        System.out.println("[JSON生成<EvaluationDto>]" + json);

        doPostJson(json);
    }

    @Override
    protected void sendVehicleEventWithGpsCore(VehicleEventDto vehicleEventDto) {

        // JSON に変換
        String json = JsonParser.Parse(vehicleEventDto);

        System.out.println("[JSON生成<VehicleEventDto>]" + json);

        doPostJson(json);
    }

    void doPostJson(String json){
        AsyncHttpSender task = new AsyncHttpSender();
        task.execute(json);
    }
}
