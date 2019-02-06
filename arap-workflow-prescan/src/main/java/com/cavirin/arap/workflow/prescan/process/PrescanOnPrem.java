package com.cavirin.arap.workflow.prescan.process;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.cavirin.arap.db.data.entities.configuration.AssetTag;
import com.cavirin.arap.db.entities.enums.AssetType;
import com.cavirin.arap.db.entities.enums.WorklogScanStatus;
import com.cavirin.arap.db.services.ConfigurationDBService;
import com.cavirin.arap.db.services.DiscoveryDBService;
import com.cavirin.arap.db.services.ResultsDBService;
import com.cavirin.arap.db.utlity.ResourceInfo;
import com.cavirin.arap.db.utlity.SystemConfig;
import com.cavirin.arap.discovery.AssetsSweep;
import com.cavirin.arap.discovery.DevDiscovery;
import com.cavirin.arap.discovery.DeviceInfoUtil;
import com.cavirin.arap.discovery.IpAddress;
import com.cavirin.arap.discovery.docker.DockerDiscovery;
import com.cavirin.arap.workflow.prescan.utils.PreScanWork;

import rx.Observable;
import rx.schedulers.Schedulers;

@Service
public class PrescanOnPrem {

	private static final String CONFIG_DISCOVERY_TIMEOUT = "com.cavirin.jovalpw.CONFIG_DISCOVERY_TIMEOUT";
	private static final String CONFIG_DISCOVERY_SCALE_TEST_MODE = "com.cavirin.jovalpw.CONFIG_DISCOVERY_SCALE_TEST_MODE";
	private static final String CONFIG_DISCOVERY_SCALE_TEST_CLONE_FACTOR = "com.cavirin.jovalpw.CONFIG_DISCOVERY_SCALE_TEST_CLONE_FACTOR";
	private static final long INCOMPLETE_DISCOVERY_PROCESS_INTERVAL = 30000L;

	private static final Logger logger = LoggerFactory.getLogger(PrescanOnPrem.class);

	@Autowired
	private DiscoveryDBService discoveryDBService;

	@Autowired
	private ConfigurationDBService configDBService;

	@Autowired
	private ResultsDBService resultsDBService;

	@Autowired
	private DevDiscovery devDiscovery;
	
	@Autowired
	private DockerDiscovery dockerDiscovery;

	@Autowired
	private ApplicationContext applicationContext;

	public void triggerOnPremPreScan(PreScanWork work, ExecutorService executor) {
		logger.debug("Get pingable resources for asset group: {}", work);
		long workLogId = work.getWorkLogId();
		long assetGroupId = work.getAssetGroupId();
		AssetType assetType = work.getAssetType();
		DeviceInfoUtil deviceInfoUtil = applicationContext.getBean(DeviceInfoUtil.class);
		AssetTag assetTag = configDBService.getAssetTagById(assetGroupId);
		List<ResourceInfo> liveResources = null;
		boolean cloudType = AssetType.isCloudType(assetType);
		// StartIP and END IP are null in case of Cherry-Picked Asset Group.
		boolean scaleTestMode = false;
		if (cloudType || (assetTag.getEndIp() == null && assetTag.getStartIp() == null)) {
			liveResources = getCloudLiveResources(workLogId, assetGroupId, assetType, cloudType);
			//in the case of clouds, cloud discovery sets the status to PRESCAN_COMPLETE
		} else { //On-prem
			scaleTestMode = getScaleTestMode();
			liveResources = getOnPremLiveResources(workLogId, assetGroupId, assetType, executor, scaleTestMode);
			logger.info("Set Prescan Completion for Asset Group id: {}, workLogId: {}" , assetGroupId, workLogId);
			resultsDBService.updateWorkLogScanStatus(workLogId, WorklogScanStatus.PRESCAN_COMPLETE);
		}

		discoverOnPremResources(assetGroupId, workLogId, liveResources, deviceInfoUtil, cloudType, executor, scaleTestMode);
		logger.info("Set Discovery Completion for Asset Group id: {}, workLogId: {}", assetGroupId, workLogId);
		resultsDBService.updateWorkLogScanStatus(workLogId, WorklogScanStatus.DISCOVERY_COMPLETE);
	}

