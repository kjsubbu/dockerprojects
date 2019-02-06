/*
package com.cavirin.arap.workflow.prescan;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.cavirin.arap.db.data.entities.configuration.Resource;
import com.cavirin.arap.db.data.entities.discovery.ResourceInstance;
import com.cavirin.arap.db.entities.enums.LastScanStatus;
import com.cavirin.arap.db.services.DiscoveryDBService;
import com.cavirin.arap.discovery.DevDiscovery;
import com.cavirin.arap.discovery.azure.AzureDiscovery;
import com.cavirin.arap.workflow.prescan.PreScanWorkFlowExecution.ResourceInfo;
import com.cavirin.arap.workflow.utility.RegistrationBean;
import com.cavirin.arap.workflow.utility.WorkFlowData;

import rx.Observable;
import rx.schedulers.Schedulers;

public class PreScanAzure {
	private static final Logger logger = LoggerFactory.getLogger(PreScanAzure.class);

	public static RegistrationBean registrationBean;
	public static ClassPathXmlApplicationContext context = null;
	//public static DevDiscovery dd = new DevDiscovery(context);
	public static DiscoveryDBService discoveryDBService = null;
	//(DiscoveryDBService)context.getBean("discoveryDBService");
	public static WorkFlowData workFlow = null;
	//(WorkFlowData)context.getBean("workFlowData");

	// Set of all Azure assets in a batch
	static Set<String> azurePreScanAssets = new HashSet<>();

	// All Azure Resources in the batch of Assets being pre-scanned
	static List<ResourceInfo> allAzureResources = new ArrayList<>();
	
	// Count down Azure resources until '0', for completion
	static final AtomicInteger azureResourceRemaining = new AtomicInteger();

	public static void getPingable(WorkFlowData workFlow, long worklogId, long assetId, String type) {
		logger.debug("Get pingable resources for worklogId={}, assetId={}, type={}", worklogId, assetId, type);
		PreScanWorkFlowExecution.assetResourceRemaining.put(assetId, new AtomicInteger(0));
		// reset discovered resource counter for asset
		String assetIdStr = Long.toString(assetId);
		
		// Run cloud discovery
		logger.debug("type is Azure");
		azurePreScanAssets.add(assetIdStr);
		AzureDiscovery azureDisc = new AzureDiscovery(context);
		logger.debug("Azure discovery - Start");
		Map<String, Object> hm = null;
		try {
			hm = azureDisc.azureDiscovery(assetIdStr);
		} catch (Exception  e) {
			logger.debug("Failed to parse Asset from Redis Prescan queue:" + assetId);
			return;
		}
		logger.debug("Cloud prescan output: " + hm.toString());

		// Get resources from DB
		List<Resource> resources = discoveryDBService.getResourcesByAssetTagId(assetId);
		logger.debug("Resources: " + resources);
		for (Resource r: resources) {
			ResourceInstance resInstance = r.getResourceInstance();
			if (resInstance != null) {
				LastScanStatus status = resInstance.getStatus();
				if (status==null || !status.equals(LastScanStatus.STOPPED)) {
					ResourceInfo resInfo = new ResourceInfo(worklogId, assetId, resInstance.getInstanceId());
					allAzureResources.add(resInfo);
					incrementAtomic(PreScanWorkFlowExecution.assetResourceRemaining, assetId);
				}
			}
		}
		logger.debug("getPingable Azure resources - end");
	
		PreScanWorkFlowExecution.totalAssetIpCount.put(assetId,
				new AtomicInteger(PreScanWorkFlowExecution.assetResourceRemaining.get(assetId).get()));
	}

	*/
