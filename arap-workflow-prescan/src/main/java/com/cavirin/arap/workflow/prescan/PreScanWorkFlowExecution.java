package com.cavirin.arap.workflow.prescan;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.cavirin.arap.db.data.content.OsName;
import com.cavirin.arap.db.data.entities.configuration.AssetTag;
import com.cavirin.arap.db.data.entities.configuration.Resource;
import com.cavirin.arap.db.data.entities.discovery.AssetGroupFailedResource;
import com.cavirin.arap.db.data.entities.discovery.ResourceInstanceIpAddress;
import com.cavirin.arap.db.data.entities.discovery.ResourceInstanceIpAddress.PrimaryKey;
import com.cavirin.arap.db.entities.enums.AssetType;
import com.cavirin.arap.db.services.ConfigurationDBService;
import com.cavirin.arap.db.services.ConfigurationService;
import com.cavirin.arap.db.services.ContentDBService;
import com.cavirin.arap.db.services.DiscoveryDBService;
import com.cavirin.arap.db.utlity.OSUtility;
import com.cavirin.arap.db.utlity.ResourceInfo;
import com.cavirin.arap.discovery.DevDiscovery;
import com.cavirin.arap.discovery.DeviceInfoUtil;
import com.cavirin.arap.discovery.docker.DockerDiscovery;
import com.cavirin.arap.workflow.prescan.process.PrescanOnPrem;
import com.cavirin.arap.workflow.prescan.utils.PreScanWork;
import com.cavirin.arap.workflow.utility.WorkFlowConstants;
import com.cavirin.arap.workflow.utility.WorkFlowData;

