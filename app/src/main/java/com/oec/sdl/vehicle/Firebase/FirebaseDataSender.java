package com.oec.sdl.vehicle.Firebase;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.oec.sdl.vehicle.DataSender;
import com.oec.sdl.vehicle.Dto.EvaluationDto;
import com.oec.sdl.vehicle.Dto.VehicleEventDto;

import java.util.HashMap;
import java.util.Map;

public class FirebaseDataSender extends DataSender {

    private static final String TAG 					= "FirebaseDataSender";

    final FirebaseFirestore db;

    public FirebaseDataSender(){
        // Access a Cloud Firestore instance from your Activity
        db = FirebaseFirestore.getInstance();
    }

    @Override
    protected void sendEvaluationsWithGpsCore(EvaluationDto evaluationDto) {
        Map<String, Object> evaluations = new HashMap<>();
        evaluations.put("date", evaluationDto.date);
        evaluations.put("evaluationType", evaluationDto.evaluationType.toString());
        evaluations.put("vehicleType", evaluationDto.vehicleType);
        evaluations.put("latitude", evaluationDto.location.latitude);
        evaluations.put("longitude", evaluationDto.location.longnitude);

        // エラー処理してません
        db.collection("evaluations")
                .add(evaluations)
                .addOnFailureListener(e -> Log.w(TAG, "Error writing document", e));
    }

    @Override
    protected void sendVehicleEventWithGpsCore(VehicleEventDto vehicleEventDto) {
        Map<String, Object> vehicleEvent = new HashMap<>();
        vehicleEvent.put("date", vehicleEventDto.date);
        vehicleEvent.put("vehicleEventType", vehicleEventDto.vehicleEventType.toString());
        vehicleEvent.put("vehicleType", vehicleEventDto.vehicleType);
        vehicleEvent.put("latitude", vehicleEventDto.location.latitude);
        vehicleEvent.put("longitude", vehicleEventDto.location.longnitude);

        // エラー処理してません
        db.collection("vehicleEvent")
                .add(vehicleEvent)
                .addOnFailureListener(e -> Log.w(TAG, "Error writing document", e));
    }
}
