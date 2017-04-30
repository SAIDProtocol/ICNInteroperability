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
- [ ] IP Adapter
  - [x] Consumer side (left) static
  - [ ] Provider side (right) static  \(dummy now\)
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] NDN Adapter
  - [ ] Consumer side (left) static
  - [ ] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] MF Adapter
  - [ ] Consumer side (left) static
  - [ ] Provider side (right) static
  - [ ] Consumer side (left) dynamic
  - [ ] Provider side (right) dynamic
- [ ] Optimization: chunked transmission  

