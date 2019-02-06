package com.cavirin.arap.workflow.joval;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cavirin.arap.db.data.content.OsName;
import com.cavirin.arap.db.entities.enums.WorklogScanStatus;
import com.cavirin.arap.db.services.ResultsDBService;
import com.cavirin.arap.db.utlity.OSUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.cavirin.arap.db.services.ConfigurationService;
import com.cavirin.arap.db.services.ContentDBService;
import com.cavirin.arap.workflow.scan.AssetGroupPolicyPackExecution;
import com.cavirin.arap.workflow.utility.WorkFlowConstants;

import redis.clients.jedis.exceptions.JedisConnectionException;

public class JovalWorkFlowExecution {

	private static final Logger logger = LoggerFactory.getLogger(JovalWorkFlowExecution.class);

	private static ExecutorService executor = null;
	private static ClassPathXmlApplicationContext context = null;
	private static ConfigurationService configService = null;
	private static ResultsDBService resultsService = null;
	private static ContentDBService contentService = null;

	public static void main(String[] args) {

		initialize();

		logger.debug("ARAP-JOVAL-WORKFLOW: Going to trigger the workflow");
		while (true) {
			try {
				// Clear stop queue, at every process loop.
				configService.clearWorkQueue(WorkFlowConstants.STOP_JOVAL_SCAN);

				processDockerTriggers();

				processCoreOsTriggers();

				processJovalTriggers();

			} catch (JedisConnectionException je)  {
				logger.error("Failed to get Redis Connection, wait for {} ms.", WorkFlowConstants.JEDIS_FAILURE_WAIT_TIME, je);
				try { Thread.sleep(WorkFlowConstants.JEDIS_FAILURE_WAIT_TIME); } catch (InterruptedException e) {}
				continue;
			} catch (Exception e) {
				logger.error("ERROR: ", e);
			}

			// Add 1 sec sleep to prevent busy loop
			try { Thread.sleep(1000); } catch (InterruptedException ie) {}
		}
	}

	private static void processJovalTriggers() {
		processAssetGroupTypeTriggers(WorkFlowConstants.JOVAL_ASSET_GROUP, WorkFlowConstants.ONPREM);
	}

	private static void processDockerTriggers() {
		processAssetGroupTypeTriggers(WorkFlowConstants.DOCKER_ASSET_GROUP, WorkFlowConstants.DOCKER);
	}

	private static void processCoreOsTriggers() {
		processAssetGroupTypeTriggers(WorkFlowConstants.COREOS_ASSET_GROUP, WorkFlowConstants.COREOS);
	}

	private static void processAssetGroupTypeTriggers(String assetGroupType, String assetType) {
		while (!configService.isEmptyWorkQueue(assetGroupType)) {
			String numThreads = System.getProperty("threads", "20");
			int nthreads = Integer.parseInt(numThreads);
			logger.debug("Joval workflow: using " + nthreads + " threads");
			executor = Executors.newWorkStealingPool(nthreads);
			final String workLogAssetGroupId = configService.getWorkFromQueue(assetGroupType);
			boolean isPatchesScan = false;
			AssetGroupPolicyPackExecution assetGroupToAsset = new AssetGroupPolicyPackExecution(context,
					executor, workLogAssetGroupId, assetType, isPatchesScan);
			Future future = executor.submit(assetGroupToAsset);
			try {
				future.get();
			} catch (InterruptedException|ExecutionException e) {
				logger.error("Exception occurred while triggering the joval scan",e);
			}finally {
				if(null!=executor)
					executor.shutdown();
			}
		}
	}

	private static void initialize() {
		try {
			context = new ClassPathXmlApplicationContext("spring-config.xml");
			configService = (ConfigurationService)context.getBean("configService");
			resultsService = (ResultsDBService)context.getBean("resultsDBService");
            resultsService.updateAllRunningJovalPolicyPacks(WorklogScanStatus.ERROR);
			contentService = (ContentDBService) context.getBean("contentDBService");

			List<OsName> osNames = contentService.getAllOsNames();
			OSUtility.initializeMap(osNames);
		} catch (Exception e) {
			logger.debug("Could not initialize Joval workflow, exiting..", e);
			System.exit(-1);
		}
	}

}
