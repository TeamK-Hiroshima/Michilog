package com.oec.sdl.vehicle;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.oec.sdl.vehicle.Dto.EvaluationDto;
import com.oec.sdl.vehicle.Dto.Location;
import com.oec.sdl.vehicle.Firebase.FirebaseDataSender;
import com.oec.sdl.vehicle.Http.HttpDataSender;
import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.permission.PermissionElement;
import com.smartdevicelink.managers.permission.PermissionStatus;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.rpc.DisplayCapabilities;
import com.smartdevicelink.proxy.rpc.GPSData;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.SetDisplayLayout;
import com.smartdevicelink.proxy.rpc.SetDisplayLayoutResponse;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.TireStatus;
import com.smartdevicelink.proxy.rpc.UnsubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.ComponentVolumeStatus;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.PRNDL;
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout;
import com.smartdevicelink.proxy.rpc.enums.SystemCapabilityType;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class SdlService extends Service {

	private static final String TAG 					= "SDL Service";

	private static final String APP_NAME 				= "SDL Display";
	private static final String APP_ID 					= "8678309";

	private static final String ICON_FILENAME 			= "hello_sdl_icon.png";

	private static final int FOREGROUND_SERVICE_ID = 111;

	// TCP/IP transport config
	// The default port is 12345
	// The IP is of the machine that is running SDL Core
	private static final int TCP_PORT = 19827;
	private static final String DEV_MACHINE_IP_ADDRESS = "m.sdl.tools";

	// variable to create and call functions of the SyncProxy
	private SdlManager sdlManager = null;

	private final DataSender dataSender;

	TireStatus tireStatusTmp = null;

	public SdlService(){
		// DI にしたいけど、Intent とかいうよくわからんやり方でインスタンス生成されるみたいなので
		// とりあえずクラス内で実体を生成するよ
		dataSender = new FirebaseDataSender();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			enterForeground();
		}
	}

	// Helper method to let the service enter foreground mode
	@SuppressLint("NewApi")
	public void enterForeground() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
				Notification serviceNotification = new Notification.Builder(this, channel.getId())
						.setContentTitle("Connected through SDL")
						.setSmallIcon(R.drawable.ic_sdl)
						.build();
				startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startProxy();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

		if (sdlManager != null) {
			sdlManager.dispose();
		}

		super.onDestroy();
	}

	private void startProxy() {

		if (sdlManager == null) {
			Log.i(TAG, "Starting SDL Proxy");

			BaseTransportConfig transport = null;
			if (BuildConfig.TRANSPORT.equals("MULTI")) {
				int securityLevel;
				if (BuildConfig.SECURITY.equals("HIGH")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
				} else if (BuildConfig.SECURITY.equals("MED")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
				} else if (BuildConfig.SECURITY.equals("LOW")) {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
				} else {
					securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
				}
				transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
			} else if (BuildConfig.TRANSPORT.equals("TCP")) {
				transport = new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true);
			} else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
				MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
				mtc.setRequiresHighBandwidth(true);
				transport = mtc;
			}

			// The app type to be used
			final Vector<AppHMIType> appType = new Vector<>();
			appType.add(AppHMIType.MEDIA);


			// The manager listener helps you know when certain events that pertain to the SDL Manager happen
			// Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
			SdlManagerListener listener = new SdlManagerListener() {
			    private PRNDL beforePrndl = null;
                @Override
				public void onStart() {
					// HMI Status Listener
					sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
						@Override
						public void onNotified(RPCNotification notification) {

							OnHMIStatus status = (OnHMIStatus) notification;
							if (status.getHmiLevel() == HMILevel.HMI_FULL && ((OnHMIStatus) notification).getFirstRun()) {

								checkTemplateType();

								checkPermission();

								//setDisplayDefault();
								setDisplayGraphicWithTextButtons();

								initVehicleInfoListener();
							}
						}
					});

                    //これをすると定期的にデータが取得可能
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_VEHICLE_DATA, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnVehicleData onVehicleDataNotification = (OnVehicleData) notification;

                            sdlManager.getScreenManager().beginTransaction();

                            SdlArtwork artwork = null;

                            //回転数が3000以上か、以下で画像を切り替える
                            Integer rpm = onVehicleDataNotification.getRpm();
                            if(rpm != null) {
								Log.d("testmsg","RPM:"+onVehicleDataNotification.getRpm());
//                                if (rpm > 3000) {
//                                    if (sdlManager.getScreenManager().getPrimaryGraphic().getResourceId() != R.drawable.oldman) {
//                                        artwork = new SdlArtwork("oldman.png", FileType.GRAPHIC_PNG, R.drawable.oldman, true);
//                                    }
//                                } else {
//                                    if (sdlManager.getScreenManager().getPrimaryGraphic().getResourceId() != R.drawable.oldman) {
//                                        artwork = new SdlArtwork("clap.png", FileType.GRAPHIC_PNG, R.drawable.clap, true);
//                                    }
//                                }
                                if (artwork != null) {
                                    sdlManager.getScreenManager().setPrimaryGraphic(artwork);
                                }
                            }

                            //テキストを登録する場合
                            sdlManager.getScreenManager().setTextField1("RPM: " + onVehicleDataNotification.getRpm());


                            PRNDL prndl = onVehicleDataNotification.getPrndl();
                            if (prndl != null) {
                                //sdlManager.getScreenManager().setTextField2("ParkBrake: " + prndl.toString());

                                //パーキングブレーキの状態が変わった時だけSpeedを受信させる
                                if(beforePrndl != prndl){
                                    beforePrndl = prndl;
                                    setOnTimeSpeedResponse();
                                }
                            }

							TireStatus tireStatus = onVehicleDataNotification.getTirePressure();

                            if(tireStatusTmp == null){
								tireStatusTmp = onVehicleDataNotification.getTirePressure();
							}

                            if(tireStatus != null && tireStatusTmp != null){
                            	if (tireStatusTmp.getLeftFront().getStatus() !=
										tireStatus.getLeftFront().getStatus() &&
									tireStatus.getLeftFront().getStatus() == ComponentVolumeStatus.FAULT){
									onSendTirePunk(VehicleEventType.LeftRearPunk);
								}
								if (tireStatusTmp.getRightFront().getStatus() !=
										tireStatus.getRightFront().getStatus() &&
										tireStatus.getRightFront().getStatus() == ComponentVolumeStatus.FAULT){
									onSendTirePunk(VehicleEventType.RightFrontPunk);
								}
								if (tireStatusTmp.getLeftRear().getStatus() !=
										tireStatus.getLeftRear().getStatus() &&
										tireStatus.getLeftRear().getStatus() == ComponentVolumeStatus.FAULT){
									onSendTirePunk(VehicleEventType.LeftRearPunk);
								}
								if (tireStatusTmp.getRightRear().getStatus() !=
										tireStatus.getRightRear().getStatus() &&
										tireStatus.getRightRear().getStatus() == ComponentVolumeStatus.FAULT){
									onSendTirePunk(VehicleEventType.RightRearPunk);
								}

								Log.d("testmsg","LeftFrontTireStatus:"+tireStatus.getLeftFront().getStatus());
								Log.d("testmsg","RightFrontTireStatus:"+tireStatus.getRightFront().getStatus());
								Log.d("testmsg","LeftRearTireStatus:"+tireStatus.getLeftRear().getStatus());
								Log.d("testmsg","RightRearTireStatus:"+tireStatus.getRightRear().getStatus());

							}

                            sdlManager.getScreenManager().commit(new CompletionListener() {
                                @Override
                                public void onComplete(boolean success) {
                                    if (success) {
                                        Log.i(TAG, "change successful");
                                    }
                                }
                            });
                        }
                    });
				}

				@Override
				public void onDestroy() {
					UnsubscribeVehicleData unsubscribeRequest = new UnsubscribeVehicleData();
					unsubscribeRequest.setRpm(true);
                    unsubscribeRequest.setPrndl(true);
					unsubscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
						@Override
						public void onResponse(int correlationId, RPCResponse response) {
							if(response.getSuccess()){
								Log.i("SdlService", "Successfully unsubscribed to vehicle data.");
							}else{
								Log.i("SdlService", "Request to unsubscribe to vehicle data was rejected.");
							}
						}
					});
					sdlManager.sendRPC(unsubscribeRequest);

					SdlService.this.stopSelf();
				}

				@Override
				public void onError(String info, Exception e) {
				}
			};

			// Create App Icon, this is set in the SdlManager builder
			SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

			// The manager builder sets options for your session
			SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
			builder.setAppTypes(appType);
			//builder.setTransportType(transport);
			builder.setTransportType(new TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true));
			builder.setAppIcon(appIcon);
			sdlManager = builder.build();
			sdlManager.start();


		}
	}

    /**
     * 一度だけの情報受信
     */
	private void setOnTimeSpeedResponse(){

        GetVehicleData vdRequest = new GetVehicleData();
        vdRequest.setSpeed(true);
        vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                if(response.getSuccess()){
                    Double speed = ((GetVehicleDataResponse) response).getSpeed();
                    changeSpeedTextField(speed);

                }else{
                    Log.i("SdlService", "GetVehicleData was rejected.");
                }
            }
        });
        sdlManager.sendRPC(vdRequest);
    }

    /**
     * Speed情報の往診
     * @param speed
     */
	private void changeSpeedTextField(double speed){
        sdlManager.getScreenManager().beginTransaction();

        //テキストを登録する場合
        sdlManager.getScreenManager().setTextField3("Speed: " + speed);

        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    Log.i(TAG, "change successful");
                }
            }
        });
    }

	/**
	 * 利用可能なテンプレートをチェックする
	 */
	private void checkTemplateType(){

		Object result = sdlManager.getSystemCapabilityManager().getCapability(SystemCapabilityType.DISPLAY);
		if( result instanceof DisplayCapabilities){
			List<String> templates = ((DisplayCapabilities) result).getTemplatesAvailable();

			Log.i("Templete", templates.toString());

		}
	}

    /**
     * 利用する項目が利用可能かどうか
     */
    private void checkPermission(){
        List<PermissionElement> permissionElements = new ArrayList<>();

        //チェックを行う項目
        List<String> keys = new ArrayList<>();
        keys.add(GetVehicleData.KEY_RPM);
        keys.add(GetVehicleData.KEY_SPEED);
        keys.add(GetVehicleData.KEY_PRNDL);
		keys.add(GetVehicleData.KEY_GPS);
		keys.add(GetVehicleData.KEY_TIRE_PRESSURE);
        permissionElements.add(new PermissionElement(FunctionID.GET_VEHICLE_DATA, keys));

        Map<FunctionID, PermissionStatus> status = sdlManager.getPermissionManager().getStatusOfPermissions(permissionElements);

        //すべてが許可されているかどうか
        Log.i("Permission", "Allowed:" + status.get(FunctionID.GET_VEHICLE_DATA).getIsRPCAllowed());

        //各項目ごとも可能
        Log.i("Permission", "KEY_RPM　Allowed:" + status.get(FunctionID.GET_VEHICLE_DATA).getAllowedParameters().get(GetVehicleData.KEY_RPM));

    }

	/**
	 * DEFULTテンプレートのサンプル
	 */
	private void setDisplayDefault(){

        sdlManager.getScreenManager().beginTransaction();

        //テキストを登録する場合
        sdlManager.getScreenManager().setTextField1("RPM: None");
        sdlManager.getScreenManager().setTextField2("ParkBrake: None");
        sdlManager.getScreenManager().setTextField3("Speed: None");

        //画像を登録する
        SdlArtwork artwork = new SdlArtwork("clap.png", FileType.GRAPHIC_PNG, R.drawable.clap, true);

        sdlManager.getScreenManager().setPrimaryGraphic(artwork);
        sdlManager.getScreenManager().commit(new CompletionListener() {
            @Override
            public void onComplete(boolean success) {
                if (success) {
                    //定期受信用のデータを設定する
                    SubscribeVehicleData subscribeRequest = new SubscribeVehicleData();
                    subscribeRequest.setRpm(true);                          //エンジン回転数
                    subscribeRequest.setPrndl(true);                        //シフトレーバの状態
                    subscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
                        @Override
                        public void onResponse(int correlationId, RPCResponse response) {
                            if (response.getSuccess()) {
                                Log.i("SdlService", "Successfully subscribed to vehicle data.");
                            } else {
                                Log.i("SdlService", "Request to subscribe to vehicle data was rejected.");
                            }
                        }
                    });
                    sdlManager.sendRPC(subscribeRequest);

                }
            }
		});
	}

	/**
	 * GRAPHIC_WITH_TEXTBUTTONSテンプレートのサンプル
	 */
	private void setDisplayGraphicWithTextButtons(){
		SetDisplayLayout setDisplayLayoutRequest = new SetDisplayLayout();

		//GRAPHIC_WITH_TEXT_AND_SOFTBUTTONS
		setDisplayLayoutRequest.setDisplayLayout(PredefinedLayout.GRAPHIC_WITH_TEXTBUTTONS.toString());
		setDisplayLayoutRequest.setOnRPCResponseListener(new OnRPCResponseListener() {

			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				if(((SetDisplayLayoutResponse) response).getSuccess()){
					Log.i("SdlService", "Display layout set successfully.");

					sdlManager.getScreenManager().beginTransaction();

					sdlManager.getScreenManager().setTextField1("RPM: None");
					//テキストを登録する場合
					//sdlManager.getScreenManager().setTextField1("Hello, this is MainField1.");

					//画像を登録する
					SdlArtwork artwork = new SdlArtwork("logo.png", FileType.GRAPHIC_PNG, R.drawable.logo, true);

					sdlManager.getScreenManager().setPrimaryGraphic(artwork);
					sdlManager.getScreenManager().commit(new CompletionListener() {
						@Override
						public void onComplete(boolean success) {
							if (success) {
								//定期受信用のデータを設定する
								SubscribeVehicleData subscribeRequest = new SubscribeVehicleData();
								subscribeRequest.setRpm(true);                          //エンジン回転数
								subscribeRequest.setPrndl(true);                        //シフトレーバの状態
								subscribeRequest.setGps(true);
								subscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
									@Override
									public void onResponse(int correlationId, RPCResponse response) {
										if (response.getSuccess()) {
											Log.i("SdlService", "Successfully subscribed to vehicle data.");
										} else {
											Log.i("SdlService", "Request to subscribe to vehicle data was rejected.");
										}
									}
								});

							}
						}
					});

					//ボタンの設定
					SdlArtwork state_artworkGood1 = new SdlArtwork("good.png", FileType.GRAPHIC_PNG, R.drawable.good, true);
					SdlArtwork state_artworkGood2 = new SdlArtwork("good2.png", FileType.GRAPHIC_PNG, R.drawable.good2, true);
					SoftButtonState softButton01State1 = new SoftButtonState("button01_state1", "いいねぇ〜", state_artworkGood1);
					SoftButtonState softButton01State2 = new SoftButtonState("button01_state2", "いいねぇ〜", state_artworkGood2);

					List<SoftButtonState> softButtonStates = Arrays.asList(softButton01State1, softButton01State2);

					SoftButtonObject softButtonObject1 = new SoftButtonObject("softButtonObject01", softButtonStates, softButton01State1.getName(), null);

					softButtonObject1.setOnEventListener(new SoftButtonObject.OnEventListener() {
						@Override
						public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
							softButtonObject.transitionToNextState();

							softButtonObject.transitionToNextState();

							sdlManager.getScreenManager().beginTransaction();

							//画像を登録する
							SdlArtwork artwork = new SdlArtwork("title2.png", FileType.GRAPHIC_PNG, R.drawable.title2, true);
							sdlManager.getScreenManager().setPrimaryGraphic(artwork);
							sdlManager.getScreenManager().commit(new CompletionListener() {
								@Override
								public void onComplete(boolean success) {
									if (success) {
										Log.i(TAG, "welcome show successful");
									}
								}
							});
							onClickGoodButton();
						}

						@Override
						public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {

						}
					});

					SdlArtwork state_artworkBad = new SdlArtwork("bad.png", FileType.GRAPHIC_PNG, R.drawable.bad, true);
					SdlArtwork state_artworkBad2 = new SdlArtwork("bad2.png", FileType.GRAPHIC_PNG, R.drawable.bad2, true);
					SoftButtonState softButton02State1 = new SoftButtonState("button02_state1", "悪いねぇ〜", state_artworkBad);
					SoftButtonState softButton02State2 = new SoftButtonState("button02_state2", "悪いねぇ〜", state_artworkBad2);

					List<SoftButtonState> softButtonStatesBad = Arrays.asList(softButton02State1, softButton02State2);

					SoftButtonObject softButtonObject2 = new SoftButtonObject("softButtonObject02",  softButtonStatesBad, softButton02State1.getName(), null);
					softButtonObject2.setOnEventListener(new SoftButtonObject.OnEventListener() {
						@Override
						public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {

							softButtonObject.transitionToNextState();

							softButtonObject.transitionToNextState();

							sdlManager.getScreenManager().beginTransaction();

							//画像を登録する
							SdlArtwork artwork = new SdlArtwork("logo.png", FileType.GRAPHIC_PNG, R.drawable.logo, true);
							sdlManager.getScreenManager().setPrimaryGraphic(artwork);
							sdlManager.getScreenManager().commit(new CompletionListener() {
								@Override
								public void onComplete(boolean success) {
									if (success) {
										Log.i(TAG, "welcome show successful");
									}
								}
							});

							onClickBadButton();
						}

						@Override
						public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {
						}
					});

					List<SoftButtonObject> buttons = Arrays.asList(softButtonObject1, softButtonObject2);

					//ボタンを登録する
					sdlManager.getScreenManager().setSoftButtonObjects(buttons);

				}
			}
		});

		sdlManager.sendRPC(setDisplayLayoutRequest);
	}


	private void onClickGoodButton(){
		GetVehicleData vdRequest = new GetVehicleData();
		vdRequest.setGps(true);
		vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				if(response.getSuccess()){
					GPSData gpsInfo= ((GetVehicleDataResponse) response).getGps();
					dataSender.sendEvaluationsWithGps(EvaluationType.Good,gpsInfo.getLongitudeDegrees().toString(),gpsInfo.getLatitudeDegrees().toString());
					Log.i("SdlService", "Lon:"+gpsInfo.getLongitudeDegrees().toString() + "Lat:" + gpsInfo.getLatitudeDegrees());
				}else{
					Log.i("SdlService", "GetVehicleData was rejected.");
				}
			}
		});
		sdlManager.sendRPC(vdRequest);
	}

	private void onClickBadButton(){
		GetVehicleData vdRequest = new GetVehicleData();
		vdRequest.setGps(true);
		vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				if(response.getSuccess()){
					GPSData gpsInfo= ((GetVehicleDataResponse) response).getGps();
					dataSender.sendEvaluationsWithGps(EvaluationType.Bad,gpsInfo.getLongitudeDegrees().toString(),gpsInfo.getLatitudeDegrees().toString());
					Log.i("SdlService", "Lon:"+gpsInfo.getLongitudeDegrees().toString() + "Lat:" + gpsInfo.getLatitudeDegrees());
				}else{
					Log.i("SdlService", "GetVehicleData was rejected.");
				}
			}
		});
		sdlManager.sendRPC(vdRequest);
	}

	private void initVehicleInfoListener(){
		SubscribeVehicleData subscribeRequest = new SubscribeVehicleData();
		subscribeRequest.setRpm(true);                          //エンジン回転数
		subscribeRequest.setPrndl(true);                        //シフトレーバの状態
		subscribeRequest.setSpeed(true);                        // スピーど
		subscribeRequest.setSteeringWheelAngle(true);            //　ハンドル舵角
		subscribeRequest.setDriverBraking(true);
		subscribeRequest.setGps(true);
		subscribeRequest.setTirePressure(true);
		subscribeRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				if (response.getSuccess()) {
					Log.i("SdlService", "Successfully subscribed to vehicle data.");
				} else {
					Log.i("SdlService", "Request to subscribe to vehicle data was rejected.");
				}
			}
		});
		sdlManager.sendRPC(subscribeRequest);
	}

	private void onSendTirePunk(VehicleEventType vehicleEventType){
		GetVehicleData vdRequest = new GetVehicleData();
		vdRequest.setGps(true);
		vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {
			@Override
			public void onResponse(int correlationId, RPCResponse response) {
				if(response.getSuccess()){
					GPSData gpsInfo= ((GetVehicleDataResponse) response).getGps();
					dataSender.sendVehicleEventWithGps(vehicleEventType,gpsInfo.getLongitudeDegrees().toString(),gpsInfo.getLatitudeDegrees().toString());
					Log.i("SdlService", "Lon:"+gpsInfo.getLongitudeDegrees().toString() + "Lat:" + gpsInfo.getLatitudeDegrees());
				}else{
					Log.i("SdlService", "GetVehicleData was rejected.");
				}
			}
		});
		sdlManager.sendRPC(vdRequest);
	}
}