	/*
	 * Collect live resources for an OnPrem asset.
	 */
	private List<ResourceInfo> getOnPremLiveResources(long workLogId, long assetGroupId, AssetType assetType, 
			ExecutorService executor, boolean scaleTestMode) {
		logger.debug("Get pingable resources for ONPREM asset group id {}", assetGroupId);
		List<String> ipAddresses = getOnPremAssetTagIps(assetGroupId, scaleTestMode);
		if (ipAddresses==null || ipAddresses.isEmpty()) {
			return null;
		}
		
		unmapResources(assetGroupId, workLogId, true, new HashSet<String>(ipAddresses));
		
		int onpremResources = ipAddresses.size();
		logger.debug("Found {} IP addresses for ONPREM asset group id {}", onpremResources, assetGroupId);
		ConcurrentHashMap<String, Boolean> pingableResources = new ConcurrentHashMap<>(onpremResources);
		CountDownLatch pingedResourcesCount = new CountDownLatch(onpremResources);
		Observable<String> obs = Observable.from(ipAddresses);
		obs.flatMap(ipAddress -> Observable.just(ipAddress)
				.subscribeOn(Schedulers.from(executor))
				.map(ip -> pingResource(ip, pingableResources, pingedResourcesCount)))
				.toList()
				.subscribe(val -> {});
		
		try { pingedResourcesCount.await(); } catch (InterruptedException e) {}

		List<ResourceInfo> liveResourceInfoList = new ArrayList<>(onpremResources);
		Set<String> downIpAddresses = new HashSet<String>(ipAddresses.size());
		int pingableResourcesCount = 0;
		for (String ip: ipAddresses) {
			Boolean pingable = pingableResources.get(ip);
			if (pingable!=null && pingable) {
				pingableResourcesCount++;
			} else {
				downIpAddresses.add(ip);
			}
			//try to discover even if the resource is not pingable
			ResourceInfo resInfo = new ResourceInfo(workLogId, assetGroupId, ip, assetType, pingable);
			liveResourceInfoList.add(resInfo);
		}

		logger.debug("Found {}/{} pingable resources for ONPREM asset group id {}", pingableResourcesCount, onpremResources, assetGroupId);
		if (!downIpAddresses.isEmpty()) {
			int inaccessible = discoveryDBService.setAssetGroupIpAddressesAccessibility(assetGroupId, downIpAddresses, false);
			logger.debug("Marked {} non-pingable IP addresses as inaccessible", inaccessible);
		}
		
		return liveResourceInfoList;
	}

	/**
	 * For the given asset group:
	 * 
	 * in the ONPREM case, unmaps the resource instances
	 * 		whose IP addresses are NOT included in the group's IP range
	 * 
	 * @param assetGroupId
	 * @param onprem
	 * @param ipAddresses
	 */
	private void unmapResources(long assetGroupId, long workLogId, boolean onprem, Set<String> ipAddresses) {
		if (onprem) {
			if (ipAddresses!=null && !ipAddresses.isEmpty()) {
				try {
					int resources = configDBService.detachOtherInstanceResourcesFromAssetGroup(assetGroupId, ipAddresses);
					logger.debug("Detached {} ONPREM instance resources from asset group: {}", resources, assetGroupId);
				} catch (Exception e) {
				}
			}
		}
	}