/*
	 * Discover Azure batch of Resources
	 *//*

	public static void discoverAzure(WorkFlowData workFlow) { //, Set<String> assets) {
		logger.debug("ARAP-PREScan: Starting discovery for number of Azure cloud resources:" + allAzureResources.size());
	    
	    Observable<ResourceInfo> resInfoObjects = Observable.from(allAzureResources);
	    resInfoObjects.flatMap(resInfo -> Observable.just(resInfo)
	    	.subscribeOn(Schedulers.io())
	    	.map(x -> doDiscoveryAzureNow(x))).toList()
	    	.subscribe(val -> logger.debug("Azure Discovery completed for: "
                + val + " on "  + Thread.currentThread().getName()));
	}

	private static ResourceInfo doDiscoveryAzureNow(ResourceInfo resInfo) {
		long assetId = resInfo.getAssetTagId();
		String ipAddressOrInstanceId = resInfo.getIpAddressOrInstanceId();
    	int remaining = azureResourceRemaining.decrementAndGet();
    	decrementAtomic(PreScanWorkFlowExecution.assetResourceRemaining, assetId);

    	logger.debug("Starting Azure Resource:" + resInfo.toString() + " resource count=" + remaining);
    	
    	// Add to recentlyDiscovered
    	PreScanWorkFlowExecution.recentlyDiscoveredResources.put(ipAddressOrInstanceId, new Date());
		
    	Map<String, Object> result = null;
    	try {
    //		result = dd.basicDiscovery(0L, assetId, ipAddressOrInstanceId, context);
    	} catch (Exception e) {
    		logger.debug("Exception in BasicDiscovery for resource:" + resInfo.toString() + ", error " + e.getMessage(), e);
    		
    		// Remove from recentlyDiscovered
    		PreScanWorkFlowExecution.recentlyDiscoveredResources.remove(ipAddressOrInstanceId);
    		
    		return resInfo;
    	}
    	if (!result.get("status").equals("COMPLETED")) {
	    	logger.debug("Discovery failed for: " + resInfo.toString());	    
	    	
    		// Remove from recentlyDiscovered
	    	PreScanWorkFlowExecution.recentlyDiscoveredResources.remove(ipAddressOrInstanceId);
    		
    	} */
/*else {
	    	logger.debug("Discovery successful for: " + rp.toString());
	    	logger.debug("Doing Docker discovery for: " + rp.toString() + ", resourceId=" + result.get("resourceId"));
	    	try {
	    		DockerDiscovery dd = new DockerDiscovery();
	    		dd.containerDiscovery(Long.parseLong((String)result.get("resourceId")), context);
	    		
	    		// recentlyDiscoveredResources.put(rp.ipOrInstance, new Date());
	    	} catch (Exception e) {
	    		logger.debug("Error invoking Docker Discovery for resource:"  + rp.toString() + ", error " + e.getMessage());
	    	}
	    	// Add resource to assetgroups in redis
	    	Long rid = Long.parseLong((String)result.get("resourceId"));
	    	Resource r = discoveryService.getResourceById(rid);
			for(Assetgroup ag: r.getAssetGroups()) {
				logger.debug("Redis: deep discovered resource " + r.getId() + ", added to assetgroup " + ag.getId());
				//redisTemplate.boundSetOps("Assetgroup:"+ag.getId().toString()).add(r.getId().toString());
				workFlow.addSetElement("Assetgroup:"+ag.getId().toString(), rid.toString());
			}
    	}*//*

    	return resInfo;
	}
	

	// increment count in atomicInteger map
	static int incrementAtomic(Map<Long, AtomicInteger> map, Long assetId) {
		AtomicInteger ia = map.get(assetId);
		if (ia != null) {
			return ia.incrementAndGet();
		}
		return 0;
	}

	// decrement count in atomicInteger map
	static int decrementAtomic(Map<Long, AtomicInteger> map, Long assetId) {
		AtomicInteger ia = map.get(assetId);
		if (ia != null) {
			return ia.decrementAndGet();
		}
		return 0;
	}

	*/
/*
	 * Check if the resource was recently discovered (Fresh).
	 *//*

	public static boolean checkIfRecentDiscovered(String ipOrInstance) {
		*/
/*Date date = PreScanWorkFlowExecution.recentlyDiscoveredResources.get(ipOrInstance);
		if (date != null) {
			long timeOfLastDisco = date.getTime();
			if (Math.abs(timeOfLastDisco - System.currentTimeMillis()) < (PreScanWorkFlowExecution.freshnessPeriod * 1000L)) {
				return true;
			}
		}*//*

		return false;
	}
}
*/