@EnableAsync
@EnableScheduling
@ImportResource("file:spring-config.xml")
@SpringBootApplication(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
public class PreScanWorkFlowExecution {

	private static final Logger logger = LoggerFactory.getLogger(PreScanWorkFlowExecution.class);

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	ConfigurationService configService;

	@Autowired
	ConfigurationDBService configDBService;

	@Autowired
	DiscoveryDBService discoveryDBService;

	@Autowired
	ContentDBService contentDBService;

	@Autowired @Qualifier("workFlowLocal")
	WorkFlowData workFlow;

	@Autowired
	private DevDiscovery devDiscovery;

	@Autowired
	private DockerDiscovery dockerDiscovery;

	@Autowired
	PrescanOnPrem onpremPrescan;

	// JVM identifier for multi-jvm system
	static int jvm=1;

	// Number of JVMs.
	static int jvmCount=1;


	/*
	 * OnDemand thread: Refresh
	 */

	@Scheduled(fixedRate=10000)
	public void onDemandResourceDiscovery () {
		Map<String, Object> result = null;
		Set<Object> resources = null;
		try {
			resources = workFlow.getSetMembers(WorkFlowConstants.ON_DEMAND_RESOURCE_REFRESH);
			workFlow.delKey(WorkFlowConstants.ON_DEMAND_RESOURCE_REFRESH);
		}catch (RedisConnectionFailureException e){
			logger.error("Exception occurred while triggering scan to redis",e);
		}
		if (resources!=null && !resources.isEmpty()) {
			DeviceInfoUtil deviceInfoUtil = applicationContext.getBean(DeviceInfoUtil.class);
			for (Object r : resources) {
				String ipAddress = (String) r;
				if (ipAddress == null || ipAddress.isEmpty()) return;
				logger.debug("Running OnDemand Resource Discovery for IP address = {}", ipAddress);
				List<AssetGroupFailedResource> assetGroupFailedResources = discoveryDBService.findAssetGroupFailedResource(ipAddress);
				List<ResourceInstanceIpAddress> resourceInstanceIpAddresses = discoveryDBService.findInstancesByIpAddress(ipAddress);
				try {
					Set<ResourceInfo> resourceInfos = generateResourceInfo(resourceInstanceIpAddresses, assetGroupFailedResources);
					if (resourceInfos == null || resourceInfos.isEmpty()) return;
					for(ResourceInfo resourceInfo: resourceInfos){
						result = devDiscovery.basicDiscovery(resourceInfo, deviceInfoUtil, null);
						if (result == null
								|| result.get(DevDiscovery.DISCOVERY_STATUS) != DevDiscovery.DISCOVERY_STATUS_COMPLETED) {
							logger.debug("Discovery failed for: " + ipAddress);
						} else {
							logger.debug("Discovery successful for: " + ipAddress);
							long resourceId = (Long) result.get(DevDiscovery.RESOURCE_ID);
							Boolean dockerServiceRunning = (Boolean) result.get(DevDiscovery.DOCKER_SERVICE_RUNNING);
							if (dockerServiceRunning!=null && dockerServiceRunning) {
								logger.debug("Doing Docker discovery for: " + ipAddress + ", resourceId=" + resourceId);
								try {
									dockerDiscovery.containerDiscovery(0L, 0, resourceId, true, deviceInfoUtil);
								} catch (Exception e) {
									logger.error("Error invoking Docker Discovery for resource: {}, error {}", ipAddress, e.getMessage(), e);
								}
							}
						}
					}
				} catch (Exception e) {
					logger.error("Error invoking Docker Discovery for resource:", e.getMessage());
				}
			}
		}
	}


	public static void main(String...args) {
		SpringApplication.run(PreScanWorkFlowExecution.class, args);
	}
	/*
	 * Workflow main function.
	 */

	@EventListener
	public void onApplicationEvent(ContextRefreshedEvent event) {
		List<OsName> osNames = contentDBService.getAllOsNames();
		OSUtility.initializeMap(osNames);
		jvm = Integer.parseInt(System.getProperty("jvm"));
		logger.debug("Starting Prescan WOrkflow, jvm=" +jvm);
		jvmCount = Integer.parseInt(System.getProperty("jvmCount"));
		logger.debug("jvm id=" + jvm + ", jvm Count=" + jvmCount);
	}

	@Scheduled(fixedRate=10000)
	public void triggerPreScan () {
		if (configService.isEmptyWorkQueue("preScanTrigger")) return;
		String worklogAssetIdType = configService.getWorkFromQueue("preScanTrigger");
		logger.info("Obtained preScanTrigger from Redis. worklogAssetIdType: {}", worklogAssetIdType);
		ExecutorService executor = null;
		try {
			PreScanWork preScanWork = PreScanWork.processWork(worklogAssetIdType);
			logger.info("Found Prescan WorkLogAssetType: {}", preScanWork);
			if (preScanWork.getAssetType() != null) {
				executor = createExecutor();
				onpremPrescan.triggerOnPremPreScan(preScanWork, executor);
			}
		} catch (Exception e) {
			logger.error("Caught error in main loop, will continue:", e);
		} finally {
			if (executor != null)
				executor.shutdown();
		}
	}

	private ExecutorService createExecutor() {
        String numThreads = System.getProperty("threads", "50");
        int nthreads = Integer.parseInt(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(nthreads);
        // Number of threads (configured using -Dthreads=nnn
        logger.info("Initialized thread pool with {} threads", nthreads);
        return executor;
	}

	private Set<ResourceInfo> generateResourceInfo(List<ResourceInstanceIpAddress> resourceInstanceIpAddresses, 
			List<AssetGroupFailedResource> assetGroupFailedResources) {
		Set<ResourceInfo> resourceInfos = new HashSet<>();
		AssetType assetType = null;
		AssetTag assetTag = null;
		if(null!=resourceInstanceIpAddresses && !resourceInstanceIpAddresses.isEmpty()){
			Resource resource = null;
			for(ResourceInstanceIpAddress resourceInstanceIpAddress: resourceInstanceIpAddresses){
				resource = resourceInstanceIpAddress.getResource();
				assetType = AssetType.valueOf(resource.getEnvName());
				PrimaryKey instanceIpId = resourceInstanceIpAddress.getId();
				resourceInfos.add(new ResourceInfo(0, 0, instanceIpId.getIpAddress(), assetType, false, instanceIpId.getResourceId(), true));
			}
		}
		if(null!=assetGroupFailedResources && !assetGroupFailedResources.isEmpty()){
			for(AssetGroupFailedResource assetGroupFailedResouces: assetGroupFailedResources){
				assetTag = configDBService.getAssetTagById(assetGroupFailedResouces.getAssetTagId());
				if(assetTag == null) continue;
				assetType = assetTag.getAssetTagType();
				resourceInfos.add(new ResourceInfo(0, assetGroupFailedResouces.getAssetTagId(), assetGroupFailedResouces.getIpAddress(), assetType, false, 0, true));
			}
		}
		return resourceInfos;
	}
}