	private List<ResourceInfo> getCloudLiveResources(long workLogId, long assetGroupId, AssetType assetType, boolean cloudType) {
		List<ResourceInfo> resources = (cloudType)?
				discoveryDBService.getActiveCloudResources(assetGroupId)
				: discoveryDBService.getAssetGroupResources(assetGroupId);

		logger.debug("Found {} active resources on cloud for worklogid {}, asset group id {}", 
				(resources==null)? 0 : resources.size(), workLogId, assetGroupId);
		if (resources==null || resources.isEmpty())
			return null;

		List<ResourceInfo> cloudResources = new ArrayList<ResourceInfo>(resources.size());
		for (ResourceInfo resource: resources) {
			resource.setWorkLogId(workLogId);
			resource.setAssetTagId(assetGroupId);
			resource.setAssetType(assetType);
			resource.setPingable(false);
			resource.setRunning(true);
			cloudResources.add(resource);
		}

		return cloudResources;
	}

	/*
	 * Do deep discovery of resources (On-prem/Cloud)
	 */
	private void discoverOnPremResources(long assetGroupId, long workLogId, List<ResourceInfo> liveResources, 
			DeviceInfoUtil deviceInfoUtil, boolean cloudType, ExecutorService executor, boolean scaleTestMode) {
		if (liveResources==null || liveResources.isEmpty())
			return;

		ConcurrentHashMap<String, Integer> uuidsCountsMap = scaleTestMode? new ConcurrentHashMap<String, Integer>() : null; 
		
		int resourceCount = liveResources.size();
		logger.debug("Starting the discovery for OnPrem Resources:" + resourceCount);
		CountDownLatch latch = new CountDownLatch(resourceCount);
		ConcurrentHashMap<ResourceInfo, Long> discoveriesStartTimeMap = new ConcurrentHashMap<ResourceInfo, Long>();
		
		/*
		Observable<ResourceInfo> pairs = Observable.from(liveResources);
		pairs.flatMap(pair -> Observable.just(pair)
				.subscribeOn(Schedulers.from(executor))
				.map(x ->  discoverOnPremResource(x, latch, deviceInfoUtil)))
				.toList()
				.subscribe(val ->
						logger.debug("OnPrem discovery completed for assetGroupId: {}, workLogId: {} on {}",
								assetGroupId, workLogId, Thread.currentThread().getName()));
		*/
		
		launchIncompleteDiscoveriesMonitor(latch, discoveriesStartTimeMap);

		while (!liveResources.isEmpty()) {
			ResourceInfo resInfo = liveResources.remove(0);
			Runnable runnable = new Runnable() {
				public void run() {
					discoverOnPremResource(resInfo, latch, deviceInfoUtil, discoveriesStartTimeMap, uuidsCountsMap);
				}
			};
			executor.execute(runnable);
		}

		logger.info("Waiting for the discovery of {} resources to finish", latch.getCount());
		try { latch.await(); } catch (InterruptedException e) {}

		logger.info("Discovery finished for all resources. Post-processing starts...");
		try {
			//mark obsolete/inactive instances IP addresses as inaccessible
			int inactiveInstances = discoveryDBService.setAssetGroupInactiveIpAddressesAccessibility(assetGroupId, workLogId);
			logger.info("Marked {} obsolete/inactive instances as inaccessible for asset group: {}", inactiveInstances, assetGroupId);

			int failedResources = discoveryDBService.removeOldFailedResources(assetGroupId, workLogId);
			logger.info("Deleted {} OLD failed resources from asset group: {}", failedResources, assetGroupId);
			
			//remove the IP addresses of accessible resources for cloud envt
			if (cloudType) {
				int removed = discoveryDBService.removeFailedResourceEntriesOfAccessibleResources(assetGroupId);
				logger.info("Deleted {} IP addresses of accessible resources from failed-resources table", removed);
			}
		} catch (Exception e) {
			logger.error("Exception occurred in post-processing of Discovery: {}", e.getMessage(), e);
		}
	}

