package com.justinschultz.pusherclient;

/*	
 *  Copyright (C) 2012 Justin Schultz
 *  JavaPusherClient, a Pusher (http://pusherapp.com) client for Java
 *  
 *  http://justinschultz.com/
 *  http://publicstaticdroidmain.com/
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */

import java.net.URI;
import java.util.HashMap;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

public class Pusher {
	private static final String PUSHER_CLIENT = "java-android-client";
	private final String VERSION = "1.11";
	private final String HOST = "ws.pusherapp.com";
	private final int WS_PORT = 80;
	private final String PREFIX = "ws://";
	private PusherClient pusherSocket;
	private String apiKey;
	private final HashMap<String, Channel> channels;

	private PusherListener pusherEventListener;

	public Pusher(String key, boolean useIPv6) {
		apiKey = key;
		channels = new HashMap<String, Channel>();
		if (!useIPv6) {
			java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
			java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		}
	}
	
	public Pusher(String key) {
		this(key, true);
	}
	
	private class PusherClient extends WebSocketClient {
		
		private PusherClientEventHandler mEventHandler;
		private volatile boolean mConnected = false;
		
		public void setEventHandler(PusherClientEventHandler handler) {
			mEventHandler = handler;
		}
		
		public boolean isConnected() {
			return mConnected;
		}

		public PusherClient( URI serverUri , Draft draft ) {
			super( serverUri, draft );
		}

		public PusherClient( URI serverURI ) {
			super( serverURI );
		}

		@Override
		public void onOpen( ServerHandshake handshakedata ) {
			mConnected = true;
		}

		@Override
		public void onMessage( String message ) {
			mEventHandler.onMessage(message);
		}

		@Override
		public void onClose( int code, String reason, boolean remote ) {
			mEventHandler.onClose(code, reason, remote);
		}

		@Override
		public void onError( Exception ex ) {
			mEventHandler.onError(ex);
		}
	}
	
	public void connect() {
		String path = "/app/" + apiKey + "?client=" + PUSHER_CLIENT + "&version=" + VERSION;

		try {
			URI url = new URI(PREFIX + HOST + ":" + WS_PORT + path);
			pusherSocket = new PusherClient(url, new Draft_10());
			
			pusherSocket.setEventHandler(new PusherClientEventHandler() {

				public void onMessage(String message) {
					try {
						JSONObject jsonMessage = new JSONObject(message);
						String event = jsonMessage.optString("event", null);
						
						if(event.equals("pusher:connection_established" ))
						{
							JSONObject data = new JSONObject(jsonMessage.getString("data"));
							pusherEventListener.onConnect(data.getString("socket_id"));
						} else {
							pusherEventListener.onMessage(jsonMessage.toString());
							dispatchChannelEvent(jsonMessage, event);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				public void onClose(int code, String reason, boolean remote) {
					if (pusherEventListener != null) {
						pusherEventListener.onDisconnect();
					}
				}

				public void onError(Exception ex) { }
			});
			
			pusherSocket.connect();
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void disconnect() {
		try {
			pusherSocket.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean isConnected() {
		return pusherSocket.isConnected();
	}
	
	public void setPusherListener(PusherListener listener) {
		pusherEventListener = listener;
	}
	
	public Channel subscribe(String channelName) {
		Channel c = new Channel(channelName);

		if (pusherSocket != null && isConnected()) {
			try {
				sendSubscribeMessage(c);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		channels.put(channelName, c);
		return c;
	}
	
	public Channel subscribe(String channelName, String authToken) {
		Channel c = new Channel(channelName);

		if (pusherSocket != null && isConnected()) {
			try {
				sendSubscribeMessage(c, authToken);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		channels.put(channelName, c);
		return c;
	}
	
	public Channel subscribe(String channelName, String authToken, int userId) {
		Channel c = new Channel(channelName);

		if (pusherSocket != null && isConnected()) {
			try {
				sendSubscribeMessage(c, authToken, userId);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		channels.put(channelName, c);
		return c;
	}

	public void unsubscribe(String channelName) {
		if (channels.containsKey(channelName)) {
			if (pusherSocket != null && isConnected()) {
				try {
					sendUnsubscribeMessage(channels.get(channelName));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			channels.remove(channelName);
		}
	}

	private void sendSubscribeMessage(Channel c) {
		JSONObject data = new JSONObject();
		c.send("pusher:subscribe", data);
	}
	
	private void sendSubscribeMessage(Channel c, String authToken) {
		JSONObject data = new JSONObject();
		try {
			data.put("auth", authToken);
		}
		catch(Exception ex) { }
		
		c.send("pusher:subscribe", data);
	}
	
	private void sendSubscribeMessage(Channel c, String authToken, int userId) {
		JSONObject data = new JSONObject();
		try {
			data.put("auth", authToken);
			data.put("channel_data", new JSONObject().put("user_id", userId));
		} catch(Exception ex) {
			
		}
		
		c.send("pusher:subscribe", data);
	}

	private void sendUnsubscribeMessage(Channel c) {
		JSONObject data = new JSONObject();
		c.send("pusher:unsubscribe", data);
	}
	
	private void dispatchChannelEvent(JSONObject jsonMessage, String event) {
		String channelName = jsonMessage.optString("channel", null);
		
		Channel channel = channels.get(channelName);
		if(channel != null) {
			ChannelListener channelListener = channel.channelEvents.get(event);
			
			if(channelListener != null)
				channelListener.onMessage(jsonMessage.toString());
		}
	}

	public void send(String event_name, JSONObject data) {
		JSONObject message = new JSONObject();

		try {
			message.put("event", event_name);
			message.put("data", data);
			pusherSocket.send(message.toString());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public class Channel {
		private String channelName;
		private final HashMap<String, ChannelListener> channelEvents;

		public Channel(String _name) {
			channelName = _name;
			channelEvents = new HashMap<String, ChannelListener>();
		}

		public void send(String eventName, JSONObject data) {
			JSONObject message = new JSONObject();

			try {
				data.put("channel", channelName);
				message.put("channel", channelName);
				message.put("event", eventName);
				message.put("data", data);
				pusherSocket.send(message.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void bind(String eventName, ChannelListener channelListener) {
			channelEvents.put(eventName, channelListener);
		}

		@Override
		public String toString() {
			return channelName;
		}
	}
}
