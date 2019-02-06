package com.cavirin.arap.workflow.prescan.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cavirin.arap.db.entities.enums.AssetType;

public class PreScanWork {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
    private long workLogId;

    private  long assetGroupId;

    private AssetType assetType;

    private PreScanWork(String work){
        String[] parts = work.split(":");
        this.workLogId = Long.parseLong(parts[0]);
        this.assetGroupId = Long.parseLong(parts[1]);
        this.assetType = AssetType.ONPREM;
        try {
        	this.assetType = AssetType.valueOf(parts[2]);
        } catch (Exception e) {
        	logger.debug("Exception: {}", e.getMessage(), e);
        }
    }

    public static PreScanWork processWork(String work){
        return new PreScanWork(work);
    }

    public long getAssetGroupId() {
        return assetGroupId;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public long getWorkLogId() {
        return workLogId;
    }

    public String toString() {
    	StringBuffer sb = new StringBuffer();
    	sb.append("[ ");
    	sb.append("workLogId: ");
    	sb.append(workLogId);
    	sb.append(", assetGroupId: ");
    	sb.append(assetGroupId);
    	sb.append(", assetType: ");
    	sb.append(assetType);
    	sb.append(" ]");
    	return sb.toString();
    }

}
