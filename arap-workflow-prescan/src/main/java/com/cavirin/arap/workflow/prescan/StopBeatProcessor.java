/*
package com.cavirin.arap.workflow.prescan;

import com.cavirin.arap.db.entities.configuration.Credential;
import com.cavirin.arap.db.entities.discovery.Resource;
import com.cavirin.arap.db.entities.monitoring.MonitoredSource;
import com.cavirin.arap.db.utlity.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cavirin.arap.workflow.prescan.PreScanWorkFlowExecution.*;

@Service
public class StopBeatProcessor {

    public static final String DISABLE = "disable";
    private static Logger logger = LoggerFactory.getLogger(StopBeatProcessor.class);

    private ExecutorService executorService;

    public StopBeatProcessor() {
        init();
    }

    public void checkAndProcessStopBeat() {
        logger.debug("method=checkAndProcessStopBeat at={}", new Date());
        //TODO: to fix this with new config server

        List<MonitoredSource> stopBeats = null;

        if (stopBeats == null || stopBeats.size() == 0) {
            logger.debug("No StopBeats enabled");
            return;
        }
        stopBeats.forEach(monitoredSource -> {
            Boolean deleteFlag = monitoredSource.getDeleteFlag();
            Boolean container = monitoredSource.getContainers();
            JSONObject stopbeat = monitoredSource.getStopbeat();
            Long id = monitoredSource.getMonitorAssetGroup().getId();
            if (deleteFlag && Boolean.TRUE.equals(container)) {
                executeAsyncStopBeat(monitoredSource, id);
            }
            logger.debug("JSON for stopbeat = {}", stopbeat.toJSONString());
            String containerEnableDisable = (String) stopbeat.get("container");
            if (Boolean.FALSE.equals(container) && DISABLE.equalsIgnoreCase(containerEnableDisable)) {
                executeAsyncStopBeat(monitoredSource, id);
            }
        });
    }

    private void executeAsyncStopBeat(MonitoredSource monitoredSource, Long id) {
        executorService.execute(() -> {
            stopBeat(id);
            monitoredSource.setStopbeat(new JSONObject());
            monitoredSource.setContainers(false);
            //TODO: to fix this with new config server

            //monitoringService.updateMonitoredSource(monitoredSource.getId(), monitoredSource);
        });
    }

    // OS -> credentialId -> IPs
    // Map<String, Map<Long, Set<String>>> ansibleMap = new HashMap<>();


    private void stopBeat(Long monitorDisabledAssetGroupID) {
        try {
            logger.debug("StopBeat in Progress id={}", monitorDisabledAssetGroupID);
            Map<String, Map<Long, Set<String>>>  ansibleMap = new HashMap<>();
            Set<Resource> disableResources = new HashSet<Resource>();

           // disableResources.addAll(discoveryService.getResourcesByAssetGroupId(monitorDisabledAssetGroupID).stream().collect(Collectors.toSet()));

            logger.debug("Resources.... {}", disableResources.toString());
            //Get all the assetGroups which are monitored
            //TODO: to fix this with new config server

            Set<Long> monitoredAssetGroup = null;
            logger.debug("Monitored AssetGroup ....{}", monitoredAssetGroup.toString());
            //remove the assetGroup which we need to disable
            monitoredAssetGroup.remove(monitorDisabledAssetGroupID);

            logger.debug("Monitored AssetGroup after removing asssetGroup.....{}", monitoredAssetGroup.toString());

            Set<Long> resources = new HashSet<Long>();
            //Get all the resources for monitoredGroups
            for (long assetGroupId : monitoredAssetGroup) {
               // resources.addAll(discoveryService.getResourcesByAssetGroupId(assetGroupId).stream().map(t -> t.getId()).collect(Collectors.toSet()));
                logger.debug("List of resources which are monitored.....{}", resources.toString());
            }


            logger.debug("List of resources which are going to considerd for disabling monitoring {}", disableResources.toString());

            for (Resource r : disableResources) {
                logger.debug("Evaluating resource for disabling monitoring ....{}", r.getId());
                if (!r.isAccessible()) {
                    // Skip resource
                    logger.debug("Resources is not assesible ....{}", r.toString());
                    continue;
                } else if (resources.contains(r.getId())) {
                    // Skip resource
                    logger.debug("Resource is monitored in another group ....{}", r.getId());
                    continue;
                }
                String osname = r.getOsName();
                String[] oslist = StringUtils.split(osname);
                String os = oslist[0];
                if (os.equals("Windows")) {
                    // Skip monitoring
                    continue;
                }
                if (os.equals("Red")) {
                    os = "Red Hat";
                }
                if (os.equals("Amazon")) {
                    os = "Amazon Linux";
                }
                // Ubuntu, Debian  are fine
                String ip = r.getIpaddress();
                String str = r.getValidCredentialSet();
                Set<Long> validCredIds = ArrayUtils.fromStringLong(str);
                Long validCredId = validCredIds.iterator().next();

                // Add to the Ansible inventory
                putAnsibleIP(os, validCredId, ip, ansibleMap);
            }
            // Dump Ansible Inventory
            dumpAnsibleInventory(ansibleMap);
        } catch (Exception e) {
            logger.error("Exception ...." + e);
        }
    }

    // Add a single IP to (OS, Credential)
    private void putAnsibleIP(String os, Long credentialId, String ip, Map<String, Map<Long, Set<String>>> ansibleMap) {
        logger.debug("Adding to Ansible os={} , cred={} , ip={}", os, credentialId, ip);
        Map<Long, Set<String>> map = ansibleMap.get(os);
        if (map == null) {
            // Create and add Map for the OS
            map = new HashMap<>();
            ansibleMap.put(os, map);
        }
        Set<String> ips = map.get(credentialId);
        if (ips == null) {
            ips = new HashSet<>();
            map.put(credentialId, ips);
        }
        ips.add(ip);
    }

    private void dumpAnsibleInventory(Map<String, Map<Long, Set<String>>> ansibleMap) {
        logger.debug("Dumping Ansible Inventory to YML file");
        if (ansibleMap.isEmpty()) {
            logger.debug("Ansible map is empty. Dont write ansible file.");
            return;
        }
		*/
