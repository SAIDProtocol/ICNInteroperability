# ICNInteroperability
## Before compile
### MobilityFirst (byte_array_guid branch) and ccnx 0.8.0
### In ccnx folder
```
mvn clean install -DskipTests=true -Dmaven.javadoc.skip=true
```
### In MF/mfclient/netapi/java 
```
mvn clean install
```
## Available commands
```
export EXECUTE=ICNInteroperability-1.0-SNAPSHOT-jar-with-dependencies.jar
```
### Gateway
```
java -classpath $EXECUTE edu.rutgers.winlab.icninteroperability.RunGateway ipip|ipndn|ipmf|mfndn
```
### Providers
#### IP provider
```
java -classpath $EXECUTE edu.rutgers.winlab.provider.ProviderIP %port% %folder% %wait_on_static%
```
#### NDN dynamic provider
```
java -classpath $EXECUTE edu.rutgers.winlab.provider.ProviderNDNDynamic %prefix%
```
#### CCNFileProxy (copied from CCNx, suggest to use ccnr instead)
```
java -classpath $EXECUTE edu.rutgers.winlab.provider.CCNFileProxy %filePrefix% %ccnURI%
```
#### MF provider
```
java -classpath $EXECUTE edu.rutgers.winlab.provider.ProviderMF %mapping% %wait_on_static% %dynamicGUID%
```
A mapping file is needed. E.g.,
```
8193 key
8194 test/testFile.txt
```
Each line represents a guid (and the corresponding file, separated by space)
### Consumer
```
java -classpath $EXECUTE edu.rutgers.winlab.consumer.RunConsumer %output% static|dynamic %clientDomain% %clientName% %dstDomain% %name% [%version%|%input%]
```
You can specify sleep time in the input (e.g., sleep=xxx). The server should wait for xxx ms before responding.

However, do not set sleep > 4000, since this is the timeout time in CCN. You will not get response if you specify a large value.

## Task list
- [x] Skeleton of adapters, demultiplexing items, canonical formats
- [x] Gateway with adapter interface
- [x] IP Adapter
  - [x] Consumer side (left) static
  - [x] Provider side (right) static
  - [x] Consumer side (left) dynamic
  - [x] Provider side (right) dynamic
- [x] IP Content provider
  - [x] static (similar to file server)
  - [x] dynamic (similar to ORS)
- [x] IP Content consumer
  - [x] static (similar to file retriever)
  - [x] dynamic (similar to ORS client)
- [x] NDN Adapter
  - [x] Consumer side (left) static
  - [x] Provider side (right) static
  - [x] Consumer side (left) dynamic
  - [x] Provider side (right) dynamic
- [x] NDN Content provider
  - [x] static (use ccnr)
  - [x] dynamic (similar to ORS)
- [x] NDN Content consumer
  - [x] static (similar to file retriever)
  - [x] dynamic (similar to ORS client)
- [x] MF Adapter
  - [x] Consumer side (left) static
  - [x] Provider side (right) static
  - [x] Consumer side (left) dynamic
  - [x] Provider side (right) dynamic
- [x] MF Content provider
  - [x] static (similar to file server)
  - [x] dynamic (similar to ORS)
- [x] MF Content consumer
  - [x] static (similar to file retriever)
  - [x] dynamic (similar to ORS client)
  - [x] MF content aggregator: (Use MF-IP GW to optimize)
- [ ] Optimization: chunked transmission
- [ ] Optimization: put a global pending request table in the gw, instead of each adapter
- [x] Report system
  - [x] Basic report framework at DomainAdapter
  - [x] Report implementation at DomainAdapterIP
  - [x] Report implementation at DomainAdapterNDN
  - [x] Report implementation at DomainAdapterMF
  - [x] Report action implementation

