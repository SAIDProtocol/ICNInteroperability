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
- [ ] NDN Adapter
  - [ ] Consumer side (left) static
  - [x] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [x] NDN Content provider
  - [x] static (use ccnr)
  - [x] dynamic (similar to ORS)
- [x] NDN Content consumer
  - [x] static (similar to file retriever)
  - [x] dynamic (similar to ORS client)
- [ ] MF Adapter
  - [ ] Consumer side (left) static
  - [ ] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] MF Content provider
  - [ ] static (similar to file server)
  - [ ] dynamic (similar to ORS)
- [ ] MF Content consumer
  - [ ] static (similar to file retriever)
  - [ ] dynamic (similar to ORS client)
- [ ] Optimization: chunked transmission  