/*Calendar nowMinus3 = Calendar.getInstance();
		nowMinus3.add(Calendar.MINUTE, -3);
		if ((lastAnsibleDump != null) && lastAnsibleDump.after(nowMinus3.getTime())) {
			logger.debug("Last Ansible dump was < 3 minutes ago.");
			return;
		}*//*

        logger.debug("Creating Ansible Inventory Dump file");
        // Create Ansible dump file
        Date date = new Date();
        PrintWriter writer = null;
        String path = "/var/log/cavirin/ansible-stopBeat/";
        String filename = path + date.getTime() + ".yml";

        // Create ansible directory if does not exist.
        File directory = new File(String.valueOf(path));
        if (!directory.exists()) {
            logger.debug("Creating /var/log/cavirin/ansible-stopBeat/ directory");
            directory.mkdir();
        }
        logger.debug("Dumping Ansible Inventory to file:" + filename);
        try {
            writer = new PrintWriter(filename);
            writer.write("# Ansible Inventory File Auto-generated at " + date.toString() + " \n\n");
        } catch (IOException ioe) {
            logger.error("Failed to create ansible dump file:" + filename, ioe);
            return;
        }
        Map<String, List<Map<String, Object>>> mmap = new HashMap<>();
        try {
            for (String os : ansibleMap.keySet()) {
                logger.debug("Dumping Ansible inventory for os={}", os);
                addAnsibleOsMap(mmap, os, ansibleMap.get(os));
            }
            String output = YamlUtils.dumpInventory(mmap);
            writer.write(output);
            writer.write("\n\n");
        } catch (Throwable t) {
            logger.debug("Error writing ansible:", t);
            return;
        } finally {
            writer.close();
        }
    }

    // Create a List of HashMap for each OS.
    // Call YamlUtils.save to write to file.
    private void addAnsibleOsMap(Map<String, List<Map<String, Object>>> mmap, String os, Map<Long, Set<String>> osMap) {

        for (Long id : osMap.keySet()) {
            //TODO: to fix this with new config server
            Credential credential = null;
            String login = credential.getLoginId();
            //String password = credential.getPassword();
            String password = credential.getLabel();
            String pemfile = credential.getPemFile();
            Set<String> ips = osMap.get(id);
            YamlUtils.addInventory(mmap, os, login, password, pemfile, ips);
        }
    }

    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(30);
    }

    @PreDestroy
    public void shutDown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                executorService.awaitTermination(60, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }

}
*/
