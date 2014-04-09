package com.vanderbie.bumpy;

import nl.sense_os.platform.SenseApplication;


public class BumpyApplication extends SenseApplication{
	private static BumpyApplication singleton;
	
	
	
	public BumpyApplication getInstance(){
		return singleton;
	}
	
	  @Override
	  public void onCreate() {
		  super.onCreate();
		  singleton = this;
	  }
	
	
}