	private List<String> getOnPremAssetTagIps(Long assetId, boolean scaleTestMode) {
		List<String> ipAddresses = new ArrayList<>();

		try {
			AssetTag assetTag = configDBService.getAssetTagById(assetId);
			ipAddresses = getIPAddressList(assetTag.getStartIp(), assetTag.getEndIp(), scaleTestMode);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ipAddresses;
	}

	private List<String> getIPAddressList(String startIp, String endIp, boolean scaleTestMode) {
		List<String> ipAddresses = null;
		try {
			ipAddresses = new ArrayList<>();
			logger.debug("Start IP --> " + startIp);
			IpAddress startIpAddr = new IpAddress(startIp);
			logger.debug("End IP --> " + endIp);
			IpAddress endIpAddr = new IpAddress(endIp);

			while (!startIpAddr.toString().equals(endIpAddr.nextIPAddress().toString())) {
				ipAddresses.add(startIpAddr.toString());
				startIpAddr = startIpAddr.nextIPAddress();
			}

		} catch (Exception ex) {
			logger.error("There is problem while generating the IP addresses from IP Range " , ex.getMessage());
		}

		if (scaleTestMode) {
			int cloneFactor = getScaleTestCloneFactor();
			List<String> ipAddressesCopy = new ArrayList<String>(ipAddresses);
			for (int i=0; i<cloneFactor-1; i++)
				ipAddresses.addAll(ipAddressesCopy);
		}

		return ipAddresses;
	}

	private boolean pingResource(String ipAddress, ConcurrentHashMap<String, Boolean> pingableResources, CountDownLatch pingedResourcesCount) {
		logger.info("Ping resource:" + ipAddress.toString());
		Boolean live = AssetsSweep.icmpPing(ipAddress, 1000L);
		if (live) {
			logger.info("Ping successful for resource:" + ipAddress);
		}
		pingableResources.put(ipAddress, live);
		pingedResourcesCount.countDown();
		return live;
	}

	private ResourceInfo discoverOnPremResource(ResourceInfo resInfo, CountDownLatch latch, DeviceInfoUtil deviceInfoUtil, 
			ConcurrentHashMap<ResourceInfo,Long> discoveriesStartTimeMap, ConcurrentHashMap<String, Integer> uuidsCountsMap) {
		discoveriesStartTimeMap.put(resInfo, System.currentTimeMillis());

		logger.info("Doing deep discovery for {}" , resInfo.toString());
		
		Map<String, Object> result = null;
		try {
			result = devDiscovery.basicDiscovery(resInfo, deviceInfoUtil, uuidsCountsMap);
		} catch (Exception e) {
			logger.error("Exception in BasicDiscovery for resource: {}, error: {}", resInfo.toString(), e.getMessage(), e);
			return resInfo;
		}
		if (result == null
				|| result.get(DevDiscovery.DISCOVERY_STATUS) != DevDiscovery.DISCOVERY_STATUS_COMPLETED) {
			logger.debug("Discovery failed for: {}", resInfo.toString());
		} else {
			logger.debug("Discovery successful for: {}", resInfo.toString());
			long resourceId = (Long) result.get(DevDiscovery.RESOURCE_ID);
			Boolean dockerServiceRunning = (Boolean) result.get(DevDiscovery.DOCKER_SERVICE_RUNNING);
			if (dockerServiceRunning!=null && dockerServiceRunning) {
				logger.debug("Doing Docker discovery for resource: {}", resourceId);
				try {
					dockerDiscovery.containerDiscovery(resInfo.getWorkLogId(), resInfo.getAssetTagId(), resourceId, true, deviceInfoUtil);
				} catch (Exception e) {
					logger.error("Error invoking Docker Discovery for resource: {}, error {}", resInfo.toString(), e.getMessage(), e);
				}
			}
		}
		logger.debug("Ending OnPrem Resource: {}", resInfo.toString());

		synchronized(discoveriesStartTimeMap) {
			if (discoveriesStartTimeMap.get(resInfo) != null) {
				latch.countDown();
				discoveriesStartTimeMap.remove(resInfo);
			}
		}
		
		logger.info("Discovery completed for {}. Remaining discoveries: {}", resInfo.getIpAddress(), latch.getCount());
		return resInfo;
	}

	private void launchIncompleteDiscoveriesMonitor(CountDownLatch latch, ConcurrentHashMap<ResourceInfo,Long> discoveriesStartTimeMap) {
		try {
			Runnable monitor = createMonitoringTask(latch, discoveriesStartTimeMap);
			new Thread(monitor).start();
		} catch (Exception e) {
			logger.error("Exception occurred: ", e);
		}
	}

	private Runnable createMonitoringTask(final CountDownLatch latch, final ConcurrentHashMap<ResourceInfo,Long> discoveriesStartTimeMap) {
		long discoveryTimeout = getDiscoveryTimeout();
		
		Runnable monitor = new Runnable() {

			@Override
			public void run() {
				logger.debug("Incomplete discoveries monitor thread started...");

				while (latch.getCount() > 0) {
					try {
						Thread.sleep(INCOMPLETE_DISCOVERY_PROCESS_INTERVAL);
					} catch (InterruptedException e) {}

					try {
						logger.debug("Checking for timed-out discoveries...");
						discoveriesStartTimeMap.forEach((resInfo, startTime) -> {
							long endTime = startTime + discoveryTimeout;
							logger.debug("resource discovery: {}, startTime: {}, endTime: {}", resInfo,
									new Timestamp(startTime), new Timestamp(endTime));
							boolean timedOut = false;
							if (System.currentTimeMillis() > endTime) {
								synchronized(discoveriesStartTimeMap) {
									if (discoveriesStartTimeMap.get(resInfo) != null) {
										logger.debug("Found timed-out resource discovery: {}", resInfo);
										logger.debug("Removing timed-out/completed resource scan: {}", resInfo);

										discoveriesStartTimeMap.remove(resInfo);
										latch.countDown();
										timedOut = true;
										logger.info("Remaining discoveries: {} after processing timed-out/completed resource discovery: {}",
												latch.getCount(), resInfo);
									}
								}
								if (timedOut) {
									devDiscovery.processInaccessibleResource(resInfo, false);
								}
							}
						});
					} catch (Exception e) {
						logger.error("Exception: {}", e.getMessage(), e);
					}
				}

				logger.debug("Incomplete discoveries monitor thread ended.");
			}

		};
		return monitor;
	}

	private long getDiscoveryTimeout() {
		String discoveryTimeout = SystemConfig.get(CONFIG_DISCOVERY_TIMEOUT);
		logger.debug("Read Configuration property CONFIG_DISCOVERY_TIMEOUT = {}", discoveryTimeout);

		int discoveryTimeoutMinutes = (discoveryTimeout == null)? 5 : Integer.parseInt(discoveryTimeout);
		long discoveryTimeoutMilliseconds = discoveryTimeoutMinutes * 60000;
		return discoveryTimeoutMilliseconds;
	}

	private boolean getScaleTestMode() {
		String discoveryTimeout = SystemConfig.get(CONFIG_DISCOVERY_SCALE_TEST_MODE);
		logger.debug("Read Configuration property CONFIG_DISCOVERY_SCALE_TEST_MODE = {}", discoveryTimeout);

		return (discoveryTimeout == null)? false : Boolean.parseBoolean(discoveryTimeout);
	}

	private int getScaleTestCloneFactor() {
		String cloneFactorStr = SystemConfig.get(CONFIG_DISCOVERY_SCALE_TEST_CLONE_FACTOR);
		logger.debug("Read Configuration property CONFIG_DISCOVERY_SCALE_TEST_CLONE_FACTOR = {}", cloneFactorStr);

		int cloneFactor = 1;

		if (cloneFactorStr == null) 
			return cloneFactor;
		
		try {
			cloneFactor = Integer.parseInt(cloneFactorStr);
			cloneFactor = (cloneFactor < 1)? 1 : cloneFactor;
		} catch (Exception e) {
			logger.error("Exception occurred while parsing Scale-test clone factor: {}", cloneFactorStr, e);
		}
		
		return cloneFactor;
	}

}
