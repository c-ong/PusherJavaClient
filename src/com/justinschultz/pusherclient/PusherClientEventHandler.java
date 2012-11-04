package com.justinschultz.pusherclient;

public interface PusherClientEventHandler {
	public void onMessage( String message );
	public void onClose( int code, String reason, boolean remote );
	public void onError( Exception ex );
}
