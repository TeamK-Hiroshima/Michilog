package com.oec.sdl.vehicle;


import com.oec.sdl.vehicle.Dto.EvaluationDto;
import com.oec.sdl.vehicle.Dto.Location;
import com.oec.sdl.vehicle.Dto.VehicleEventDto;
import com.oec.sdl.vehicle.Jsons.JsonParser;

//HTTP通信
import java.util.Date;

// データ送信の実装
public abstract class DataSender{

    public void sendEvaluationsWithGps(EvaluationType evaluationType, String Lon, String Lat){
        EvaluationDto evaluationDto = new EvaluationDto();

        evaluationDto.evaluationType = evaluationType;
        evaluationDto.date = new Date(System.currentTimeMillis());

        // TODO ここで GPS と 車種を取得する（車種は将来対応？）
        evaluationDto.location  = new Location(Lat, Lon);

        sendEvaluationsWithGpsCore(evaluationDto);
    }

    public void sendVehicleEventWithGps(VehicleEventType vehicleEventType, String Lon, String Lat){
        VehicleEventDto vehicleEventDto = new VehicleEventDto();

        vehicleEventDto.vehicleEventType = vehicleEventType;
        vehicleEventDto.date= new Date(System.currentTimeMillis());

        // TODO ここで GPS と 車種を取得する（車種は将来対応？）
        vehicleEventDto.location  = new Location(Lat, Lon);

        sendVehicleEventWithGpsCore(vehicleEventDto);
    }

    abstract protected void sendEvaluationsWithGpsCore(EvaluationDto evaluationDto);

    abstract protected void sendVehicleEventWithGpsCore(VehicleEventDto vehicleEventDto);
}
