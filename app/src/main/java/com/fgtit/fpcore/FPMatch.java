package com.fgtit.fpcore;

import com.fgtit.data.Conversions;

public class FPMatch {

	private static FPMatch mMatch=null;
	
	public static FPMatch getInstance(){
		if(mMatch==null){
			mMatch=new FPMatch();
		}
		return mMatch;
	}

	public native int InitMatch( int inittype, String initcode);
	public native int MatchTemplate( byte[] piFeatureA, byte[] piFeatureB);
	
	public void ToStd(byte[] input,byte[] output){
		switch(Conversions.getInstance().GetDataType(input)){
		case 1:{
				System.arraycopy(input, 0, output, 0, 512);
			}
		case 2:{
				Conversions.getInstance().IsoToStd(1,input,output);
			}
		case 3:{
				Conversions.getInstance().IsoToStd(2,input,output);
			}
		}
	}


	public int MatchFingerData(byte[] piFeatureA, byte[] piFeatureB){
		int at= Conversions.getInstance().GetDataType(piFeatureA);
		int bt= Conversions.getInstance().GetDataType(piFeatureB);
		if((at==1)&&(bt==1)){
			if(piFeatureA.length>=512){
				byte tmp[]=new byte[256];
				System.arraycopy(piFeatureA, 0, tmp, 0, 256);
				int sc1=MatchTemplate(tmp,piFeatureB);
				System.arraycopy(piFeatureA, 256, tmp, 0, 256);
				int sc2=MatchTemplate(tmp,piFeatureB);
				if(sc1>sc2)
					return sc1;
				else
					return sc2;
			}else{
				return MatchTemplate(piFeatureA,piFeatureB);
			}
		}else{
			byte adat[]=new byte[512];
			byte bdat[]=new byte[512];
			ToStd(piFeatureA,adat);
			ToStd(piFeatureB,bdat);
			return MatchTemplate(adat,bdat);
		}
	}
	
	static {
		System.loadLibrary("fgtitalg");
		System.loadLibrary("fpcore");
	}
}
