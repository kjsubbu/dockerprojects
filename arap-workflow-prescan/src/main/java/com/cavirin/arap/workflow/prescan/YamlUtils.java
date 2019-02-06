package com.cavirin.arap.workflow.prescan;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.cavirin.arap.db.entities.configuration.Credential;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class YamlUtils {
	private static final Logger logger = LoggerFactory.getLogger(YamlUtils.class);
	// Add one OS Inventory
	public static void addInventory(Map<String, List<Map<String, Object>>> mmap, String os, String login, String pass, String pem, Set<String> ips) {
		String iplist = StringUtils.join(ips, " ");
		logger.debug("Adding inventory: os=" + os + ", login=" + login + ", pass=" + pass + ", pem=" + pem + ", ips=" + iplist);
		Map<String, Object> data = new HashMap<>();
		data.put("login", login);
		data.put("password", pass);
		data.put("pemfile", pem);
		List<String> list = new ArrayList<>();
		list.addAll(ips);
		data.put("IPS", list);
		List<Map<String, Object>> listmap = mmap.get(os);
		if (listmap == null) {
			listmap = new ArrayList<>();
		}
		listmap.add(data);
		mmap.put(os, listmap);
	}

	// Dump out the inventory
	public static String dumpInventory(Map<String, List<Map<String, Object>>> data) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);
		String output = yaml.dump(data);
		return output;
	}


	public static Map<String, String> saveMap1(String os, String login, String pass, String pem, Set<String> ips) {
		Map<String, String> data = new HashMap<>();
		data.put("OS", "os");
		data.put("login", "login");
		data.put("password", "password");
		data.put("pemfile", "pemfile");
		data.put("IPS", StringUtils.join(ips, ","));
		return data;
	}

	public static String save(String os, String login, String pass, String pem, Set<String> ips) {
		Map<String, String> data = new HashMap<>();
		data.put("OS", "os");
		data.put("login", "login");
		data.put("password", "password");
		data.put("pemfile", "pemfile");
		data.put("IPS", StringUtils.join(ips, ","));
		return save(data);
	}

	public static String saveList(List<Map<String, String>> data) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);
		String output = yaml.dump(data);
		return output;
	}

	public static String saveList1(Map<String, Map<String, String>> data) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);
		String output = yaml.dump(data);
		return output;
	}

	public static String save(Map<String, String> data) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);
		String output = yaml.dump(data);
		return output;
	}


	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Set<String> ips1 = new HashSet<>();
		ips1.add("10.10.10.10");
		ips1.add("10.10.10.11");

		Set<String> ips = new HashSet<>();
		ips.add("10.101.10.161");
		ips.add("10.101.10.162");
		ips.add("10.101.10.163");
		ips.add("10.101.10.164");
		ips.add("10.101.10.165");
		ips.add("10.101.10.166");
		ips.add("10.101.10.167");

		Map<String, List<Map<String, Object>>> mmap = new HashMap<>();
		addInventory(mmap, "ubuntu", "ubuntu", "password", "pemfile", ips);
		addInventory(mmap, "centos", "centos", "password", "pemfile", ips);
		addInventory(mmap, "redhat", "ec2-user", "password", "pemfile", ips);
		addInventory(mmap, "ubuntu", "ubuntu", "pass1", "pem", ips1);
		String output = dumpInventory(mmap);
		System.out.println(output);
	}
}