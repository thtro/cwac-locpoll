/***
	Copyright (c) 2010 CommonsWare, LLC
	
	Licensed under the Apache License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may obtain
	a copy of the License at
		http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.commonsware.cwac.locpoll;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class LocationPollerService extends Service {
	private static final String LOCK_NAME_STATIC="com.commonsware.cwac.locpoll.LocationPoller";
	private static final int TIMEOUT=120000;	// two minutes
	private static PowerManager.WakeLock lockStatic=null;
	private LocationManager locMgr=null;
	
	synchronized private static PowerManager.WakeLock getLock(Context context) {
		if (lockStatic==null) {
			PowerManager mgr=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
			
			lockStatic=mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
																	LOCK_NAME_STATIC);
			lockStatic.setReferenceCounted(true);
		}
		
		return(lockStatic);
	}
	
	public static void requestLocation(Context ctxt, Intent i) {
		getLock(ctxt).acquire();
		
		i.setClass(ctxt, LocationPollerService.class);
		
		ctxt.startService(i);
	}
	
	@Override
	public void onCreate() {
		locMgr=(LocationManager)getSystemService(LOCATION_SERVICE);
	}
	
	@Override
	public IBinder onBind(Intent i) {
		return(null);
	}
	
	@Override
  public int onStartCommand(Intent intent, int flags, int startId) {
		String provider=intent.getStringExtra(LocationPoller.EXTRA_PROVIDER);
		Intent toBroadcast=(Intent)intent.getExtras().get(LocationPoller.EXTRA_INTENT);
		
		if (provider==null) {
			Log.e(getClass().getName(), "Invalid Intent -- has no provider");
		}
		else if (toBroadcast==null) {
			Log.e(getClass().getName(), "Invalid Intent -- has no Intent to broadcast");
		}
		else {
			toBroadcast.setPackage(getPackageName());
			new PollerThread(getLock(this), locMgr, provider,
											 toBroadcast).start();
		}
		
		return(START_REDELIVER_INTENT);
	}
	
	private class PollerThread extends WakefulThread {
		private LocationManager locMgr=null;
		private String provider=null;
		private Intent intentTemplate=null;
		private Runnable onTimeout=null;
		private LocationListener listener=new LocationListener() {
			public void onLocationChanged(Location location) {
				handler.removeCallbacks(onTimeout);
				Intent toBroadcast=new Intent(intentTemplate);
				
				toBroadcast.putExtra(LocationPoller.EXTRA_LOCATION, location);
				sendBroadcast(toBroadcast);
				quit();
			}
			
			public void onProviderDisabled(String provider) {
				// required for interface, not used
			}
			
			public void onProviderEnabled(String provider) {
				// required for interface, not used
			}
			
			public void onStatusChanged(String provider, int status,
																		Bundle extras) {
				// required for interface, not used
			}
		};
		private Handler handler=new Handler();
		
		PollerThread(PowerManager.WakeLock lock, LocationManager locMgr,
								 String provider, Intent intentTemplate) {
			super(lock, "LocationPoller-PollerThread");
			
			this.locMgr=locMgr;
			this.provider=provider;
			this.intentTemplate=intentTemplate;
		}
		
		@Override
		protected void onPreExecute() {
			onTimeout=new Runnable() {
				public void run() {
					Intent toBroadcast=new Intent(intentTemplate);
					
					toBroadcast.putExtra(LocationPoller.EXTRA_ERROR, "Timeout!");
					sendBroadcast(toBroadcast);
					quit();
				}
			};
			
			handler.postDelayed(onTimeout, TIMEOUT);
			locMgr.requestLocationUpdates(provider, 0, 0, listener);
		}
		
		@Override
		protected void onPostExecute() {
			locMgr.removeUpdates(listener);
			
			super.onPostExecute();
		}
		
		@Override
		protected void onUnlocked() {
			stopSelf();
		}
	}
}
