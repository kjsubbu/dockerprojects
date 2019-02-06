#!/bin/bash

display_usage() {
  echo ""
  echo "Usage:  $0  jvmId  numberOfJvms"
  echo ""
  echo "To start 4 PreScan JVMs, run as follows"
  echo "$0  0  4"
  echo "$0  1  4"
  echo "$0  2  4"
  echo "$0  3  4"
  echo ""
}

if [  $# -ne 2 ] 
then 
  display_usage
  exit 1
fi 

#Xms=`awk '/Xms/{print $NF}' /var/lib/cavirin/conf/pulsar-global.properties`
#Xmx=`awk '/Xmx/{print $NF}' /var/lib/cavirin/conf/pulsar-global.properties`
#echo "value of Xms is $Xms"

PrescanHeapSize=1024m

java -Xms$PrescanHeapSize -Xmx$PrescanHeapSize -Djvm=$1 -DjvmCount=$2 -cp ./logback-core-1.1.3.jar:./logback-classic-1.1.3.jar:.:arap-discoveryserver-1.0.0.jar:winrm4j-0.5.0.jar:winrm4j-client-0.5.0.jar:cxf-core-3.1.10.jar:cxf-rt-bindings-soap-3.1.10.jar:cxf-rt-bindings-xml-3.1.10.jar:cxf-rt-databinding-jaxb-3.1.10.jar:cxf-rt-frontend-jaxws-3.1.10.jar:cxf-rt-frontend-simple-3.1.10.jar:cxf-rt-transports-http-3.1.10.jar:cxf-rt-transports-http-hc-3.1.10.jar:cxf-rt-ws-addr-3.1.10.jar:cxf-rt-wsdl-3.1.10.jar:cxf-rt-ws-policy-3.1.10.jar:asm-5.0.4.jar:neethi-3.0.3.jar:woodstox-core-asl-4.4.1.jar:wsdl4j-1.6.3.jar:xml-resolver-1.2.jar:xmlschema-core-2.2.1.jar:xmlunit-core-2.3.0.jar:xmlunit-matchers-2.3.0.jar:stax2-api-3.1.4.jar:httpasyncclient-4.1.2.jar:jaxb-core-2.2.11.jar:jaxb-impl-2.2.11.jar:jcl-over-slf4j-1.7.22.jar:jcommander-1.48.jar:guava-18.0.jar:httpcore-nio-4.4.4.jar:bsh-2.0b4.jar:hamcrest-core-1.3.jar:cxf-api-2.7.14.jar:antlr-2.7.7.jar:jackson-annotations-2.7.4.jar:jackson-core-2.7.4.jar:httpcore-4.4.4.jar:httpclient-4.5.2.jar:log4j-1.2.17.jar:jedis-2.8.0.jar:commons-pool2-2.3.jar:c3p0-0.9.2.1.jar:classmate-1.3.3.jar:jackson-databind-2.7.4.jar:javassist-3.21.0-GA.jar:hibernate-commons-annotations-5.0.1.Final.jar:dom4j-1.6.1.redhat-7.jar:jta-1.1.jar:jboss-logging-3.3.0.Final-redhat-1.jar:hibernate-entitymanager-5.1.0.Final.jar:hibernate-jpa-2.1-api-1.0.0.Final.jar:hibernate-core-5.1.0.Final.jar:postgresql-9.4-1200-jdbc41.jar:hibernate-jpa-2.0-api.jar:persistence-api-1.0.jar:spring-orm-4.2.6.RELEASE.jar:spring-jdbc-4.2.6.RELEASE.jar:spring-data-redis-1.7.2.RELEASE.jar:arap-workflow-utility-1.0.0.jar:spring-expression-4.2.6.RELEASE.jar:aopalliance-1.0.jar:spring-tx-4.2.6.RELEASE.jar:.:./spring-aop-4.2.6.RELEASE.jar:./commons-logging-1.2.jar:./arap-workflow-prescan-1.0.0.jar:./arap-jovalpw-1.0.0.jar:./arap-jovalscan-1.0.0.jar:./arap-db-1.0.0.jar:./json-simple-1.1.1.jar:./spring-context-4.2.4.RELEASE.jar:./spring-beans-4.2.4.RELEASE.jar:./spring-core-4.2.4.RELEASE.jar:./slf4j-api-1.7.21.jar:./cal10n-api-0.8.1.jar:./slf4j-ext-1.7.21.jar:./javatar-2.5.jar:./java-json.jar:vault-java-driver-2.0.0.jar:rxjava-1.2.10.jar:jsch-0.1.54.jar:icmp4j-1.0.jar:./adal4j-1.0.0.jar:./azure-mgmt-compute-0.9.1.jar:./azure-mgmt-network-0.9.1.jar:./azure-mgmt-resources-0.9.1.jar:./oauth2-oidc-sdk-4.5.jar:./nimbus-jose-jwt-3.1.2.jar:./json-smart-1.1.1.jar:./commons-lang3-3.0.jar:./mail-1.4.7.jar:./commons-codec-1.9.jar:./azure-core-0.9.1.jar:./javax.inject-1.jar:./jersey-client-1.13.jar:./jersey-core-1.13.jar:./jackson-core-asl-1.9.13.jar:./jackson-mapper-asl-1.9.13.jar:./commons-lang-2.6.jar:./commons-io-2.4.jar:appengine-api-1.0-sdk-1.8.3.jar:google-api-client-appengine-1.15.0-rc.jar:google-api-services-cloudresourcemanager-v1-rev7-1.22.0.jar:google-api-services-compute-v1-rev127-1.22.0.jar:google-api-services-container-v1-rev8-1.22.0.jar:google-api-services-sqladmin-v1beta4-rev30-1.22.0.jar:google-api-services-storage-v1-rev86-1.22.0.jar:google-http-client-1.16.0-rc.jar:google-oauth-client-jetty-1.21.0.jar:snakeyaml-1.17.jar:Cavirin2Joval-DoD-ARF-schema-0.0.449-0.0.449.jar:Cavirin2Joval-jOVAL-0.0.449-0.0.449.jar:Cavirin2Joval-jOVAL-Plugin-0.0.449-0.0.449.jar:Cavirin2Joval-jSAF-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-jdbm-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-jPE-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-jSAF-Provider-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-JSch-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-jWSMV-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-ntlm-core-1.0-r21-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-tftp-0.0.449-0.0.449.jar:Cavirin2Joval-REMOTE-ws-man-0.0.449-0.0.449.jar:Cavirin2Joval-scap-schema-1.2.1-0.0.449-0.0.449.jar:Cavirin2Joval-scap-schema-extensions-0.0.449-0.0.449.jar com.cavirin.arap.workflow.prescan.PreScanWorkFlowExecution

